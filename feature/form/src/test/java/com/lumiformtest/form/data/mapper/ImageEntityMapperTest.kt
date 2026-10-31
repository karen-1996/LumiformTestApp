package com.lumiformtest.form.data.mapper

import com.lumiformtest.form.data.local.FormStorageException
import com.lumiformtest.form.data.local.entity.FormNodeEntity
import com.lumiformtest.form.data.local.entity.FormNodeType
import com.lumiformtest.form.domain.model.FormImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ImageEntityMapperTest {
    private val image by lazy { FormNodeEntity(9, 2, 0, FormNodeType.IMAGE, title = "Saved", imageUrl = "https://example.com/a") }
    @Test
    fun `one image row maps without a complete saved form`() {
        assertEquals(FormImage(9, "Saved", "https://example.com/a"), image.toDomainImage())
    }
    @Test
    fun `non-image and incomplete saved metadata are read errors`() {
        for (invalid in listOf(image.copy(type = FormNodeType.TEXT), image.copy(title = null), image.copy(imageUrl = null))) {
            assertThrows(FormStorageException::class.java) { invalid.toDomainImage() }
        }
    }
}
