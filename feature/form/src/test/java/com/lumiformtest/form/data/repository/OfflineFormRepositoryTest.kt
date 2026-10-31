package com.lumiformtest.form.data.repository

import com.lumiformtest.form.data.local.FormLocalDataSource
import com.lumiformtest.form.data.local.FormStorageException
import com.lumiformtest.form.data.remote.FormRemoteDataSource
import com.lumiformtest.form.data.remote.InvalidFormContentException
import com.lumiformtest.form.data.remote.NoInternetConnectionException
import com.lumiformtest.form.domain.model.FormImage
import com.lumiformtest.form.domain.model.ImageObservation
import com.lumiformtest.form.domain.model.FormObservation
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelectionResult
import com.lumiformtest.form.domain.model.ChoiceSelectionFailure
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.FormPage
import com.lumiformtest.form.domain.model.FormRefreshFailure
import com.lumiformtest.form.domain.model.FormSnapshot
import com.lumiformtest.form.domain.model.RefreshFormResult
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFormRepositoryTest {
    private val time by lazy { Instant.parse("2026-10-29T12:00:00Z") }
    private val clock by lazy { Clock.fixed(time, ZoneOffset.UTC) }
    private val saved by lazy { FormSnapshot(document("Saved"), time.minusSeconds(60)) }

    @Test
    fun `observation reads cache immediately without requesting the network`() = runTest {
        var fetches = 0
        val local = FakeLocal(saved)
        val repository = OfflineFormRepository(FormRemoteDataSource { fetches++; document("New") }, local, clock)

        assertEquals(FormObservation.Snapshot(saved), repository.observeForm().first())
        assertEquals(0, fetches)
    }

    @Test
    fun `successful refresh publishes only through storage and updates its timestamp`() = runTest {
        val writeStarted = CompletableDeferred<Unit>()
        val finishWrite = CompletableDeferred<Unit>()
        val local = FakeLocal(saved).apply {
            beforeWrite = { writeStarted.complete(Unit); finishWrite.await() }
        }
        val updated = document("Updated")
        val repository = OfflineFormRepository(FormRemoteDataSource { updated }, local, clock)

        val refresh = async { repository.refreshForm() }
        writeStarted.await()
        assertEquals(FormObservation.Snapshot(saved), repository.observeForm().first())
        finishWrite.complete(Unit)

        assertEquals(RefreshFormResult.Success, refresh.await())
        assertEquals(FormObservation.Snapshot(FormSnapshot(updated, time)), repository.observeForm().first())
    }

    @Test
    fun `fetch and content failures preserve saved content and the successful refresh time`() = runTest {
        val failures = listOf(
            IOException("Offline") to FormRefreshFailure.FETCH,
            NoInternetConnectionException(IOException("Disconnected")) to FormRefreshFailure.OFFLINE,
            HttpException(Response.error<Any>(503, "Unavailable".toResponseBody())) to FormRefreshFailure.FETCH,
            InvalidFormContentException("Invalid") to FormRefreshFailure.INVALID_CONTENT
        )
        for ((error, reason) in failures) {
            val local = FakeLocal(saved)
            val repository = OfflineFormRepository(FormRemoteDataSource { throw error }, local, clock)

            assertEquals(RefreshFormResult.Failure(reason), repository.refreshForm())
            assertEquals(FormObservation.Snapshot(saved), repository.observeForm().first())
            assertEquals(0, local.writes)
        }
    }

    @Test
    fun `failed first fetch leaves the cache absent`() = runTest {
        val local = FakeLocal(null)
        val repository = OfflineFormRepository(FormRemoteDataSource { throw IOException("Offline") }, local, clock)

        assertEquals(RefreshFormResult.Failure(FormRefreshFailure.FETCH), repository.refreshForm())
        assertEquals(FormObservation.Snapshot(null), repository.observeForm().first())
    }

    @Test
    fun `storage failure keeps the previous snapshot`() = runTest {
        val local = FakeLocal(saved).apply { beforeWrite = { throw FormStorageException("Disk full") } }
        val repository = OfflineFormRepository(FormRemoteDataSource { document("Updated") }, local, clock)

        assertEquals(RefreshFormResult.Failure(FormRefreshFailure.STORAGE), repository.refreshForm())
        assertEquals(FormObservation.Snapshot(saved), repository.observeForm().first())
    }

    @Test
    fun `overlapping refreshes are serialized across fetch and write`() = runTest {
        var fetches = 0
        var active = 0
        var maximumActive = 0
        val local = FakeLocal(saved)
        val firstWriteStarted = CompletableDeferred<Unit>()
        val finishFirstWrite = CompletableDeferred<Unit>()
        local.beforeWrite = {
            if (local.writes == 0) {
                firstWriteStarted.complete(Unit)
                finishFirstWrite.await()
            }
        }
        val remote = FormRemoteDataSource {
            val number = ++fetches
            active++
            maximumActive = maxOf(maximumActive, active)
            delay(100)
            active--
            document("Response $number")
        }
        val repository = OfflineFormRepository(remote, local, clock)
        val first = async { repository.refreshForm() }
        val second = async { repository.refreshForm() }
        runCurrent()
        assertEquals(1, fetches)
        firstWriteStarted.await()
        runCurrent()
        assertEquals(1, fetches)
        assertEquals(0, local.writes)
        finishFirstWrite.complete(Unit)
        advanceUntilIdle()

        assertEquals(RefreshFormResult.Success, first.await())
        assertEquals(RefreshFormResult.Success, second.await())
        assertEquals(1, maximumActive)
        assertEquals(2, local.writes)
        assertEquals(document("Response 2"), local.state.value?.document)
    }

    @Test
    fun `cancelling a fetch releases the refresh lock and does not write`() = runTest {
        var fetches = 0
        val local = FakeLocal(saved)
        val repository = OfflineFormRepository(FormRemoteDataSource {
            if (++fetches == 1) awaitCancellation() else document("Retry")
        }, local, clock)
        val first = async { repository.refreshForm() }
        runCurrent()
        first.cancelAndJoin()

        assertEquals(0, local.writes)
        assertEquals(saved, local.state.value)
        assertEquals(RefreshFormResult.Success, repository.refreshForm())
        assertEquals(document("Retry"), local.state.value?.document)
    }

    @Test
    fun `cancellation during storage is not converted to a failure result`() = runTest {
        val local = FakeLocal(saved).apply { beforeWrite = { throw CancellationException("Stopped") } }
        val repository = OfflineFormRepository(FormRemoteDataSource { document("Updated") }, local, clock)
        val refresh = async { repository.refreshForm() }
        refresh.join()

        assertTrue(refresh.isCancelled)
        assertEquals(0, local.writes)
        assertEquals(saved, local.state.value)
    }

    @Test
    fun `saved content read failures become a domain result`() = runTest {
        val local = object : FormLocalDataSource {
            override fun observeImage(imageId: Long) = flowOf<FormImage?>(null)
            override fun observeForm() = flow<FormSnapshot?> { throw FormStorageException("Cannot read") }
            override suspend fun replaceForm(document: FormDocument, lastUpdated: Instant) = Unit
            override suspend fun toggleChoiceOption(option: ChoiceOption) = ChoiceSelectionResult.Success
        }
        val repository = OfflineFormRepository(FormRemoteDataSource { document("New") }, local, clock)

        assertEquals(FormObservation.ReadFailure, repository.observeForm().first())
    }

    @Test
    fun `observation does not hide cancellation or programming errors`() {
        for (error in listOf(CancellationException("Stopped"), IllegalStateException("Bug"))) {
            val local = object : FormLocalDataSource {
                override fun observeImage(imageId: Long) = flowOf<FormImage?>(null)
            override fun observeForm() = flow<FormSnapshot?> { throw error }
                override suspend fun replaceForm(document: FormDocument, lastUpdated: Instant) = Unit
            override suspend fun toggleChoiceOption(option: ChoiceOption) = ChoiceSelectionResult.Success
            }
            val thrown = assertThrows(error.javaClass) {
                runTest {
                    val repository = OfflineFormRepository(FormRemoteDataSource { document("New") }, local, clock)
                    repository.observeForm().first()
                }
            }
            assertEquals(error, thrown)
        }
    }

    @Test
    fun `selection writes stay local and preserve qualified option identity`() = runTest {
        val input = ChoiceOption(10, 100, 2)
        val local = FakeLocal(saved).apply { toggle = { assertEquals(input, it); ChoiceSelectionResult.Success } }
        val repository = OfflineFormRepository(FormRemoteDataSource { error("Selection must not fetch") }, local, clock)
        assertEquals(ChoiceSelectionResult.Success, repository.toggleChoiceOption(input))
        local.toggle = { ChoiceSelectionResult.Failure(ChoiceSelectionFailure.UNAVAILABLE) }
        assertEquals(ChoiceSelectionResult.Failure(ChoiceSelectionFailure.UNAVAILABLE), repository.toggleChoiceOption(input))
    }

    @Test
    fun `selection storage errors are typed while cancellation and programming errors propagate`() = runTest {
        val local = FakeLocal(saved)
        val repository = OfflineFormRepository(FormRemoteDataSource { document("New") }, local, clock)
        local.toggle = { throw FormStorageException("Cannot save") }
        assertEquals(ChoiceSelectionResult.Failure(ChoiceSelectionFailure.STORAGE), repository.toggleChoiceOption(ChoiceOption(10, 100, 1)))
        for (error in listOf(CancellationException("Stopped"), IllegalStateException("Bug"))) {
            local.toggle = { throw error }
            try {
                repository.toggleChoiceOption(ChoiceOption(10, 100, 1))
                throw AssertionError("The original failure must propagate")
            } catch (actual: Exception) {
                assertEquals(error.javaClass, actual.javaClass)
                assertEquals(error.message, actual.message)
            }
        }
        local.toggle = { ChoiceSelectionResult.Success }
        assertEquals(ChoiceSelectionResult.Success, repository.toggleChoiceOption(ChoiceOption(10, 100, 1)))
    }

    @Test
    fun `image observation requests only the selected ID and exposes missing metadata`() = runTest {
        val image = FormImage(9, "Saved image", "https://example.com/a")
        val local = FakeLocal(saved).apply {
            imageRead = { id -> assertEquals(9L, id); flowOf(image, null) }
        }
        val repository = OfflineFormRepository(FormRemoteDataSource { error("Image reads must not fetch") }, local, clock)
        assertEquals(listOf(ImageObservation.Content(image), ImageObservation.Content(null)),
            repository.observeImage(9).toList())
    }

    @Test
    fun `image storage errors become read failures while cancellation and bugs propagate`() = runTest {
        val local = FakeLocal(saved)
        val repository = OfflineFormRepository(FormRemoteDataSource { error("Not used") }, local, clock)
        local.imageRead = { flow { throw FormStorageException("Cannot read image") } }
        assertEquals(ImageObservation.ReadFailure, repository.observeImage(9).first())
        for (error in listOf(CancellationException("Stopped"), IllegalStateException("Bug"))) {
            local.imageRead = { flow { throw error } }
            try {
                repository.observeImage(9).first()
                throw AssertionError("The original failure must propagate")
            } catch (actual: Exception) {
                assertEquals(error, actual)
            }
        }
    }

    private fun document(title: String) = FormDocument(listOf(FormPage(1, title, emptyList())))

    private class FakeLocal(initial: FormSnapshot?) : FormLocalDataSource {
        val state by lazy { MutableStateFlow(initial) }
        var writes = 0
        var beforeWrite: suspend () -> Unit = {}
        var toggle: suspend (ChoiceOption) -> ChoiceSelectionResult = { ChoiceSelectionResult.Success }
        override suspend fun toggleChoiceOption(option: ChoiceOption) = toggle(option)

        var imageRead: (Long) -> kotlinx.coroutines.flow.Flow<FormImage?> = { flowOf(null) }
        override fun observeImage(imageId: Long) = imageRead(imageId)
        override fun observeForm() = state

        override suspend fun replaceForm(document: FormDocument, lastUpdated: Instant) {
            beforeWrite()
            state.value = FormSnapshot(document, lastUpdated)
            writes++
        }
    }
}
