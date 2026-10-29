package com.lumiformtest.form.data.mapper

import com.lumiformtest.form.data.local.FormStorageException
import com.lumiformtest.form.data.remote.FormJsonParser
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.FormSnapshot
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FormEntityMapperTest {
    private val mapper = FormEntityMapper()
    private val time = Instant.parse("2026-10-29T12:00:00Z")

    @Test
    fun `the complete sample survives relational mapping even when SQL rows are unordered`() {
        val document = sample()
        val stored = mapper.toStorage(document, time)
        val unordered = stored.copy(
            nodes = stored.nodes.reversed(),
            responseSets = stored.responseSets.reversed().map { it.copy(responses = it.responses.reversed()) }
        )

        assertEquals(FormSnapshot(document, time), mapper.toDomain(unordered))
    }

    @Test
    fun `an empty form still has successful refresh metadata`() {
        val empty = FormDocument(emptyList())

        assertEquals(FormSnapshot(empty, time), mapper.toDomain(mapper.toStorage(empty, time)))
    }

    @Test
    fun `detached saved nodes fail instead of disappearing`() {
        val stored = mapper.toStorage(sample(), time)
        val invalid = stored.copy(nodes = stored.nodes.map { if (it.id == 3L) it.copy(parentId = 999) else it })

        assertThrows(FormStorageException::class.java) { mapper.toDomain(invalid) }
    }

    @Test
    fun `missing required saved fields produce a storage failure`() {
        val stored = mapper.toStorage(sample(), time)
        val invalid = stored.copy(nodes = stored.nodes.map { if (it.id == 3L) it.copy(content = null) else it })

        assertThrows(FormStorageException::class.java) { mapper.toDomain(invalid) }
    }

    private fun sample(): FormDocument {
        val input = requireNotNull(javaClass.getResourceAsStream("/fixtures/lumiform-android-test.json"))
        return input.bufferedReader().use { FormJsonParser(FormDataMapper()).parse(it.readText()) }
    }
}
