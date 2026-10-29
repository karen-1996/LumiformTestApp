package com.lumiformtest.form.data.repository

import com.lumiformtest.form.data.local.FormLocalDataSource
import com.lumiformtest.form.data.local.FormStorageException
import com.lumiformtest.form.data.remote.FormRemoteDataSource
import com.lumiformtest.form.data.remote.InvalidFormContentException
import com.lumiformtest.form.domain.model.FormRefreshFailure
import com.lumiformtest.form.domain.model.FormObservation
import com.lumiformtest.form.domain.model.FormSnapshot
import com.lumiformtest.form.domain.model.RefreshFormResult
import com.lumiformtest.form.domain.repository.FormRepository
import java.io.IOException
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import retrofit2.HttpException

internal class OfflineFormRepository @Inject constructor(
    private val remote: FormRemoteDataSource,
    private val local: FormLocalDataSource,
    private val clock: Clock
) : FormRepository {
    private val refreshMutex by lazy { Mutex() }

    override fun observeForm() = local.observeForm()
        .map<FormSnapshot?, FormObservation> { FormObservation.Snapshot(it) }
        .catch { error ->
            if (error is FormStorageException) emit(FormObservation.ReadFailure) else throw error
        }

    override suspend fun refreshForm(): RefreshFormResult = refreshMutex.withLock {
        // Fetch and validate first. A failed request must not clear saved content.
        val document = try {
            remote.fetchForm()
        } catch (_: IOException) {
            return@withLock RefreshFormResult.Failure(FormRefreshFailure.FETCH)
        } catch (_: HttpException) {
            return@withLock RefreshFormResult.Failure(FormRefreshFailure.FETCH)
        } catch (_: InvalidFormContentException) {
            return@withLock RefreshFormResult.Failure(FormRefreshFailure.INVALID_CONTENT)
        }
        try {
            local.replaceForm(document, Instant.ofEpochMilli(clock.millis()))
            RefreshFormResult.Success
        } catch (_: FormStorageException) {
            RefreshFormResult.Failure(FormRefreshFailure.STORAGE)
        }
    }
}
