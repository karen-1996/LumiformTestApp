package com.lumiformtest.form.data.local

import androidx.room.Embedded
import androidx.room.Relation
import com.lumiformtest.form.data.local.entity.FormMetadataEntity
import com.lumiformtest.form.data.local.entity.FormNodeEntity
import com.lumiformtest.form.data.local.entity.ResponseOptionEntity
import com.lumiformtest.form.data.local.entity.ResponseSetEntity
import com.lumiformtest.form.data.local.entity.SelectedResponseEntity

internal data class StoredForm(
    @Embedded val metadata: FormMetadataEntity,
    @Relation(parentColumn = "id", entityColumn = "snapshotId")
    val nodes: List<FormNodeEntity>,
    @Relation(parentColumn = "id", entityColumn = "snapshotId", entity = ResponseSetEntity::class)
    val responseSets: List<StoredResponseSet>,
    @Relation(parentColumn = "id", entityColumn = "snapshotId")
    val selections: List<SelectedResponseEntity> = emptyList()
)

internal data class StoredResponseSet(
    @Embedded val definition: ResponseSetEntity,
    @Relation(parentColumn = "id", entityColumn = "responseSetId")
    val responses: List<ResponseOptionEntity>
)

internal data class StoredFormDefinition(
    @Embedded val metadata: FormMetadataEntity,
    @Relation(parentColumn = "id", entityColumn = "snapshotId")
    val nodes: List<FormNodeEntity>,
    @Relation(parentColumn = "id", entityColumn = "snapshotId", entity = ResponseSetEntity::class)
    val responseSets: List<StoredResponseSet>
)

internal data class StoredFormAnswers(
    @Embedded val metadata: FormMetadataEntity,
    @Relation(parentColumn = "id", entityColumn = "snapshotId")
    val selections: List<SelectedResponseEntity>
)
