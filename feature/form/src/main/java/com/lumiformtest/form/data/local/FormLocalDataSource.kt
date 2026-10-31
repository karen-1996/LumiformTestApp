package com.lumiformtest.form.data.local

import android.database.SQLException
import com.lumiformtest.form.data.mapper.toDomain
import com.lumiformtest.form.data.mapper.toStorage
import com.lumiformtest.form.data.mapper.toEntity
import com.lumiformtest.form.di.FormIoDispatcher
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.FormSnapshot
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelectionResult
import com.lumiformtest.form.domain.model.ChoiceSelectionFailure
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal interface FormLocalDataSource {
    fun observeForm(): Flow<FormSnapshot?>
    suspend fun replaceForm(document: FormDocument, lastUpdated: Instant)
    suspend fun toggleChoiceOption(option: ChoiceOption): ChoiceSelectionResult
}

internal class RoomFormLocalDataSource @Inject constructor(
    private val dao: FormDao,
    @FormIoDispatcher private val dispatcher: CoroutineDispatcher
) : FormLocalDataSource {
    override fun observeForm(): Flow<FormSnapshot?> = dao.observeForm()
        .map { it?.toDomain() }
        .catch { error ->
            if (error is SQLException) throw FormStorageException("Cannot read the saved form", error)
            throw error
        }
        .distinctUntilChanged()
        .flowOn(dispatcher)

    override suspend fun replaceForm(document: FormDocument, lastUpdated: Instant) = withContext(dispatcher) {
        val stored = document.toStorage(lastUpdated)
        try {
            dao.replaceForm(stored)
        } catch (error: SQLException) {
            throw FormStorageException("Cannot save the form", error)
        }
    }

    override suspend fun toggleChoiceOption(option: ChoiceOption): ChoiceSelectionResult = withContext(dispatcher) {
        try {
            if (dao.toggleChoiceOption(option.toEntity())) ChoiceSelectionResult.Success
            else ChoiceSelectionResult.Failure(ChoiceSelectionFailure.UNAVAILABLE)
        } catch (error: SQLException) {
            throw FormStorageException("Cannot save the answer", error)
        }
    }
}
