package com.lumiformtest.form.data.local

import android.database.SQLException
import com.lumiformtest.form.data.mapper.FormEntityMapper
import com.lumiformtest.form.di.FormIoDispatcher
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.FormSnapshot
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
}

internal class RoomFormLocalDataSource @Inject constructor(
    private val dao: FormDao,
    private val mapper: FormEntityMapper,
    @FormIoDispatcher private val dispatcher: CoroutineDispatcher
) : FormLocalDataSource {
    override fun observeForm(): Flow<FormSnapshot?> = dao.observeForm()
        .map { stored -> stored?.let(mapper::toDomain) }
        .catch { error ->
            if (error is SQLException) throw FormStorageException("Cannot read the saved form", error)
            throw error
        }
        .distinctUntilChanged()
        .flowOn(dispatcher)

    override suspend fun replaceForm(document: FormDocument, lastUpdated: Instant) = withContext(dispatcher) {
        val stored = mapper.toStorage(document, lastUpdated)
        try {
            dao.replaceForm(stored)
        } catch (error: SQLException) {
            throw FormStorageException("Cannot save the form", error)
        }
    }
}
