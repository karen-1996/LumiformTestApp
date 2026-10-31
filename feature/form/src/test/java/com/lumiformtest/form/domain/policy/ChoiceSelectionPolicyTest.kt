package com.lumiformtest.form.domain.policy

import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChoiceSelectionPolicyTest {
    private val single by lazy { ChoiceDefinition(10, 100, false, listOf(1, 2, 3)) }

    @Test
    fun `single choice replaces the old answer and never deselects a repeated tap`() {
        assertEquals(ChoiceSelection(100, setOf(2)), single.nextSelection(ChoiceOption(10, 100, 2), setOf(1)))
        assertEquals(ChoiceSelection(100, setOf(2)), single.nextSelection(ChoiceOption(10, 100, 2), setOf(2)))
        assertEquals(ChoiceSelection(100, setOf(2)), single.nextSelection(ChoiceOption(10, 100, 2), emptySet()))
    }

    @Test
    fun `multiple choice adds and removes options without mutating the previous answer`() {
        val multiple = single.copy(allowsMultipleSelection = true)
        val previous = setOf(1L, 2L)
        assertEquals(ChoiceSelection(100, setOf(2)), multiple.nextSelection(ChoiceOption(10, 100, 1), previous))
        assertEquals(ChoiceSelection(100, setOf(1, 2, 3)), multiple.nextSelection(ChoiceOption(10, 100, 3), previous))
        assertEquals(ChoiceSelection(100, emptySet()), multiple.nextSelection(ChoiceOption(10, 100, 1), setOf(1)))
        assertEquals(setOf(1L, 2L), previous)
    }

    @Test
    fun `stale question set and option identities are rejected`() {
        for (option in listOf(ChoiceOption(11, 100, 1), ChoiceOption(10, 200, 1), ChoiceOption(10, 100, 99))) {
            assertNull(single.nextSelection(option, setOf(2)))
        }
        assertNull(single.copy(optionIds = emptyList()).nextSelection(ChoiceOption(10, 100, 1), emptySet()))
    }

    @Test
    fun `refresh filters removed options and a mode change respects the new display order`() {
        val previous = ChoiceSelection(100, setOf(1, 2, 99))
        val reordered = single.copy(optionIds = listOf(2, 1, 3))
        assertEquals(ChoiceSelection(100, setOf(2)), reordered.reconcileSelection(previous))
        assertEquals(ChoiceSelection(100, setOf(2, 1)), reordered.copy(allowsMultipleSelection = true).reconcileSelection(previous))
        assertEquals(ChoiceSelection(100, setOf(1, 2, 99)), previous)
    }

    @Test
    fun `missing replaced and completely invalidated answers have no surviving selection`() {
        assertNull(single.reconcileSelection(null))
        assertNull(single.reconcileSelection(ChoiceSelection(200, setOf(1))))
        assertNull(single.reconcileSelection(ChoiceSelection(100, setOf(99))))
        assertNull(single.copy(optionIds = emptyList()).reconcileSelection(ChoiceSelection(100, setOf(1))))
    }
}
