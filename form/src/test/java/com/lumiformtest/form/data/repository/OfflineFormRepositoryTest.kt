package com.lumiformtest.form.data.repository

import com.lumiformtest.form.data.local.FormLocalDataSource
import com.lumiformtest.form.data.local.FormStorageException
import com.lumiformtest.form.data.remote.FormRemoteDataSource
import com.lumiformtest.form.data.remote.InvalidFormContentException
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFormRepositoryTest {
    private val time = Instant.parse("2026-10-29T12:00:00Z")
    private val clock = Clock.fixed(time, ZoneOffset.UTC)
    private val saved = FormSnapshot(document("Saved"), time.minusSeconds(60))

    @Test
    fun `observation reads cache immediately without requesting the network`() = runTest {
        var fetches = 0
        val local = FakeLocal(saved)
        val repository = OfflineFormRepository(FormRemoteDataSource { fetches++; document("New") }, local, clock)

        assertEquals(saved, repository.observeForm().first())
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
        assertEquals(saved, repository.observeForm().first())
        finishWrite.complete(Unit)

        assertEquals(RefreshFormResult.Success, refresh.await())
        assertEquals(FormSnapshot(updated, time), repository.observeForm().first())
    }

    @Test
    fun `fetch and content failures preserve saved content and the successful refresh time`() = runTest {
        val failures = listOf(
            IOException("Offline") to FormRefreshFailure.FETCH,
            HttpException(Response.error<Any>(503, "Unavailable".toResponseBody())) to FormRefreshFailure.FETCH,
            InvalidFormContentException("Invalid") to FormRefreshFailure.INVALID_CONTENT
        )
        for ((error, reason) in failures) {
            val local = FakeLocal(saved)
            val repository = OfflineFormRepository(FormRemoteDataSource { throw error }, local, clock)

            assertEquals(RefreshFormResult.Failure(reason), repository.refreshForm())
            assertEquals(saved, repository.observeForm().first())
            assertEquals(0, local.writes)
        }
    }

    @Test
    fun `failed first fetch leaves the cache absent`() = runTest {
        val local = FakeLocal(null)
        val repository = OfflineFormRepository(FormRemoteDataSource { throw IOException("Offline") }, local, clock)

        assertEquals(RefreshFormResult.Failure(FormRefreshFailure.FETCH), repository.refreshForm())
        assertEquals(null, repository.observeForm().first())
    }

    @Test
    fun `storage failure keeps the previous snapshot`() = runTest {
        val local = FakeLocal(saved).apply { beforeWrite = { throw FormStorageException("Disk full") } }
        val repository = OfflineFormRepository(FormRemoteDataSource { document("Updated") }, local, clock)

        assertEquals(RefreshFormResult.Failure(FormRefreshFailure.STORAGE), repository.refreshForm())
        assertEquals(saved, repository.observeForm().first())
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

    private fun document(title: String) = FormDocument(listOf(FormPage(1, title, emptyList())))

    private class FakeLocal(initial: FormSnapshot?) : FormLocalDataSource {
        val state = MutableStateFlow(initial)
        var writes = 0
        var beforeWrite: suspend () -> Unit = {}

        override fun observeForm() = state

        override suspend fun replaceForm(document: FormDocument, lastUpdated: Instant) {
            beforeWrite()
            state.value = FormSnapshot(document, lastUpdated)
            writes++
        }
    }
}
