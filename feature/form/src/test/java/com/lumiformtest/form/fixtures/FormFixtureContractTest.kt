package com.lumiformtest.form.fixtures

import com.lumiformtest.form.data.mapper.FormDataMapper
import com.lumiformtest.form.data.mapper.FormEntityMapper
import com.lumiformtest.form.data.remote.FormJsonParser
import com.lumiformtest.form.data.remote.InvalidFormContentException
import com.lumiformtest.form.domain.model.FormSnapshot
import com.lumiformtest.form.presentation.screens.form.mapper.FormUiMapper
import java.time.Instant
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
internal class FormFixtureContractTest(private val fixture: FormFixture) {
    private val parser by lazy { FormJsonParser(FormDataMapper()) }
    private val mapper by lazy { FormEntityMapper() }

    @Test
    fun fixtureMatchesItsContractAcrossDataDomainAndUi() {
        val body = read(fixture.path)
        if (!fixture.accepted) {
            assertThrows(fixture.file, InvalidFormContentException::class.java) { parser.parse(body) }
            return
        }
        val document = parser.parse(body)
        val snapshot = FormSnapshot(document, Instant.parse("2026-10-30T12:00:00Z"))
        val stored = mapper.toStorage(document, snapshot.lastUpdated)
        // Room can return relations in any order; stored positions own display order.
        val shuffled = stored.copy(nodes = stored.nodes.shuffled(Random(42)),
            responseSets = stored.responseSets.reversed().map { it.copy(responses = it.responses.reversed()) })
        assertEquals(fixture.file, snapshot, mapper.toDomain(shuffled))
        val ui = FormUiMapper().map(snapshot)
        assertEquals(fixture.file, fixture.pages, document.pages.size)
        assertEquals(fixture.file, fixture.rows, ui.rows.size)
        assertEquals(fixture.file, fixture.lastId, ui.rows.lastOrNull()?.id)
        assertEquals("Lazy list keys must be unique: ${fixture.file}", ui.rows.size, ui.rows.map { it.id }.toSet().size)
    }

    companion object {
        private fun read(path: String): String = requireNotNull(FormFixtureContractTest::class.java.getResourceAsStream("/$path")) {
            "Missing fixture: $path"
        }.bufferedReader().use { it.readText() }

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun fixtures(): List<Array<Any>> = FormFixture.cases(::read).map { arrayOf(it) }
    }
}
