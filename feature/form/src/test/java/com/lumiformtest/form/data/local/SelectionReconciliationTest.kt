package com.lumiformtest.form.data.local

import com.lumiformtest.form.data.mapper.toStorage
import com.lumiformtest.form.data.local.entity.SelectedResponseEntity
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.FormPage
import com.lumiformtest.form.domain.model.ResponseOption
import com.lumiformtest.form.domain.model.ResponseSet
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionReconciliationTest {


    @Test
    fun `shared definitions retain independent answers and ignore metadata changes`() {
        val previous = listOf(SelectedResponseEntity(10, 100, 1), SelectedResponseEntity(11, 100, 3))
        val set = set(multiple = true, options = listOf(3, 2, 1))
        val refreshed = stored(choice(10, set), choice(11, set))

        assertEquals(previous, reconcileSelections(previous, refreshed))
    }

    @Test
    fun `removed options questions and changed question types lose only invalid answers`() {
        val previous = listOf(
            SelectedResponseEntity(10, 100, 1), SelectedResponseEntity(10, 100, 2),
            SelectedResponseEntity(11, 100, 1), SelectedResponseEntity(12, 100, 1)
        )
        val refreshed = stored(choice(10, set(true, listOf(2, 3))), FormItem.Text(11, "Now text"))

        assertEquals(listOf(SelectedResponseEntity(10, 100, 2)), reconcileSelections(previous, refreshed))
    }

    @Test
    fun `multi to single keeps the first surviving answer in refreshed display order`() {
        val previous = listOf(SelectedResponseEntity(10, 100, 1), SelectedResponseEntity(10, 100, 3))

        assertEquals(
            listOf(SelectedResponseEntity(10, 100, 3)),
            reconcileSelections(previous, stored(choice(10, set(false, listOf(3, 2, 1)))))
        )
    }

    @Test
    fun `changed response set does not reuse another sets option identity`() {
        val previous = listOf(SelectedResponseEntity(10, 100, 1), SelectedResponseEntity(11, 200, 1))
        val replacement = set(true, listOf(1), id = 200)

        assertEquals(
            listOf(SelectedResponseEntity(11, 200, 1)),
            reconcileSelections(previous, stored(choice(10, replacement), choice(11, replacement)))
        )
    }

    @Test
    fun `single to multi keeps the existing answer and empty content removes all answers`() {
        val previous = listOf(SelectedResponseEntity(10, 100, 1))
        assertEquals(previous, reconcileSelections(previous, stored(choice(10, set(true, listOf(1, 2))))))
        assertTrue(reconcileSelections(previous, FormDocument(emptyList()).toStorage(Instant.EPOCH)).isEmpty())
    }

    private fun choice(id: Long, set: ResponseSet) = FormItem.Choice(id, "Updated question $id", set)
    private fun set(multiple: Boolean, options: List<Int>, id: Long = 100) = ResponseSet(
        id, multiple, options.map { ResponseOption(it.toLong(), "Updated $it", null) }
    )
    private fun stored(vararg items: FormItem) = FormDocument(listOf(FormPage(1, "Page", items.toList()))).toStorage(Instant.EPOCH)
}
