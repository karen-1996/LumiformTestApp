package com.lumiformtest.form.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.lumiformtest.form.data.local.entity.FormMetadataEntity
import com.lumiformtest.form.data.local.entity.FormNodeEntity
import com.lumiformtest.form.data.local.entity.ResponseOptionEntity
import com.lumiformtest.form.data.local.entity.ResponseSetEntity
import com.lumiformtest.form.data.local.entity.SelectedResponseEntity
import com.lumiformtest.form.data.mapper.toDomain
import com.lumiformtest.form.data.mapper.toChoiceDefinition
import com.lumiformtest.form.data.mapper.toEntities
import com.lumiformtest.form.domain.policy.nextSelection
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class FormDao {
    @Transaction
    @Query("SELECT * FROM form_metadata WHERE id = 0")
    abstract fun observeForm(): Flow<StoredForm?>

    @Transaction
    open suspend fun replaceForm(form: StoredForm) {
        // Capture answers here, after the fetch, so recent taps are not overwritten.
        val selections = reconcileSelections(readSelections(), form)
        clearNodes()
        clearResponseSets()
        upsertMetadata(form.metadata)
        insertResponseSets(form.responseSets.map { it.definition })
        insertResponses(form.responseSets.flatMap { it.responses })
        insertNodes(form.nodes)
        insertSelections(selections)
    }

    @Transaction
    open suspend fun toggleChoiceOption(option: SelectedResponseEntity): Boolean {
        val question = readNode(option.questionId) ?: return false
        val set = question.responseSetId?.let { readResponseSet(it) }
        val definition = question.toChoiceDefinition(set) ?: return false
        val selected = readQuestionSelections(question.id).map { it.optionId }.toSet()
        val next = definition.nextSelection(option.toDomain(), selected) ?: return false
        if (next.optionIds != selected) {
            clearQuestionSelections(question.id)
            insertSelections(next.toEntities(question.id, question.snapshotId))
        }
        return true
    }

    @Query("SELECT * FROM selected_response")
    protected abstract suspend fun readSelections(): List<SelectedResponseEntity>

    @Query("SELECT * FROM selected_response WHERE questionId = :questionId")
    protected abstract suspend fun readQuestionSelections(questionId: Long): List<SelectedResponseEntity>

    @Query("SELECT * FROM form_node WHERE id = :id")
    protected abstract suspend fun readNode(id: Long): FormNodeEntity?

    @Transaction
    @Query("SELECT * FROM response_set WHERE id = :id")
    protected abstract suspend fun readResponseSet(id: Long): StoredResponseSet?

    @Query("DELETE FROM selected_response WHERE questionId = :questionId")
    protected abstract suspend fun clearQuestionSelections(questionId: Long)

    @Insert
    protected abstract suspend fun insertSelections(selections: List<SelectedResponseEntity>)

    @Query("DELETE FROM form_node")
    protected abstract suspend fun clearNodes()

    @Query("DELETE FROM response_set")
    protected abstract suspend fun clearResponseSets()

    @Upsert
    protected abstract suspend fun upsertMetadata(metadata: FormMetadataEntity)

    @Insert
    protected abstract suspend fun insertResponseSets(sets: List<ResponseSetEntity>)

    @Insert
    protected abstract suspend fun insertResponses(responses: List<ResponseOptionEntity>)

    @Insert
    protected abstract suspend fun insertNodes(nodes: List<FormNodeEntity>)
}
