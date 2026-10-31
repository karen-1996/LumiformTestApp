package com.lumiformtest.form.presentation.screens.form.mapper

import com.lumiformtest.form.data.remote.FormJsonParser
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelection
import com.lumiformtest.form.domain.model.ResponseSet
import com.lumiformtest.form.domain.model.ResponseOption
import com.lumiformtest.form.presentation.screens.form.model.ChoiceOptionUiInput
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.FormPage
import com.lumiformtest.form.domain.model.FormSnapshot
import com.lumiformtest.form.presentation.screens.form.model.FormRowUiModel
import java.time.Instant
import kotlinx.collections.immutable.PersistentList
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test

class FormUiMapperTest {

    private val time by lazy { Instant.parse("2026-10-29T12:00:00Z") }

    @Test
    fun `image presence survives answer updates and changes only with definitions`() {
        val set = ResponseSet(100, true, listOf(ResponseOption(1, "One", null)))
        val snapshot = FormSnapshot(FormDocument(listOf(FormPage(1, "Page", listOf(
            FormItem.Choice(10, "Choice", set), FormItem.Image(20, "https://example.com/image", "Image")
        )))), time)
        val original = snapshot.toUiModel()
        assertTrue(original.hasImages)
        val guardedRows = object : PersistentList<FormRowUiModel> by original.rows {
            override fun iterator(): Iterator<FormRowUiModel> = error("Answers must not recalculate image presence")
        }
        val updated = snapshot.copy(selections = mapOf(10L to ChoiceSelection(100, setOf(1))))
            .toUiModel(original.copy(rows = guardedRows))
        assertTrue(updated.hasImages)
        val removed = snapshot.copy(definitionVersion = 1,
            document = FormDocument(listOf(FormPage(1, "Page", listOf(FormItem.Choice(10, "Choice", set)))))).toUiModel(updated)
        assertFalse(removed.hasImages)
    }

    @Test
    fun `real document becomes ordered UI rows with all question metadata`() {
        val body = requireNotNull(javaClass.getResourceAsStream("/fixtures/lumiform-android-test.json"))
            .bufferedReader().use { it.readText() }
        val document = FormJsonParser().parse(body)

        val ui = FormSnapshot(document, time).toUiModel()

        assertEquals((1L..14L).toList(), ui.rows.map { it.id })
        assertEquals(listOf(0, 1, 2, 2, 1, 2, 2, 3, 3, 0, 1, 2, 2, 2), ui.rows.map { it.depth })
        assertEquals(listOf(null, 1L, 2L, 2L, 1L, 5L, 5L, 7L, 7L, null, 10L, 11L, 11L, 11L), ui.rows.map { it.parentId })
        assertEquals(time.toEpochMilli(), ui.lastUpdatedEpochMillis)
        assertSame(ui.rows, FormSnapshot(document, time).toUiModel(ui).rows)
        val image = ui.rows[3] as FormRowUiModel.Image
        assertEquals("Welcome Image", image.title)
        assertEquals("https://raw.githubusercontent.com/github/explore/main/topics/android/android.png", image.imageUrl)
        val choice = ui.rows[12] as FormRowUiModel.Choice
        assertEquals("What is the main topic of Chapter 2?", choice.text)
        assertEquals(101L, choice.responseSet.id)
        assertEquals(false, choice.responseSet.allowsMultipleSelection)
        assertEquals(listOf(1011L, 1012L, 1013L), choice.responseSet.options.map { it.id })
        assertEquals(listOf("Safety procedures", "Equipment checks", "Not applicable"), choice.responseSet.options.map { it.label })
        assertEquals(listOf(1, 2, null), choice.responseSet.options.map { it.score })
        val multiple = ui.rows[13] as FormRowUiModel.Choice
        assertTrue(multiple.responseSet.allowsMultipleSelection)
        assertEquals(listOf(null, null, null), multiple.responseSet.options.map { it.score })
    }

