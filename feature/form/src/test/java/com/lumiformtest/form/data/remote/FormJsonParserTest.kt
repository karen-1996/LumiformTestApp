package com.lumiformtest.form.data.remote

import com.lumiformtest.form.data.mapper.FormDataMapper
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.ResponseOption
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FormJsonParserTest {
    private val parser by lazy { FormJsonParser(FormDataMapper()) }

    @Test
    fun `challenge sample preserves page titles and the complete hierarchy in order`() {
        val form = parseSample()

        assertEquals(listOf("Main Page", "Second Page"), form.pages.map { it.title })
        assertEquals((1L..14L).toList(), form.contentIds())
        val introduction = form.pages[0].items[0] as FormItem.Section
        assertEquals("Introduction", introduction.title)
        assertEquals(FormItem.Text(3, "Welcome to the main page!"), introduction.items[0])
        val chapter = form.pages[0].items[1] as FormItem.Section
        assertEquals("Chapter 1", chapter.title)
        val subsection = chapter.items[1] as FormItem.Section
        assertEquals("Subsection 1.1", subsection.title)
        assertEquals(FormItem.Text(8, "This is a subsection under Chapter 1."), subsection.items[0])
    }

    @Test
    fun `challenge images preserve their IDs URLs and titles`() {
        val firstPage = parseSample().pages[0]
        val introduction = firstPage.items[0] as FormItem.Section
        val chapter = firstPage.items[1] as FormItem.Section
        val subsection = chapter.items[1] as FormItem.Section

        assertEquals(
            FormItem.Image(
                id = 4,
                imageUrl = "https://raw.githubusercontent.com/github/explore/main/topics/android/android.png",
                title = "Welcome Image"
            ),
            introduction.items[1]
        )
        assertEquals(
            FormItem.Image(
                id = 9,
                imageUrl = "https://raw.githubusercontent.com/github/explore/main/topics/ios/ios.png",
                title = "Chapter 1 Image"
            ),
            subsection.items[1]
        )
    }

    @Test
    fun `choice settings and nullable or missing scores match the sample`() {
        val section = parseSample().pages[1].items.single() as FormItem.Section
        val single = section.items[1] as FormItem.Choice
        val multiple = section.items[2] as FormItem.Choice

        assertEquals("What is the main topic of Chapter 2?", single.text)
        assertEquals(101L, single.responseSet.id)
        assertFalse(single.responseSet.allowsMultipleSelection)
        assertEquals(
            listOf(
                ResponseOption(1011, "Safety procedures", 1),
                ResponseOption(1012, "Equipment checks", 2),
                ResponseOption(1013, "Not applicable", null)
            ),
            single.responseSet.responses
        )
        assertEquals("Which areas were inspected?", multiple.text)
        assertEquals(102L, multiple.responseSet.id)
        assertTrue(multiple.responseSet.allowsMultipleSelection)
        assertEquals(
            listOf(
                ResponseOption(1021, "Entrance", null),
                ResponseOption(1022, "Storage", null),
                ResponseOption(1023, "Loading bay", null)
            ),
            multiple.responseSet.responses
        )
    }

    @Test
    fun `direct page questions keep source order and text while extra metadata is ignored`() {
        val form = parser.parse(
            """[{"type":"page","id":1,"title":"Page","version":2,"items":[
                {"type":"text","id":9,"content":"  Hello\nworld 👋  ","metadata":{"author":"test"}},
                {"type":"section","id":2,"title":"Section","items":[
                    {"type":"text","id":7,"content":"Nested"}
                ]}
            ]}]"""
        )

        assertEquals(listOf(1L, 9L, 2L, 7L), form.contentIds())
        assertEquals(FormItem.Text(9, "  Hello\nworld 👋  "), form.pages.single().items[0])
    }

    @Test
    fun `empty documents pages and sections remain valid`() {
        assertEquals(FormDocument(emptyList()), parser.parse("[]"))
        assertTrue(parser.parse(page()).pages.single().items.isEmpty())
        val section = parser.parse(
            page("""{"type":"section","id":2,"title":"Empty","items":[]}""")
        ).pages.single().items.single() as FormItem.Section
        assertTrue(section.items.isEmpty())
    }

    @Test
    fun `deeply nested sections are decoded without flattening the domain tree`() {
        var item = """{"type":"text","id":99,"content":"Leaf"}"""
        for (id in 20 downTo 2) {
            item = """{"type":"section","id":$id,"title":"Section $id","items":[$item]}"""
        }
        var mapped = parser.parse(page(item)).pages.single().items.single()
        for (id in 2L..20L) {
            val section = mapped as FormItem.Section
            assertEquals(id, section.id)
            mapped = section.items.single()
        }
        assertEquals(FormItem.Text(99, "Leaf"), mapped)
    }

    @Test
    fun `IDs larger than an Int are preserved`() {
        val form = parser.parse(page("""{"type":"text","id":9000000000,"content":"Large ID"}"""))

        assertEquals(9_000_000_000L, form.pages.single().items.single().id)
    }

    @Test
    fun `malformed JSON and invalid document shapes produce a typed failure`() {
        listOf("{", "{}", "null", "[null]", """[{"type":"page","id":1,"title":"Page","items":{}}]""")
            .forEachIndexed { index, content ->
                val failure = assertThrows("Payload $index", InvalidFormContentException::class.java) {
                    parser.parse(content)
                }
                assertTrue(failure.cause is SerializationException)
            }
    }

    @Test
    fun `missing required IDs content and selection flags are not silently defaulted`() {
        val missingFields = listOf(
            """[{"type":"page","title":"Page","items":[]}]""",
            page("""{"type":"text","id":2}"""),
            page("""{"type":"choice","id":2,"content":"Choose","response_set":{"id":1,"responses":[]}}""")
        )
        missingFields.forEach { content ->
            assertThrows(InvalidFormContentException::class.java) { parser.parse(content) }
        }
    }

    @Test
    fun `nesting limit accepts its boundary and rejects the next level`() {
        fun payload(levels: Int): String {
            val metadata = "[".repeat(levels) + "0" + "]".repeat(levels)
            return """[{"type":"page","id":1,"title":"Page","items":[],"metadata":$metadata}]"""
        }
        assertEquals(1, parser.parse(payload(126)).pages.size)
        assertThrows(InvalidFormContentException::class.java) { parser.parse(payload(127)) }
    }

    @Test
    fun `nesting guard ignores brackets and escaped quotes inside text`() {
        val text = "[ { \\\"quoted\\\" } ] \\".repeat(200)
        val form = parser.parse(page("""{"type":"text","id":2,"content":${JsonPrimitive(text)}}"""))
        assertEquals(text, (form.pages.single().items.single() as FormItem.Text).text)
    }

    @Test
    fun `unsupported content types fail instead of disappearing from the form`() {
        val unknown = """{"type":"video","id":2,"src":"https://example.com/video"}"""
        listOf("[$unknown]", page(unknown)).forEach { content ->
            val failure = assertThrows(InvalidFormContentException::class.java) { parser.parse(content) }
            assertTrue(failure.cause is SerializationException)
        }
    }

    private fun parseSample(): FormDocument {
        val input = requireNotNull(javaClass.getResourceAsStream("/fixtures/lumiform-android-test.json"))
        return input.bufferedReader().use { parser.parse(it.readText()) }
    }

    private fun page(items: String = "") =
        """[{"type":"page","id":1,"title":"Page","items":[$items]}]"""

    private fun FormDocument.contentIds(): List<Long> = buildList {
        fun addItems(items: List<FormItem>) {
            items.forEach { item ->
                add(item.id)
                if (item is FormItem.Section) addItems(item.items)
            }
        }
        pages.forEach { page ->
            add(page.id)
            addItems(page.items)
        }
    }
}
