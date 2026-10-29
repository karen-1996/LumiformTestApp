package com.lumiformtest.form.data.mapper

import com.lumiformtest.form.data.local.FormStorageException
import com.lumiformtest.form.data.local.StoredForm
import com.lumiformtest.form.data.local.StoredResponseSet
import com.lumiformtest.form.data.local.entity.FormMetadataEntity
import com.lumiformtest.form.data.local.entity.FormNodeEntity
import com.lumiformtest.form.data.local.entity.FormNodeType
import com.lumiformtest.form.data.local.entity.ResponseOptionEntity
import com.lumiformtest.form.data.local.entity.ResponseSetEntity
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.FormPage
import com.lumiformtest.form.domain.model.FormSnapshot
import com.lumiformtest.form.domain.model.ResponseOption
import com.lumiformtest.form.domain.model.ResponseSet
import java.time.Instant
import javax.inject.Inject

internal class FormEntityMapper @Inject constructor() {
    fun toStorage(document: FormDocument, lastUpdated: Instant): StoredForm {
        val nodes = mutableListOf<FormNodeEntity>()
        val sets = linkedMapOf<Long, ResponseSet>()

        fun addItem(item: FormItem, parentId: Long, position: Int) {
            val node = when (item) {
                is FormItem.Section -> FormNodeEntity(item.id, parentId, position, FormNodeType.SECTION, title = item.title)
                is FormItem.Text -> FormNodeEntity(item.id, parentId, position, FormNodeType.TEXT, content = item.text)
                is FormItem.Image -> FormNodeEntity(
                    item.id, parentId, position, FormNodeType.IMAGE, title = item.title, imageUrl = item.imageUrl
                )
                is FormItem.Choice -> {
                    val previous = sets.put(item.responseSet.id, item.responseSet)
                    if (previous != null && previous != item.responseSet) {
                        throw FormStorageException("Conflicting response set ${item.responseSet.id}")
                    }
                    FormNodeEntity(
                        item.id, parentId, position, FormNodeType.CHOICE,
                        content = item.text, responseSetId = item.responseSet.id
                    )
                }
            }
            nodes += node
            if (item is FormItem.Section) {
                item.items.forEachIndexed { index, child -> addItem(child, item.id, index) }
            }
        }
        document.pages.forEachIndexed { position, page ->
            nodes += FormNodeEntity(page.id, null, position, FormNodeType.PAGE, title = page.title)
            page.items.forEachIndexed { index, item -> addItem(item, page.id, index) }
        }
        val responseSets = sets.values.map { set ->
            StoredResponseSet(
                ResponseSetEntity(set.id, set.allowsMultipleSelection),
                set.responses.mapIndexed { position, option ->
                    ResponseOptionEntity(set.id, option.id, position, option.label, option.score)
                }
            )
        }
        return StoredForm(FormMetadataEntity(lastUpdatedMillis = lastUpdated.toEpochMilli()), nodes, responseSets)
    }

    fun toDomain(stored: StoredForm): FormSnapshot {
        val children = stored.nodes.groupBy { it.parentId }
        val responseSets = stored.responseSets.associate { storedSet ->
            val set = storedSet.definition
            set.id to ResponseSet(
                set.id, set.multipleSelection,
                storedSet.responses.sortedBy { it.position }.map { ResponseOption(it.id, it.label, it.score) }
            )
        }
        var visited = 0
        fun mapItem(node: FormNodeEntity): FormItem {
            visited++
            return when (node.type) {
                FormNodeType.SECTION -> FormItem.Section(
                    node.id, node.title.required("title"),
                    children[node.id].orEmpty().sortedBy { it.position }.map(::mapItem)
                )
                FormNodeType.TEXT -> FormItem.Text(node.id, node.content.required("text"))
                FormNodeType.IMAGE -> FormItem.Image(node.id, node.imageUrl.required("image URL"), node.title.required("title"))
                FormNodeType.CHOICE -> FormItem.Choice(
                    node.id, node.content.required("question"),
                    responseSets[node.responseSetId].required("response set")
                )
                FormNodeType.PAGE -> throw FormStorageException("A saved page is not at the root")
            }
        }
        // SQL row order is not the document order. Each parent keeps its own positions.
        val pages = children[null].orEmpty().sortedBy { it.position }.map { page ->
            if (page.type != FormNodeType.PAGE) throw FormStorageException("A saved root is not a page")
            visited++
            FormPage(
                page.id, page.title.required("title"),
                children[page.id].orEmpty().sortedBy { it.position }.map(::mapItem)
            )
        }
        if (visited != stored.nodes.size) throw FormStorageException("Saved content has unreachable nodes")
        return FormSnapshot(FormDocument(pages), Instant.ofEpochMilli(stored.metadata.lastUpdatedMillis))
    }

    private fun <T : Any> T?.required(field: String): T =
        this ?: throw FormStorageException("Missing $field in saved content")
}
