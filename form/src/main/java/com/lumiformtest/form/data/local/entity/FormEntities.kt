package com.lumiformtest.form.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "form_metadata")
internal data class FormMetadataEntity(
    @PrimaryKey val id: Int = 0,
    val lastUpdatedMillis: Long
)

@Entity(
    tableName = "response_set",
    foreignKeys = [ForeignKey(
        entity = FormMetadataEntity::class,
        parentColumns = ["id"], childColumns = ["snapshotId"]
    )],
    indices = [Index("snapshotId")]
)
internal data class ResponseSetEntity(
    @PrimaryKey val id: Long,
    val multipleSelection: Boolean,
    val snapshotId: Int = 0
)

@Entity(
    tableName = "response_option",
    primaryKeys = ["responseSetId", "id"],
    foreignKeys = [ForeignKey(
        entity = ResponseSetEntity::class,
        parentColumns = ["id"], childColumns = ["responseSetId"],
        onDelete = ForeignKey.CASCADE
    )]
)
internal data class ResponseOptionEntity(
    val responseSetId: Long,
    val id: Long,
    val position: Int,
    val label: String,
    val score: Int?
)

@Entity(
    tableName = "form_node",
    foreignKeys = [
        ForeignKey(entity = FormMetadataEntity::class, parentColumns = ["id"], childColumns = ["snapshotId"]),
        ForeignKey(
            entity = FormNodeEntity::class,
            parentColumns = ["id"], childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(entity = ResponseSetEntity::class, parentColumns = ["id"], childColumns = ["responseSetId"])
    ],
    indices = [Index("snapshotId"), Index("parentId"), Index("responseSetId")]
)
internal data class FormNodeEntity(
    @PrimaryKey val id: Long,
    val parentId: Long?,
    val position: Int,
    val type: FormNodeType,
    val title: String? = null,
    val content: String? = null,
    val imageUrl: String? = null,
    val responseSetId: Long? = null,
    val snapshotId: Int = 0
)

internal enum class FormNodeType { PAGE, SECTION, TEXT, IMAGE, CHOICE }
