package com.lumiformtest.form.data.local

import android.database.SQLException
import com.lumiformtest.form.data.mapper.toMappedDefinition
import com.lumiformtest.form.data.mapper.withAnswers
import com.lumiformtest.form.data.mapper.toStorage
import com.lumiformtest.form.data.mapper.toEntity
import com.lumiformtest.form.data.mapper.toDomainImage
import com.lumiformtest.form.di.FormIoDispatcher
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.FormImage
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
import kotlinx.coroutines.flow.combineTransform
import kotlinx.coroutines.withContext

internal interface FormLocalDataSource {
    fun observeForm(): Flow<FormSnapshot?>
    fun observeImage(imageId: Long): Flow<FormImage?>
    suspend fun replaceForm(document: FormDocument, lastUpdated: Instant)
    suspend fun toggleChoiceOption(option: ChoiceOption): ChoiceSelectionResult
}

internal class RoomFormLocalDataSource @Inject constructor(
    private val dao: FormDao,
    @FormIoDispatcher private val dispatcher: CoroutineDispatcher
) : FormLocalDataSource {
    override fun observeForm(): Flow<FormSnapshot?> = combineTransform(
        dao.observeDefinition().map { it?.toMappedDefinition() }, dao.observeAnswers()
    ) { definition, answers ->
        // Room queries can publish in either order. Never mix two definition versions.
        if (definition?.snapshot?.definitionVersion == answers?.metadata?.definitionVersion) {
            emit(if (definition == null || answers == null) null else definition.withAnswers(answers))
        }
    }.catch { error ->
        if (error is SQLException) throw FormStorageException("Cannot read the saved form", error)
        throw error
    }.distinctUntilChanged().flowOn(dispatcher)

    override fun observeImage(imageId: Long): Flow<FormImage?> = dao.observeImageNode(imageId)
        .map { it?.toDomainImage() }
        .catch { error ->
            if (error is SQLException) throw FormStorageException("Cannot read the saved image", error)
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
