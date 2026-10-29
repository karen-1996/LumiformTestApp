package com.lumiformtest.form.data.mapper

import com.lumiformtest.form.data.remote.InvalidFormContentException
import com.lumiformtest.form.data.remote.dto.FormNodeDto
import com.lumiformtest.form.data.remote.dto.ResponseOptionDto
import com.lumiformtest.form.data.remote.dto.ResponseSetDto
import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.ResponseOption
import com.lumiformtest.form.domain.model.ResponseSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FormDataMapperTest {
    private val mapper = FormDataMapper()
    private val responseSet = ResponseSetDto(
        id = 10,
        multipleSelection = false,
        responses = listOf(ResponseOptionDto(1, "First", 0), ResponseOptionDto(2, "Second", null))
    )

    @Test
    fun `direct questions map to domain types in source order`() {
        val page = page(
            FormNodeDto.Text(9, "Text"),
            FormNodeDto.Image(4, "https://example.com/image.png", "Image title"),
            FormNodeDto.Choice(2, "Choose", responseSet)
        )

        val expectedSet = ResponseSet(
            id = 10,
            allowsMultipleSelection = false,
            responses = listOf(ResponseOption(1, "First", 0), ResponseOption(2, "Second", null))
        )
        assertEquals(
            listOf(
                FormItem.Text(9, "Text"),
                FormItem.Image(4, "https://example.com/image.png", "Image title"),
                FormItem.Choice(2, "Choose", expectedSet)
            ),
            mapper.map(listOf(page)).pages.single().items
        )
    }

    @Test
    fun `only pages are allowed at the root`() {
        listOf(FormNodeDto.Text(1, "Text"), FormNodeDto.Section(1, "Section", emptyList()))
            .forEach { root ->
                assertThrows(InvalidFormContentException::class.java) { mapper.map(listOf(root)) }
            }
    }

    @Test
    fun `pages cannot appear under pages or sections`() {
        val nestedPage = FormNodeDto.Page(2, "Nested page", emptyList())
        listOf(page(nestedPage), page(FormNodeDto.Section(3, "Section", listOf(nestedPage))))
            .forEach { invalid ->
                assertThrows(InvalidFormContentException::class.java) { mapper.map(listOf(invalid)) }
            }
    }

    @Test
    fun `content IDs are unique across the whole document`() {
        val invalidDocuments = listOf(
            listOf(page(), page()),
            listOf(page(FormNodeDto.Text(1, "Same ID as page"))),
            listOf(page(FormNodeDto.Text(2, "A"), FormNodeDto.Image(2, "url", "B"))),
            listOf(
                page(FormNodeDto.Text(2, "A")),
                FormNodeDto.Page(3, "Other page", listOf(FormNodeDto.Text(2, "B")))
            )
        )
        invalidDocuments.forEach { document ->
            assertThrows(InvalidFormContentException::class.java) { mapper.map(document) }
        }
    }

    @Test
    fun `option IDs must be unique within their response set`() {
        val invalid = responseSet.copy(
            responses = listOf(ResponseOptionDto(1, "A"), ResponseOptionDto(1, "B"))
        )

        assertThrows(InvalidFormContentException::class.java) {
            mapper.map(listOf(page(FormNodeDto.Choice(2, "Choose", invalid))))
        }
    }

    @Test
    fun `ID namespaces are independent and option IDs can repeat in different sets`() {
        val first = responseSet.copy(id = 1, responses = listOf(ResponseOptionDto(1, "A")))
        val second = first.copy(id = 2, responses = listOf(ResponseOptionDto(1, "B")))

        val form = mapper.map(
            listOf(page(FormNodeDto.Choice(2, "First", first), FormNodeDto.Choice(3, "Second", second)))
        )

        val choices = form.pages.single().items.map { it as FormItem.Choice }
        assertEquals(listOf(1L, 2L), choices.map { it.responseSet.id })
        assertEquals(listOf("A", "B"), choices.map { it.responseSet.responses.single().label })
    }

    @Test
    fun `questions can share an identical response set`() {
        val form = mapper.map(
            listOf(
                page(FormNodeDto.Choice(2, "First", responseSet), FormNodeDto.Choice(3, "Second", responseSet))
            )
        )

        val choices = form.pages.single().items.map { it as FormItem.Choice }
        assertEquals(choices[0].responseSet, choices[1].responseSet)
    }

    @Test
    fun `shared response sets cannot have different flags labels scores or order`() {
        val conflictingSets = listOf(
            responseSet.copy(multipleSelection = true),
            responseSet.copy(responses = responseSet.responses.reversed()),
            responseSet.copy(
                responses = listOf(responseSet.responses[0].copy(label = "Changed"), responseSet.responses[1])
            ),
            responseSet.copy(
                responses = listOf(responseSet.responses[0].copy(score = -1), responseSet.responses[1])
            )
        )
        conflictingSets.forEach { conflicting ->
            assertThrows(InvalidFormContentException::class.java) {
                mapper.map(
                    listOf(
                        page(
                            FormNodeDto.Choice(2, "First", responseSet),
                            FormNodeDto.Choice(3, "Second", conflicting)
                        )
                    )
                )
            }
        }
    }

    @Test
    fun `validation state does not leak between snapshots or after a failed mapping`() {
        assertThrows(InvalidFormContentException::class.java) { mapper.map(listOf(page(), page())) }
        mapper.map(listOf(page(FormNodeDto.Choice(2, "Choose", responseSet))))
        val changed = responseSet.copy(responses = listOf(ResponseOptionDto(1, "Updated")))

        val next = mapper.map(listOf(page(FormNodeDto.Choice(2, "Choose", changed))))

        val choice = next.pages.single().items.single() as FormItem.Choice
        assertEquals(listOf(ResponseOption(1, "Updated", null)), choice.responseSet.responses)
    }

    private fun page(vararg items: FormNodeDto) = FormNodeDto.Page(1, "Page", items.toList())
}
