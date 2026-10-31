package com.lumiformtest.form.data.local

import com.lumiformtest.form.data.mapper.toStorage
import android.content.Context
import android.database.SQLException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumiformtest.form.data.remote.FormRemoteDataSource
import com.lumiformtest.form.data.repository.OfflineFormRepository
import com.lumiformtest.form.domain.model.FormObservation
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.FormPage
import com.lumiformtest.form.domain.model.FormRefreshFailure
import com.lumiformtest.form.domain.model.FormSnapshot
import com.lumiformtest.form.domain.model.RefreshFormResult
import com.lumiformtest.form.domain.model.ResponseOption
import com.lumiformtest.form.domain.model.ResponseSet
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormPersistenceTest {
    private val name = "form-persistence-test.db"

    private val time by lazy { Instant.parse("2026-10-29T12:00:00Z") }
    private lateinit var context: Context
    private lateinit var database: FormDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun noCacheAndSuccessfullyFetchedEmptyContentAreDifferent() = runBlocking {
        assertNull(local().observeForm().first())

        local().replaceForm(FormDocument(emptyList()), time)

        assertEquals(FormSnapshot(FormDocument(emptyList()), time, definitionVersion = 1), local().observeForm().first())
    }

    @Test
    fun hierarchyOptionsAndTimestampSurviveClosingAndReopeningTheDatabase() = runBlocking {
        val document = document()
        local().replaceForm(document, time)
        database.close()
        database = openDatabase()

        assertEquals(FormSnapshot(document, time, definitionVersion = 1), local().observeForm().first())
    }

    @Test
    fun failedOfflineRefreshAfterReopenKeepsSavedContent() = runBlocking {
        local().replaceForm(document(), time)
        database.close()
        database = openDatabase()
        val repository = OfflineFormRepository(
            FormRemoteDataSource { throw IOException("Offline") },
            local(), Clock.fixed(time.plusSeconds(60), ZoneOffset.UTC)
        )

        assertEquals(RefreshFormResult.Failure(FormRefreshFailure.FETCH), repository.refreshForm())
        assertEquals(FormObservation.Snapshot(FormSnapshot(document(), time, definitionVersion = 1)), repository.observeForm().first())
    }

    @Test
    fun aForeignKeyFailureRollsBackContentAndMetadataTogether() = runBlocking {
        local().replaceForm(document(), time)
        val replacement = document("Replacement").toStorage(time.plusSeconds(60))
        val invalid = replacement.copy(nodes = replacement.nodes.map {
            if (it.id == 9L) it.copy(parentId = 999) else it
        })
        var failure: SQLException? = null
        try {
            database.formDao().replaceForm(invalid)
        } catch (error: SQLException) {
            failure = error
        }

        assertNotNull("The parent foreign key must be enforced", failure)
        assertEquals(FormSnapshot(document(), time, definitionVersion = 1), local().observeForm().first())
    }

    @Test
    fun storageConstraintFailureIsTypedAndKeepsThePreviousCache() = runBlocking {
        val valid = document()
        local().replaceForm(valid, time)
        val duplicateIds = valid.copy(pages = valid.pages + valid.pages.first())
        var failure: FormStorageException? = null
        try {
            local().replaceForm(duplicateIds, time.plusSeconds(60))
        } catch (error: FormStorageException) {
            failure = error
        }

        assertNotNull("SQLite failures should be translated by the local data source", failure)
        assertNotNull(failure?.cause as? SQLException)
        assertEquals(FormSnapshot(valid, time, definitionVersion = 1), local().observeForm().first())
    }

    @Test
    fun observersReceiveCompleteSnapshotsDuringReplacement() = runBlocking {
        local().replaceForm(document(), time)
        val subscribed = CompletableDeferred<Unit>()
        val values = async(Dispatchers.IO) {
            withTimeout(10_000) {
                local().observeForm().onEach { subscribed.complete(Unit) }.take(2).toList()
            }
        }
        subscribed.await()
        val replacement = document("Replacement")
        local().replaceForm(replacement, time.plusSeconds(60))

        assertEquals(
            listOf(FormSnapshot(document(), time, definitionVersion = 1), FormSnapshot(replacement, time.plusSeconds(60), definitionVersion = 2)),
            values.await()
        )
    }

    private fun openDatabase(): FormDatabase =
        Room.databaseBuilder(context, FormDatabase::class.java, name).build()

    private fun local() = RoomFormLocalDataSource(database.formDao(), Dispatchers.IO)

    private fun document(title: String = "Saved") = FormDocument(
        listOf(
            FormPage(9_000_000_000, title, listOf(
                FormItem.Text(9, "Direct question"),
                FormItem.Section(2, "Section", listOf(
                    FormItem.Image(8, "https://example.com/image.png", "Image"),
                    FormItem.Choice(3, "Choose", ResponseSet(10, false,
                        listOf(ResponseOption(2, "B", null), ResponseOption(1, "A", 0)))),
                    FormItem.Section(6, "Nested", listOf(
                        FormItem.Choice(5, "Multiple", ResponseSet(11, true,
                            listOf(ResponseOption(1, "Other set", 1))))
                    ))
                ))
            )),
            FormPage(20, "Empty page", emptyList())
        )
    )
}
