package com.lumiformtest.form.data.mapper

import com.lumiformtest.form.data.local.StoredResponseSet
import com.lumiformtest.form.data.local.entity.FormNodeEntity
import com.lumiformtest.form.data.local.entity.FormNodeType
import com.lumiformtest.form.data.local.entity.ResponseOptionEntity
import com.lumiformtest.form.data.local.entity.ResponseSetEntity
import com.lumiformtest.form.data.local.entity.SelectedResponseEntity
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelection
import com.lumiformtest.form.domain.policy.ChoiceDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChoiceDefinitionMapperTest {
    @Test
    fun `saved choice definition maps to domain in explicit option order`() {
        val node = FormNodeEntity(10, 1, 0, FormNodeType.CHOICE, responseSetId = 100)
        val set = StoredResponseSet(ResponseSetEntity(100, true), listOf(
            ResponseOptionEntity(100, 2, 1, "Two", null), ResponseOptionEntity(100, 1, 0, "One", 2)
        ))
        assertEquals(ChoiceDefinition(10, 100, true, listOf(1, 2)), node.toChoiceDefinition(set))
        assertNull(node.toChoiceDefinition(null))
        assertNull(node.copy(type = FormNodeType.TEXT).toChoiceDefinition(set))
        assertNull(node.copy(responseSetId = 200).toChoiceDefinition(set))
    }

    @Test
    fun `chosen answer maps back with its question and snapshot identity`() {
        assertEquals(listOf(SelectedResponseEntity(10, 100, 2, 7)), ChoiceSelection(100, setOf(2)).toEntities(10, 7))
        assertEquals(emptyList<SelectedResponseEntity>(), ChoiceSelection(100, emptySet()).toEntities(10, 7))
        assertEquals(ChoiceOption(10, 100, 2), SelectedResponseEntity(10, 100, 2, 7).toDomain())
    }
}
