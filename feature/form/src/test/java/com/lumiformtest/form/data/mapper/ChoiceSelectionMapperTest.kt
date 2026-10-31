package com.lumiformtest.form.data.mapper

import com.lumiformtest.form.data.local.FormStorageException
import com.lumiformtest.form.data.local.entity.SelectedResponseEntity
import com.lumiformtest.form.data.local.entity.FormNodeEntity
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelection
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.FormPage
import com.lumiformtest.form.domain.model.ResponseOption
import com.lumiformtest.form.domain.model.ResponseSet
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChoiceSelectionMapperTest {

    private val form by lazy {
        val set = ResponseSet(100, false, listOf(ResponseOption(1, "One", null), ResponseOption(2, "Two", null)))
        FormDocument(listOf(FormPage(1, "Page", listOf(
            FormItem.Choice(10, "First", set), FormItem.Choice(11, "Second", set)
        )))).toStorage(Instant.EPOCH)
    }

    @Test
    fun `an empty answer set does not need a choice index`() {
        val nodes = object : AbstractList<FormNodeEntity>() {
            override val size: Int get() = error("No answers need definition lookup")
            override fun get(index: Int): FormNodeEntity = error("No answers need definition lookup")
        }
        assertEquals(emptyMap<Long, ChoiceSelection>(), form.copy(nodes = nodes).toSelections())
    }

    @Test
    fun `qualified input identity and independent saved answers cross the data boundary`() {
        assertEquals(SelectedResponseEntity(10, 100, 2), ChoiceOption(10, 100, 2).toEntity())
        val stored = form.copy(selections = listOf(SelectedResponseEntity(10, 100, 1), SelectedResponseEntity(11, 100, 2)))
        assertEquals(mapOf(10L to ChoiceSelection(100, setOf(1)), 11L to ChoiceSelection(100, setOf(2))), stored.toSelections())
        assertEquals(stored.toSelections(), stored.toDomain().selections)
    }

    @Test
    fun `invalid stored relationships and single choice violations are read errors`() {
        val invalid = listOf(
            listOf(SelectedResponseEntity(99, 100, 1)),
            listOf(SelectedResponseEntity(10, 200, 1)),
            listOf(SelectedResponseEntity(10, 100, 99)),
            listOf(SelectedResponseEntity(10, 100, 1, snapshotId = 9)),
            listOf(SelectedResponseEntity(10, 100, 1), SelectedResponseEntity(10, 100, 2)),
            listOf(SelectedResponseEntity(10, 100, 1), SelectedResponseEntity(10, 100, 1))
        )
        for (answers in invalid) {
            assertThrows(FormStorageException::class.java) { form.copy(selections = answers).toSelections() }
        }
    }
}