    @Test
    fun `direct questions and empty nested containers keep their position and depth`() {
        val document = FormDocument(listOf(
            FormPage(90, "Page", listOf(
                FormItem.Text(5, "Direct question"),
                FormItem.Section(12, "Outer", listOf(FormItem.Section(2, "Empty nested", emptyList()))),
                FormItem.Text(3, "Last question")
            )),
            FormPage(1, "Empty page", emptyList())
        ))

        val ui = FormSnapshot(document, time).toUiModel()

        assertEquals(listOf(90L, 5L, 12L, 2L, 3L, 1L), ui.rows.map { it.id })
        assertEquals(listOf(0, 1, 1, 2, 1, 0), ui.rows.map { it.depth })
        assertEquals("Direct question", (ui.rows[1] as FormRowUiModel.Text).text)
        assertEquals("Empty nested", (ui.rows[3] as FormRowUiModel.Section).title)
        assertEquals("Empty page", (ui.rows.last() as FormRowUiModel.Page).title)
    }

    @Test
    fun `empty snapshot retains its successful update time`() {
        val ui = FormSnapshot(FormDocument(emptyList()), time).toUiModel()
        assertTrue(ui.rows.isEmpty())
        assertEquals(time.toEpochMilli(), ui.lastUpdatedEpochMillis)
    }
    @Test
    fun `selection projection respects question and response set identity without mutating old rows`() {
        val set = ResponseSet(100, true, listOf(ResponseOption(1, "One", null), ResponseOption(2, "Two", null)))
        val snapshot = FormSnapshot(FormDocument(listOf(FormPage(1, "Page", listOf(
            FormItem.Choice(10, "First", set), FormItem.Choice(11, "Shared", set)
        )))), time)
        val original = snapshot.toUiModel()
        val changed = snapshot.copy(selections = mapOf(10L to ChoiceSelection(100, setOf(2)))).toUiModel(original)
        assertEquals(setOf(2L), (changed.rows[1] as FormRowUiModel.Choice).selectedOptionIds)
        assertTrue((original.rows[1] as FormRowUiModel.Choice).selectedOptionIds.isEmpty())
        assertSame(original.rows[0], changed.rows[0])
        assertSame(original.rows[2], changed.rows[2])
        val wrongSet = snapshot.copy(selections = mapOf(10L to ChoiceSelection(200, setOf(2)))).toUiModel(original)
        assertTrue((wrongSet.rows[1] as FormRowUiModel.Choice).selectedOptionIds.isEmpty())
        assertEquals(ChoiceOption(10, 100, 2), ChoiceOptionUiInput(10, 100, 2).toDomain())
    }

    @Test
    fun `mapping reuses equal rows from state while applying new selections`() {
        val set = ResponseSet(100, true, listOf(ResponseOption(1, "One", null)))
        val snapshot = FormSnapshot(FormDocument(listOf(FormPage(1, "Page", listOf(
            FormItem.Text(2, "Text"), FormItem.Choice(10, "Choice", set)
        )))), time)
        val original = snapshot.toUiModel()
        val selected = snapshot.copy(selections = mapOf(10L to ChoiceSelection(100, setOf(1)))).toUiModel(original)
        assertSame(original.rows[0], selected.rows[0])
        assertSame(original.rows[1], selected.rows[1])
        assertTrue((original.rows[2] as FormRowUiModel.Choice).selectedOptionIds.isEmpty())
        assertEquals(setOf(1L), (selected.rows[2] as FormRowUiModel.Choice).selectedOptionIds)
        val oldChoice = original.rows[2] as FormRowUiModel.Choice
        val newChoice = selected.rows[2] as FormRowUiModel.Choice
        assertSame(oldChoice.responseSet, newChoice.responseSet)
        assertSame(oldChoice.responseSet.options, newChoice.responseSet.options)
        val unchanged = snapshot.copy(lastUpdated = time.plusSeconds(1)).toUiModel(original)
        assertSame(original.rows, unchanged.rows)
        assertEquals(time.plusSeconds(1).toEpochMilli(), unchanged.lastUpdatedEpochMillis)
    }

