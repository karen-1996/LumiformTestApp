package com.lumiformtest.form.data.remote

import com.lumiformtest.form.data.mapper.FormDataMapper
import com.lumiformtest.form.data.remote.dto.FormNodeDto
import com.lumiformtest.form.domain.model.FormDocument
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal class FormJsonParser @Inject constructor(private val mapper: FormDataMapper) {
    private val json = Json {
        // Extra metadata is safe to ignore. Unknown content types still fail decoding.
        ignoreUnknownKeys = true
    }

    fun parse(content: String): FormDocument {
        val nodes = try {
            json.decodeFromString<List<FormNodeDto>>(content)
        } catch (error: SerializationException) {
            throw InvalidFormContentException("The form JSON cannot be decoded", error)
        }
        return mapper.map(nodes)
    }
}
