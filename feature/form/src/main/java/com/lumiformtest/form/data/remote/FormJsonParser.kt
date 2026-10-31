package com.lumiformtest.form.data.remote

import com.lumiformtest.form.data.mapper.toDomain
import com.lumiformtest.form.data.remote.dto.FormNodeDto
import com.lumiformtest.form.domain.model.FormDocument
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal class FormJsonParser @Inject constructor() {
    private val json by lazy {
        Json {
            // Extra metadata is safe to ignore. Unknown content types still fail decoding.
            ignoreUnknownKeys = true
        }
    }

    fun parse(content: String): FormDocument {
        validateNesting(content)
        val nodes = try {
            json.decodeFromString<List<FormNodeDto>>(content)
        } catch (error: SerializationException) {
            throw InvalidFormContentException("The form JSON cannot be decoded", error)
        }
        return nodes.toDomain()
    }

    private fun validateNesting(content: String) {
        // Check before recursive decoding; Android's stack can overflow on a deep payload.
        var depth = 0
        var quoted = false
        var escaped = false
        for (character in content) {
            if (quoted) {
                if (escaped) escaped = false
                else when (character) {
                    '\\' -> escaped = true
                    '"' -> quoted = false
                }
            } else when (character) {
                '"' -> quoted = true
                '{', '[' -> if (++depth > MAX_JSON_DEPTH) {
                    throw InvalidFormContentException("The form exceeds the JSON nesting limit")
                }
                '}', ']' -> depth--
            }
        }
    }

    private companion object {
        const val MAX_JSON_DEPTH = 128
    }
}