    @Test
    fun `previous UI content cannot hide reordered or changed definitions with the same IDs`() {
        val snapshot = FormSnapshot(FormDocument(listOf(FormPage(1, "Page", listOf(
            FormItem.Text(2, "Before"), FormItem.Image(3, "https://example.com/a", "Image")
        )))), time)
        val original = snapshot.toUiModel()
        val changed = snapshot.copy(definitionVersion = 1, document = FormDocument(listOf(FormPage(1, "Renamed", listOf(
            FormItem.Image(3, "https://example.com/b", "New image"), FormItem.Text(2, "After")
        ))))).toUiModel(original)
        assertEquals(listOf(1L, 3L, 2L), changed.rows.map { it.id })
        assertEquals("Renamed", (changed.rows[0] as FormRowUiModel.Page).title)
        assertEquals("https://example.com/b", (changed.rows[1] as FormRowUiModel.Image).imageUrl)
        assertEquals("After", (changed.rows[2] as FormRowUiModel.Text).text)
        assertEquals("Before", (original.rows[1] as FormRowUiModel.Text).text)
    }

    @Test
    fun `answer-only updates in a large form retain row and response definition instances`() {
        val set = ResponseSet(100, true, listOf(ResponseOption(1, "One", null), ResponseOption(2, "Two", 2)))
        val document = FormDocument(listOf(FormPage(1, "Page", (2L..1001L).map { FormItem.Choice(it, "Question $it", set) })))
        val snapshot = FormSnapshot(document, time)
        val original = snapshot.toUiModel()
        val changed = snapshot.copy(selections = mapOf(500L to ChoiceSelection(100, setOf(2)))).toUiModel(original)
        for ((before, after) in original.rows.zip(changed.rows)) {
            if (before.id != 500L) assertSame(before, after)
            if (before is FormRowUiModel.Choice && after is FormRowUiModel.Choice) {
                assertSame(before.responseSet, after.responseSet)
                assertSame(before.responseSet.options, after.responseSet.options)
            }
        }
        assertEquals(setOf(2L), (changed.rows.first { it.id == 500L } as FormRowUiModel.Choice).selectedOptionIds)
    }

    @Test
    fun `definition edits replace stale row types positions and response metadata`() {
        val set = ResponseSet(100, true, listOf(ResponseOption(1, "One", null), ResponseOption(2, "Two", 2)))
        val original = FormSnapshot(FormDocument(listOf(FormPage(1, "Page", listOf(
            FormItem.Section(2, "Section", listOf(FormItem.Image(3, "https://example.com/a", "Old"))),
            FormItem.Choice(10, "Old choice", set)
        )))), time).toUiModel()
        val variants = listOf(set.copy(id = 200), set.copy(allowsMultipleSelection = false),
            set.copy(responses = set.responses.reversed()), set.copy(responses = set.responses.take(1)),
            set.copy(responses = listOf(ResponseOption(1, "Renamed", 4), ResponseOption(2, "Two", 2))))
        for (updatedSet in variants) {
            val snapshot = FormSnapshot(FormDocument(listOf(FormPage(1, "Page", listOf(
                FormItem.Image(3, "https://example.com/b", "New"),
                FormItem.Section(2, "Renamed section", listOf(FormItem.Choice(10, "New choice", updatedSet)))
            )))), time, definitionVersion = 1)
            assertEquals(snapshot.toUiModel(), snapshot.toUiModel(original))
        }
        val changedType = FormSnapshot(FormDocument(listOf(FormPage(1, "Page", listOf(FormItem.Text(3, "Now text"))))), time, definitionVersion = 1)
        assertEquals(changedType.toUiModel(), changedType.toUiModel(original))
    }

