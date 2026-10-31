package com.lumiformtest.form.data.mapper

import com.lumiformtest.form.data.local.StoredFormAnswers
import com.lumiformtest.form.data.local.StoredFormDefinition
import com.lumiformtest.form.data.local.entity.FormNodeEntity
import com.lumiformtest.form.data.local.entity.SelectedResponseEntity
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.FormPage
import com.lumiformtest.form.domain.model.ResponseOption
import com.lumiformtest.form.domain.model.ResponseSet
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class FormDefinitionMapperTest {
    @Test
    fun `answer projection uses the prepared choice index without rereading definitions`() {
        val document = FormDocument(listOf(FormPage(1, "Page", listOf(FormItem.Choice(10, "Choice",
            ResponseSet(100, true, listOf(ResponseOption(1, "One", null))))))))
        val stored = document.toStorage(Instant.EPOCH)
        var allowReads = true
        val nodes = object : AbstractList<FormNodeEntity>() {
            override val size: Int get() { check(allowReads); return stored.nodes.size }
            override fun get(index: Int): FormNodeEntity { check(allowReads); return stored.nodes[index] }
        }
        val metadata = stored.metadata.copy(definitionVersion = 7)
        val mapped = StoredFormDefinition(metadata, nodes, stored.responseSets).toMappedDefinition()
        allowReads = false
        val answered = mapped.withAnswers(StoredFormAnswers(metadata, listOf(SelectedResponseEntity(10, 100, 1))))
        assertSame(mapped.snapshot.document, answered.document)
        assertEquals(7L, answered.definitionVersion)
        assertEquals(setOf(1L), answered.selections[10]?.optionIds)
        assertEquals(emptyMap<Long, Any>(), mapped.withAnswers(StoredFormAnswers(metadata, emptyList())).selections)
    }
}
