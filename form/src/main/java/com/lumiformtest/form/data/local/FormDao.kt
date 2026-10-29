package com.lumiformtest.form.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.lumiformtest.form.data.local.entity.FormMetadataEntity
import com.lumiformtest.form.data.local.entity.FormNodeEntity
import com.lumiformtest.form.data.local.entity.ResponseOptionEntity
import com.lumiformtest.form.data.local.entity.ResponseSetEntity
import com.lumiformtest.form.data.local.entity.FormNodeType
import com.lumiformtest.form.data.local.entity.SelectedResponseEntity
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
        if (question.type != FormNodeType.CHOICE || question.responseSetId != option.responseSetId) return false
        val set = readResponseSet(option.responseSetId) ?: return false
        if (!optionExists(option.responseSetId, option.optionId)) return false
        val selected = readQuestionSelections(question.id)
        if (!set.multipleSelection) {
            if (selected.singleOrNull()?.optionId == option.optionId) return true
            clearQuestionSelections(question.id)
            insertSelections(listOf(option.copy(snapshotId = question.snapshotId)))
        } else if (selected.any { it.optionId == option.optionId }) {
            deleteSelection(option)
        } else {
            insertSelections(listOf(option.copy(snapshotId = question.snapshotId)))
        }
        return true
    }

    @Query("SELECT * FROM selected_response")
    protected abstract suspend fun readSelections(): List<SelectedResponseEntity>

    @Query("SELECT * FROM selected_response WHERE questionId = :questionId")
    protected abstract suspend fun readQuestionSelections(questionId: Long): List<SelectedResponseEntity>

    @Query("SELECT * FROM form_node WHERE id = :id")
    protected abstract suspend fun readNode(id: Long): FormNodeEntity?

    @Query("SELECT * FROM response_set WHERE id = :id")
    protected abstract suspend fun readResponseSet(id: Long): ResponseSetEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM response_option WHERE responseSetId = :setId AND id = :optionId)")
    protected abstract suspend fun optionExists(setId: Long, optionId: Long): Boolean

    @Query("DELETE FROM selected_response WHERE questionId = :questionId")
    protected abstract suspend fun clearQuestionSelections(questionId: Long)

    @Delete
    protected abstract suspend fun deleteSelection(selection: SelectedResponseEntity)

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
