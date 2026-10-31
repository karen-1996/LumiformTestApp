package com.lumiformtest.form.presentation.screens.form.mapper

import com.lumiformtest.form.data.mapper.FormDataMapper
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Test

class FormUiMapperTest {
    private val mapper by lazy { FormUiMapper() }
    private val time by lazy { Instant.parse("2026-10-29T12:00:00Z") }

    @Test
    fun `real document becomes ordered UI rows with all question metadata`() {
        val body = requireNotNull(javaClass.getResourceAsStream("/fixtures/lumiform-android-test.json"))
            .bufferedReader().use { it.readText() }
        val document = FormJsonParser(FormDataMapper()).parse(body)

        val ui = mapper.map(FormSnapshot(document, time))

        assertEquals((1L..14L).toList(), ui.rows.map { it.id })
        assertEquals(listOf(0, 1, 2, 2, 1, 2, 2, 3, 3, 0, 1, 2, 2, 2), ui.rows.map { it.depth })
        assertEquals(listOf(null, 1L, 2L, 2L, 1L, 5L, 5L, 7L, 7L, null, 10L, 11L, 11L, 11L), ui.rows.map { it.parentId })
        assertEquals(time.toEpochMilli(), ui.lastUpdatedEpochMillis)
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

        val ui = mapper.map(FormSnapshot(document, time))

        assertEquals(listOf(90L, 5L, 12L, 2L, 3L, 1L), ui.rows.map { it.id })
        assertEquals(listOf(0, 1, 1, 2, 1, 0), ui.rows.map { it.depth })
        assertEquals("Direct question", (ui.rows[1] as FormRowUiModel.Text).text)
        assertEquals("Empty nested", (ui.rows[3] as FormRowUiModel.Section).title)
        assertEquals("Empty page", (ui.rows.last() as FormRowUiModel.Page).title)
    }

    @Test
    fun `empty snapshot retains its successful update time`() {
        val ui = mapper.map(FormSnapshot(FormDocument(emptyList()), time))
        assertTrue(ui.rows.isEmpty())
        assertEquals(time.toEpochMilli(), ui.lastUpdatedEpochMillis)
    }
    @Test
    fun `selection projection respects question and response set identity without mutating old rows`() {
        val set = ResponseSet(100, true, listOf(ResponseOption(1, "One", null), ResponseOption(2, "Two", null)))
        val snapshot = FormSnapshot(FormDocument(listOf(FormPage(1, "Page", listOf(
            FormItem.Choice(10, "First", set), FormItem.Choice(11, "Shared", set)
        )))), time)
        val original = mapper.map(snapshot)
        val changed = mapper.applySelections(original.rows, snapshot.copy(selections = mapOf(10L to ChoiceSelection(100, setOf(2)))))
        assertEquals(setOf(2L), (changed.rows[1] as FormRowUiModel.Choice).selectedOptionIds)
        assertTrue((original.rows[1] as FormRowUiModel.Choice).selectedOptionIds.isEmpty())
        assertSame(original.rows[0], changed.rows[0])
        assertSame(original.rows[2], changed.rows[2])
        val wrongSet = mapper.applySelections(original.rows, snapshot.copy(selections = mapOf(10L to ChoiceSelection(200, setOf(2)))))
        assertTrue((wrongSet.rows[1] as FormRowUiModel.Choice).selectedOptionIds.isEmpty())
        assertEquals(ChoiceOption(10, 100, 2), mapper.map(ChoiceOptionUiInput(10, 100, 2)))
    }

}
