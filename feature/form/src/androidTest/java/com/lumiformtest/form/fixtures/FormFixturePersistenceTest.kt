package com.lumiformtest.form.fixtures

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumiformtest.form.data.local.FormDatabase
import com.lumiformtest.form.data.local.RoomFormLocalDataSource
import com.lumiformtest.form.data.remote.FormJsonParser
import com.lumiformtest.form.data.remote.FormRemoteDataSource
import com.lumiformtest.form.data.repository.OfflineFormRepository
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelectionResult
import com.lumiformtest.form.domain.model.FormRefreshFailure
import com.lumiformtest.form.domain.model.FormSnapshot
import com.lumiformtest.form.domain.model.RefreshFormResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormFixturePersistenceTest {
    private val databaseName = "fixture-scenarios-test.db"
    private val parser by lazy { FormJsonParser() }

    private val time by lazy { Instant.parse("2026-10-30T12:00:00Z") }
    private lateinit var context: Context
    private lateinit var database: FormDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(databaseName)
        database = open()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun acceptedFixturesSurviveRealDatabaseCloseAndReopen() = runBlocking {
        for ((index, fixture) in FormFixture.cases(::read).filter { it.accepted }.withIndex()) {
            val document = parser.parse(read(fixture.path))
            local().replaceForm(document, time)
            database.close()
            database = open()
            assertEquals(fixture.file, FormSnapshot(document, time, definitionVersion = index + 1L), snapshot())
        }
    }

    @Test
    fun rejectedRefreshesKeepSavedContentAnswersAndTimestamp() = runBlocking {
        local().replaceForm(parse("refresh/01-before.json"), time)
        tap(2, 700, 1)
        tap(2, 700, 3)
        tap(3, 701, 2)
        val saved = snapshot()
        for (fixture in FormFixture.cases(::read).filterNot { it.accepted }) {
            val repository = OfflineFormRepository(FormRemoteDataSource { parser.parse(read(fixture.path)) },
                local(), Clock.fixed(time.plusSeconds(60), ZoneOffset.UTC), this)
            assertEquals(fixture.file, RefreshFormResult.Failure(FormRefreshFailure.INVALID_CONTENT), repository.refreshForm())
            assertEquals("Rejected refresh must preserve the complete snapshot: ${fixture.file}", saved, snapshot())
        }
    }

    @Test
    fun refreshSequenceRetainsOnlyAnswersValidForTheNewDefinitions() = runBlocking {
        local().replaceForm(parse("refresh/01-before.json"), time)
        tap(2, 700, 1)
        tap(2, 700, 3)
        tap(3, 701, 2)
        local().replaceForm(parse("refresh/02-reordered.json"), time.plusSeconds(60))
        assertEquals(setOf(3L), snapshot().selections[2]?.optionIds)
        assertEquals(setOf(2L), snapshot().selections[3]?.optionIds)
        local().replaceForm(parse("refresh/03-replaced-set.json"), time.plusSeconds(120))
        assertTrue(snapshot().selections.isEmpty())
        tap(2, 702, 2)
        assertEquals(702L, snapshot().selections[2]?.responseSetId)
        assertEquals(time.plusSeconds(120), snapshot().lastUpdated)
    }

    @Test
    fun sharedDefinitionsDoNotShareAnswersAndLongIdsStayUsable() = runBlocking {
        local().replaceForm(parse("valid/shared-response-sets.json"), time)
        tap(2, 400, 1)
        tap(3, 400, 2)
        tap(4, 401, 1)
        assertEquals(setOf(1L), snapshot().selections[2]?.optionIds)
        assertEquals(setOf(2L), snapshot().selections[3]?.optionIds)
        assertEquals(401L, snapshot().selections[4]?.responseSetId)
        local().replaceForm(parse("valid/large-and-unordered-ids.json"), time)
        tap(9_000_000_000, -1, 2)
        assertEquals(setOf(2L), snapshot().selections[9_000_000_000]?.optionIds)
    }

    private fun read(path: String) = InstrumentationRegistry.getInstrumentation().context.assets.open(path)
        .bufferedReader().use { it.readText() }
    private fun parse(file: String) = parser.parse(read("${FormFixture.ROOT}/$file"))
    private fun open() = Room.databaseBuilder(context, FormDatabase::class.java, databaseName).build()
    private fun local() = RoomFormLocalDataSource(database.formDao(), Dispatchers.IO)
    private suspend fun snapshot() = requireNotNull(local().observeForm().first())
    private suspend fun tap(question: Long, set: Long, option: Long) {
        assertEquals(ChoiceSelectionResult.Success, local().toggleChoiceOption(ChoiceOption(question, set, option)))
    }
}
