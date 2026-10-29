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
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class FormDao {
    @Transaction
    @Query("SELECT * FROM form_metadata WHERE id = 0")
    abstract fun observeForm(): Flow<StoredForm?>

    @Transaction
    open suspend fun replaceForm(form: StoredForm) {
        // Readers see one complete snapshot, including its successful refresh time.
        clearNodes()
        clearResponseSets()
        upsertMetadata(form.metadata)
        insertResponseSets(form.responseSets.map { it.definition })
        insertResponses(form.responseSets.flatMap { it.responses })
        insertNodes(form.nodes)
    }

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
