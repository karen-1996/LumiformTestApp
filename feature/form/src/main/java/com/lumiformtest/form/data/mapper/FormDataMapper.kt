package com.lumiformtest.form.data.mapper

import com.lumiformtest.form.data.remote.InvalidFormContentException
import com.lumiformtest.form.data.remote.dto.FormNodeDto
import com.lumiformtest.form.data.remote.dto.ResponseSetDto
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.FormPage
import com.lumiformtest.form.domain.model.ResponseOption
import com.lumiformtest.form.domain.model.ResponseSet

internal fun List<FormNodeDto>.toDomain(): FormDocument {
    val nodes = this
    // I validate one complete snapshot before it can replace saved content.
    val contentIds = mutableSetOf<Long>()
    val responseSets = mutableMapOf<Long, ResponseSet>()

    fun registerContentId(id: Long) {
        if (!contentIds.add(id)) {
            throw InvalidFormContentException("Duplicate content ID: $id")
        }
    }

    fun mapResponseSet(dto: ResponseSetDto): ResponseSet {
        val optionIds = mutableSetOf<Long>()
        val mapped = ResponseSet(
            id = dto.id,
            allowsMultipleSelection = dto.multipleSelection,
            responses = dto.responses.map { response ->
                if (!optionIds.add(response.id)) {
                    throw InvalidFormContentException(
                        "Duplicate response ID ${response.id} in response set ${dto.id}"
                    )
                }
                ResponseOption(response.id, response.label, response.score)
            }
        )
        val previous = responseSets[dto.id]
        if (previous != null && previous != mapped) {
            throw InvalidFormContentException("Conflicting definitions for response set ${dto.id}")
        }
        responseSets[dto.id] = mapped
        return mapped
    }

    fun mapItem(dto: FormNodeDto): FormItem {
        registerContentId(dto.id)
        return when (dto) {
            is FormNodeDto.Section -> FormItem.Section(
                id = dto.id,
                title = dto.title,
                items = dto.items.map(::mapItem)
            )
            is FormNodeDto.Text -> FormItem.Text(id = dto.id, text = dto.content)
            is FormNodeDto.Image -> FormItem.Image(
                id = dto.id,
                imageUrl = dto.src,
                title = dto.title
            )
            is FormNodeDto.Choice -> FormItem.Choice(
                id = dto.id,
                text = dto.content,
                responseSet = mapResponseSet(dto.responseSet)
            )
            is FormNodeDto.Page -> throw InvalidFormContentException("Pages must be at the root")
        }
    }

    val pages = nodes.map { dto ->
        if (dto !is FormNodeDto.Page) {
            throw InvalidFormContentException("Top-level items must be pages")
        }
        registerContentId(dto.id)
        FormPage(id = dto.id, title = dto.title, items = dto.items.map(::mapItem))
    }
    return FormDocument(pages)
}
