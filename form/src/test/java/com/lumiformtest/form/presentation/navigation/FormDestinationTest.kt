package com.lumiformtest.form.presentation.navigation

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class FormDestinationTest {
    @Test
    fun `form and image keys can be restored from serialized data`() {
        val destinations = listOf(FormDestination.Form, FormDestination.Image(imageId = 9))

        destinations.forEach { destination ->
            val encoded = Json.encodeToString<FormDestination>(destination)
            val restored = Json.decodeFromString<FormDestination>(encoded)
            assertEquals(destination, restored)
        }
    }
}
