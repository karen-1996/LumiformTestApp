package com.lumiformtest.form.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumiformtest.form.data.remote.FormRemoteDataSource
import com.lumiformtest.form.data.repository.OfflineFormRepository
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.FormImage
import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.FormPage
import com.lumiformtest.form.domain.model.ImageObservation
import com.lumiformtest.form.domain.model.ResponseOption
import com.lumiformtest.form.domain.model.ResponseSet
import java.time.Clock
import java.time.Instant
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageObservationTest {
    private lateinit var database: FormDatabase
    private lateinit var local: RoomFormLocalDataSource
    private val imageQueries = Channel<Unit>(Channel.UNLIMITED)

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, FormDatabase::class.java)
            .setQueryCallback({ sql, _ ->
                if (sql.contains("AND type = 'IMAGE'")) imageQueries.trySend(Unit)
            }, Executor { it.run() }).build()
        local = RoomFormLocalDataSource(database.formDao(), Dispatchers.IO)
    }

    @After
    fun tearDown() { database.close(); imageQueries.close() }

    @Test
    fun answersDoNotQueryTheImageAndDefinitionUpdatesRemainObservable() = runBlocking {
        val original = FormItem.Image(9, "https://example.com/a", "Saved")
        local.replaceForm(document(original), Instant.EPOCH)
        val emitted = Channel<FormImage?>(Channel.UNLIMITED)
        val observer = launch(Dispatchers.Default) { local.observeImage(9).collect { emitted.send(it) } }
        try {
            assertEquals(FormImage(9, "Saved", "https://example.com/a"), withTimeout(5_000) { emitted.receive() })
            withTimeout(5_000) { imageQueries.receive() }
            local.toggleChoiceOption(ChoiceOption(10, 100, 1))
            assertEquals(setOf(1L), local.observeForm().first()?.selections?.get(10)?.optionIds)
            assertNull("Answer writes must not rerun the image query", withTimeoutOrNull(300) { imageQueries.receive() })
            local.replaceForm(document(original.copy(title = "Updated", imageUrl = "https://example.com/b")), Instant.ofEpochSecond(1))
            assertEquals(FormImage(9, "Updated", "https://example.com/b"), withTimeout(5_000) { emitted.receive() })
            local.replaceForm(document(FormItem.Text(9, "Now text")), Instant.ofEpochSecond(2))
            assertNull(withTimeout(5_000) { emitted.receive() })
        } finally { observer.cancelAndJoin(); emitted.close() }
    }

    @Test
    fun unknownAndNonImageIdsRemainMissing() = runBlocking {
        local.replaceForm(document(FormItem.Image(9, "https://example.com/a", "Image")), Instant.EPOCH)
        assertNull(local.observeImage(10).first())
        assertNull(local.observeImage(Long.MAX_VALUE).first())
    }

    @Test
    fun corruptImageMetadataBecomesARepositoryReadFailure() = runBlocking {
        local.replaceForm(document(FormItem.Image(9, "https://example.com/a", "Image")), Instant.EPOCH)
        database.openHelper.writableDatabase.execSQL("UPDATE form_node SET title = NULL WHERE id = 9")
        val repository = OfflineFormRepository(FormRemoteDataSource { error("A viewer must not fetch") }, local, Clock.systemUTC())
        assertEquals(ImageObservation.ReadFailure, repository.observeImage(9).first())
    }

    private fun document(item: FormItem) = FormDocument(listOf(FormPage(1, "Page", listOf(
        FormItem.Section(2, "Nested", listOf(item)),
        FormItem.Choice(10, "Choice", ResponseSet(100, true, listOf(ResponseOption(1, "One", null))))
    ))))
}
