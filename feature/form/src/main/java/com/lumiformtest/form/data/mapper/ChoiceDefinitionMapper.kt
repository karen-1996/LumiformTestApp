package com.lumiformtest.form.data.mapper

import com.lumiformtest.form.data.local.StoredResponseSet
import com.lumiformtest.form.data.local.entity.FormNodeEntity
import com.lumiformtest.form.data.local.entity.FormNodeType
import com.lumiformtest.form.data.local.entity.SelectedResponseEntity
import com.lumiformtest.form.domain.model.ChoiceSelection
import com.lumiformtest.form.domain.policy.ChoiceDefinition

internal fun FormNodeEntity.toChoiceDefinition(set: StoredResponseSet?): ChoiceDefinition? =
    if (type != FormNodeType.CHOICE || set == null || responseSetId != set.definition.id) null
    else ChoiceDefinition(id, set.definition.id, set.definition.multipleSelection, set.responses.sortedBy { it.position }.map { it.id })

internal fun ChoiceSelection.toEntities(questionId: Long, snapshotId: Int): List<SelectedResponseEntity> =
    optionIds.map { SelectedResponseEntity(questionId, responseSetId, it, snapshotId) }