    @Test
    fun `answer projection never reads the document or iterates the previous row list`() {
        val set = ResponseSet(100, true, listOf(ResponseOption(1, "One", null)))
        var allowDocumentReads = true
        val page = FormPage(1, "Page", (2L..1001L).map { FormItem.Choice(it, "Question $it", set) })
        val pages = object : AbstractList<FormPage>() {
            override val size: Int get() { check(allowDocumentReads); return 1 }
            override fun get(index: Int): FormPage { check(allowDocumentReads); return listOf(page)[index] }
        }
        val snapshot = FormSnapshot(FormDocument(pages), time, definitionVersion = 7)
        val original = snapshot.toUiModel()
        val guardedRows = object : PersistentList<FormRowUiModel> by original.rows {
            override fun iterator(): Iterator<FormRowUiModel> = error("Answer projection must not iterate all rows")
        }
        val guarded = original.copy(rows = guardedRows)
        allowDocumentReads = false
        val selected = snapshot.copy(selections = mapOf(500L to ChoiceSelection(100, setOf(1)))).toUiModel(guarded)
        assertEquals(setOf(1L), (selected.rows[selected.rowIndexById.getValue(500)] as FormRowUiModel.Choice).selectedOptionIds)
        assertSame(original.rows[0], selected.rows[0])
        assertSame(original.rows[1000], selected.rows[1000])
        assertSame(original.rowIndexById, selected.rowIndexById)
        assertFalse(selected == guarded)
        assertSame(selected, snapshot.copy(selections = mapOf(500L to ChoiceSelection(100, setOf(1)))).toUiModel(selected))
        val cleared = snapshot.toUiModel(selected)
        assertTrue((cleared.rows[cleared.rowIndexById.getValue(500)] as FormRowUiModel.Choice).selectedOptionIds.isEmpty())
    }

    @Test
    fun `answer projection handles simultaneous changes removals and unrelated stale identities`() {
        val set = ResponseSet(100, true, listOf(ResponseOption(1, "One", null), ResponseOption(2, "Two", null)))
        val snapshot = FormSnapshot(FormDocument(listOf(FormPage(1, "Page", listOf(
            FormItem.Choice(10, "First", set), FormItem.Choice(11, "Second", set), FormItem.Text(12, "Text")
        )))), time, selections = mapOf(10L to ChoiceSelection(100, setOf(1)), 11L to ChoiceSelection(100, setOf(1))))
        val original = snapshot.toUiModel()
        val changed = snapshot.copy(selections = mapOf(10L to ChoiceSelection(100, setOf(2)),
            11L to ChoiceSelection(200, setOf(1)), 12L to ChoiceSelection(100, setOf(1)), 999L to ChoiceSelection(100, setOf(1))))
            .toUiModel(original)
        assertEquals(setOf(2L), (changed.rows[1] as FormRowUiModel.Choice).selectedOptionIds)
        assertTrue((changed.rows[2] as FormRowUiModel.Choice).selectedOptionIds.isEmpty())
        assertEquals(setOf(10L), changed.answers.keys)
        assertSame(original.rows[3], changed.rows[3])
    }

    @Test
    fun `unchanged saved answers do not even look up their UI rows`() {
        val set = ResponseSet(100, true, listOf(ResponseOption(1, "One", null), ResponseOption(2, "Two", null)))
        val snapshot = FormSnapshot(FormDocument(listOf(FormPage(1, "Page", (2L..1001L).map { FormItem.Choice(it, "Question $it", set) }))),
            time, selections = (2L..1001L).associateWith { ChoiceSelection(100, setOf(1)) }, definitionVersion = 7)
        val original = snapshot.toUiModel()
        val guardedIndex = object : Map<Long, Int> by original.rowIndexById {
            override fun get(key: Long): Int? {
                check(key == 500L) { "An unchanged answer must not look up its row" }
                return original.rowIndexById[key]
            }
        }
        val changed = snapshot.copy(selections = snapshot.selections + (500L to ChoiceSelection(100, setOf(2))))
            .toUiModel(original.copy(rowIndexById = guardedIndex))
        assertEquals(setOf(2L), (changed.rows[original.rowIndexById.getValue(500)] as FormRowUiModel.Choice).selectedOptionIds)
        assertSame(original.rows[0], changed.rows[0])
    }

}
