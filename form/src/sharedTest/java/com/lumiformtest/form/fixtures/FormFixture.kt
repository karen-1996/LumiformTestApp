package com.lumiformtest.form.fixtures

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

internal data class FormFixture(
    val file: String,
    val accepted: Boolean,
    val ui: Boolean,
    val rows: Int,
    val pages: Int,
    val lastId: Long?
) {
    val path: String get() = "$ROOT/$file"

    override fun toString() = file

    companion object {
        const val ROOT = "fixtures/scenarios"

        fun cases(read: (String) -> String): List<FormFixture> =
            Json.parseToJsonElement(read("$ROOT/index.json")).jsonObject.getValue("cases").jsonArray.map { value ->
                val item = value.jsonObject
                FormFixture(
                    item.getValue("file").jsonPrimitive.content,
                    item.getValue("accept").jsonPrimitive.boolean,
                    item.getValue("ui").jsonPrimitive.boolean,
                    item["rows"]?.jsonPrimitive?.int ?: 0,
                    item["pages"]?.jsonPrimitive?.int ?: 0,
                    item["lastId"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.long
                )
            }
    }
}
