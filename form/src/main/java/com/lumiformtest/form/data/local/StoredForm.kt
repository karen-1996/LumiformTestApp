package com.lumiformtest.form.data.local

import androidx.room.Embedded
import androidx.room.Relation
import com.lumiformtest.form.data.local.entity.FormMetadataEntity
import com.lumiformtest.form.data.local.entity.FormNodeEntity
import com.lumiformtest.form.data.local.entity.ResponseOptionEntity
import com.lumiformtest.form.data.local.entity.ResponseSetEntity

internal data class StoredForm(
    @Embedded val metadata: FormMetadataEntity,
    @Relation(parentColumn = "id", entityColumn = "snapshotId")
    val nodes: List<FormNodeEntity>,
    @Relation(parentColumn = "id", entityColumn = "snapshotId", entity = ResponseSetEntity::class)
    val responseSets: List<StoredResponseSet>
)

internal data class StoredResponseSet(
    @Embedded val definition: ResponseSetEntity,
    @Relation(parentColumn = "id", entityColumn = "responseSetId")
    val responses: List<ResponseOptionEntity>
)
