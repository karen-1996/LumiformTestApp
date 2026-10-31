package com.lumiformtest.form.data.local

import com.lumiformtest.form.data.mapper.toStorage
import android.content.Context
import android.database.SQLException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumiformtest.form.data.remote.FormRemoteDataSource
import com.lumiformtest.form.data.repository.OfflineFormRepository
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelectionFailure
import com.lumiformtest.form.domain.model.ChoiceSelectionResult
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.FormPage
import com.lumiformtest.form.domain.model.RefreshFormResult
import com.lumiformtest.form.domain.model.ResponseOption
import com.lumiformtest.form.domain.model.ResponseSet
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChoicePersistenceTest {
    private val name = "choice-persistence-test.db"
    private val time by lazy { Instant.parse("2026-10-29T12:00:00Z") }

    private lateinit var context: Context
    private lateinit var database: FormDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(name)
        database = open()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test
    fun singleChoiceReplacesAndRepeatedSelectedTapsKeepTheAnswer() = runBlocking {
        local().replaceForm(document(multiple = false), time)
        tap(10, 1)
        tap(10, 2)
        tap(10, 2)
        assertEquals(setOf(2L), snapshot().selections[10]?.optionIds)
        assertEquals(null, snapshot().selections[11])
        assertEquals(time, snapshot().lastUpdated)
    }

    @Test
    fun multipleChoicesToggleIndependentlyEvenWhenQuestionsShareDefinitions() = runBlocking {
        local().replaceForm(document(), time)
        tap(10, 1)
        tap(10, 2)
        tap(11, 1)
        tap(12, 1, setId = 200)
        tap(10, 1)
        val saved = snapshot()
        assertEquals(setOf(2L), saved.selections[10]?.optionIds)
        assertEquals(setOf(1L), saved.selections[11]?.optionIds)
        assertEquals(setOf(1L), saved.selections[12]?.optionIds)
        assertEquals(200L, saved.selections[12]?.responseSetId)
    }

    @Test
    fun staleAndInvalidCommandsDoNotChangeSavedAnswers() = runBlocking {
        local().replaceForm(document(), time)
        tap(10, 1)
        val saved = snapshot()
        for (option in listOf(ChoiceOption(999, 100, 1), ChoiceOption(10, 200, 1), ChoiceOption(10, 100, 99), ChoiceOption(13, 100, 1))) {
            assertEquals(ChoiceSelectionResult.Failure(ChoiceSelectionFailure.UNAVAILABLE), local().toggleChoiceOption(option))
        }
        assertEquals(saved, snapshot())
    }

    @Test
    fun foreignKeysRejectOptionsFromAnotherSetOrMissingOptions() = runBlocking {
        local().replaceForm(document(), time)
        for ((setId, optionId) in listOf(200L to 1L, 100L to 99L)) {
            assertThrows(SQLException::class.java) {
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO selected_response(questionId,responseSetId,optionId,snapshotId) VALUES(10,?,?,0)",
                    arrayOf(setId, optionId)
                )
            }
        }
        assertTrue(snapshot().selections.isEmpty())
    }

    @Test
    fun answersSurviveDatabaseCloseAndReopen() = runBlocking {
        local().replaceForm(document(), time)
        tap(10, 1)
        tap(10, 3)
        tap(11, 2)
        val saved = snapshot()
        database.close()
        database = open()
        assertEquals(saved, snapshot())
    }

    @Test
    fun refreshKeepsValidAnswersAndDropsOnlyInvalidIdentities() = runBlocking {
        local().replaceForm(document(), time)
        tap(10, 1)
        tap(10, 3)
        tap(11, 2)
        tap(12, 1, 200)
        local().replaceForm(document(options = listOf(2, 3)), time.plusSeconds(60))
        assertEquals(setOf(3L), snapshot().selections[10]?.optionIds)
        assertEquals(setOf(2L), snapshot().selections[11]?.optionIds)
        assertEquals(setOf(1L), snapshot().selections[12]?.optionIds)
        local().replaceForm(document(setId = 101), time.plusSeconds(120))
        assertEquals(setOf(12L), snapshot().selections.keys)
        local().replaceForm(FormDocument(emptyList()), time.plusSeconds(180))
        assertTrue(snapshot().selections.isEmpty())
        assertTrue(snapshot().document.pages.isEmpty())
    }

    @Test
    fun modeChangesUseTheNewOptionOrderAndKeepSingleAnswersWhenBecomingMultiple() = runBlocking {
        local().replaceForm(document(), time)
        tap(10, 1)
        tap(10, 3)
        local().replaceForm(document(multiple = false, options = listOf(3, 2, 1)), time.plusSeconds(60))
        assertEquals(setOf(3L), snapshot().selections[10]?.optionIds)
        local().replaceForm(document(), time.plusSeconds(120))
        assertEquals(setOf(3L), snapshot().selections[10]?.optionIds)
    }

    @Test
    fun refreshFailureRollsBackContentAnswersAndTimeTogether() = runBlocking {
        local().replaceForm(document(), time)
        tap(10, 1)
        val saved = snapshot()
        val replacement = document(title = "Updated").toStorage(time.plusSeconds(60))
        val invalid = replacement.copy(nodes = replacement.nodes + replacement.nodes.last())
        assertThrows(SQLException::class.java) { runBlocking { database.formDao().replaceForm(invalid) } }
        assertEquals(saved, snapshot())
    }

    @Test
    fun failedSingleChoiceInsertionRollsBackItsEarlierDeletion() = runBlocking {
        local().replaceForm(document(multiple = false), time)
        tap(10, 1)
        val saved = snapshot()
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER fail_answer BEFORE INSERT ON selected_response
            BEGIN SELECT RAISE(ABORT, 'Test write failure'); END
        """.trimIndent())
        val repository = OfflineFormRepository(FormRemoteDataSource { document() }, local(), Clock.systemUTC())
        assertEquals(ChoiceSelectionResult.Failure(ChoiceSelectionFailure.STORAGE), repository.toggleChoiceOption(ChoiceOption(10, 100, 2)))
        assertEquals(saved, snapshot())
    }

    @Test
    fun anAnswerSavedDuringFetchIsIncludedInTheRefreshTransaction() = runBlocking {
        local().replaceForm(document(), time)
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val repository = OfflineFormRepository(FormRemoteDataSource {
            started.complete(Unit)
            finish.await()
            document(title = "Updated")
        }, local(), Clock.fixed(time.plusSeconds(60), ZoneOffset.UTC))
        val refresh = async(Dispatchers.IO) { repository.refreshForm() }
        withTimeout(5_000) { started.await() }
        assertEquals(ChoiceSelectionResult.Success, withTimeout(2_000) { repository.toggleChoiceOption(ChoiceOption(10, 100, 2)) })
        finish.complete(Unit)
        assertEquals(RefreshFormResult.Success, refresh.await())
        assertEquals(setOf(2L), snapshot().selections[10]?.optionIds)
        assertEquals("Updated", snapshot().document.pages.single().title)
        assertEquals(time.plusSeconds(60), snapshot().lastUpdated)
    }

    @Test
    fun aTapAfterRefreshValidatesTheNewQuestionDefinition() = runBlocking {
        local().replaceForm(document(), time)
        local().replaceForm(document(setId = 101), time.plusSeconds(60))
        assertEquals(ChoiceSelectionResult.Failure(ChoiceSelectionFailure.UNAVAILABLE), local().toggleChoiceOption(ChoiceOption(10, 100, 1)))
        tap(10, 1, 101)
        assertEquals(101L, snapshot().selections[10]?.responseSetId)
    }

    @Test
    fun observationEmitsAnswerChangesAndCoherentRefreshedDefinitions() = runBlocking {
        local().replaceForm(document(), time)
        tap(10, 1)
        val first = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        var emissions = 0
        val observed = async(Dispatchers.IO) {
            withTimeout(5_000) {
                local().observeForm().onEach {
                    if (++emissions == 1) first.complete(Unit)
                    if (emissions == 2) second.complete(Unit)
                }.take(3).toList()
            }
        }
        first.await()
        tap(10, 2)
        second.await()
        local().replaceForm(document(multiple = false, options = listOf(2, 1, 3)), time.plusSeconds(60))
        val snapshots = observed.await().map { requireNotNull(it) }
        assertEquals(listOf(setOf(1L), setOf(1L, 2L), setOf(2L)), snapshots.map { it.selections[10]?.optionIds })
        assertEquals(listOf(time, time, time.plusSeconds(60)), snapshots.map { it.lastUpdated })
        assertTrue((snapshots[1].document.pages.single().items.first() as FormItem.Choice).responseSet.allowsMultipleSelection)
        assertEquals(false, (snapshots[2].document.pages.single().items.first() as FormItem.Choice).responseSet.allowsMultipleSelection)
    }

    @Test
    fun concurrentTogglesAreAtomicAndSingleChoiceNeverKeepsTwoAnswers() = runBlocking {
        local().replaceForm(document(), time)
        (1..20).map { async(Dispatchers.IO) { local().toggleChoiceOption(ChoiceOption(10, 100, 1)) } }
            .awaitAll().forEach { assertEquals(ChoiceSelectionResult.Success, it) }
        assertEquals(null, snapshot().selections[10])
        local().replaceForm(document(multiple = false), time)
        (1..20).map { index -> async(Dispatchers.IO) { local().toggleChoiceOption(ChoiceOption(10, 100, (index % 3 + 1).toLong())) } }
            .awaitAll().forEach { assertEquals(ChoiceSelectionResult.Success, it) }
        assertEquals(1, snapshot().selections[10]?.optionIds?.size)
    }

    private fun open() = Room.databaseBuilder(context, FormDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
    private fun local() = RoomFormLocalDataSource(database.formDao(), Dispatchers.IO)
    private suspend fun snapshot() = requireNotNull(local().observeForm().first())
    private suspend fun tap(questionId: Long, optionId: Long, setId: Long = 100) {
        assertEquals(ChoiceSelectionResult.Success, local().toggleChoiceOption(ChoiceOption(questionId, setId, optionId)))
    }

    private fun document(multiple: Boolean = true, options: List<Int> = listOf(1, 2, 3), setId: Long = 100, title: String = "Page"): FormDocument {
        val set = ResponseSet(setId, multiple, options.map { ResponseOption(it.toLong(), "Option $it", null) })
        val other = ResponseSet(200, true, listOf(ResponseOption(1, "Other set option", null)))
        return FormDocument(listOf(FormPage(1, title, listOf(
            FormItem.Choice(10, "First question", set), FormItem.Choice(11, "Shared question", set),
            FormItem.Choice(12, "Other set question", other), FormItem.Text(13, "Text")
        ))))
    }
}
