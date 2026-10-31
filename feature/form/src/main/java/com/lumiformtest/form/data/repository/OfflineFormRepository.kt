package com.lumiformtest.form.data.repository

import com.lumiformtest.form.di.FormWriteScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import com.lumiformtest.form.data.local.FormLocalDataSource
import com.lumiformtest.form.data.local.FormStorageException
import com.lumiformtest.form.data.remote.FormRemoteDataSource
import com.lumiformtest.form.data.remote.InvalidFormContentException
import com.lumiformtest.form.data.remote.NoInternetConnectionException
import com.lumiformtest.form.domain.model.FormRefreshFailure
import com.lumiformtest.form.domain.model.FormObservation
import com.lumiformtest.form.domain.model.FormSnapshot
import com.lumiformtest.form.domain.model.FormImage
import com.lumiformtest.form.domain.model.ImageObservation
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelectionResult
import com.lumiformtest.form.domain.model.ChoiceSelectionFailure
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
    private val clock: Clock,
    @FormWriteScope private val writeScope: CoroutineScope
) : FormRepository {
    private val refreshMutex by lazy { Mutex() }
    private val choiceMutex by lazy { Mutex() }

    override suspend fun toggleChoiceOption(option: ChoiceOption): ChoiceSelectionResult =
        // Enter the FIFO mutex before suspension; cancelling the caller only stops waiting.
        writeScope.async(start = CoroutineStart.UNDISPATCHED) {
            choiceMutex.withLock {
                try {
                    local.toggleChoiceOption(option)
                } catch (_: FormStorageException) {
                    ChoiceSelectionResult.Failure(ChoiceSelectionFailure.STORAGE)
                }
            }
        }.await()

    override fun observeForm() = local.observeForm()
        .map<FormSnapshot?, FormObservation> { FormObservation.Snapshot(it) }
        .catch { error ->
            if (error is FormStorageException) emit(FormObservation.ReadFailure) else throw error
        }

    override fun observeImage(imageId: Long) = local.observeImage(imageId)
        .map<FormImage?, ImageObservation> { ImageObservation.Content(it) }
        .catch { error ->
            if (error is FormStorageException) emit(ImageObservation.ReadFailure) else throw error
        }

    override suspend fun refreshForm(): RefreshFormResult = refreshMutex.withLock {
        // Fetch and validate first. A failed request must not clear saved content.
        val document = try {
            remote.fetchForm()
        } catch (_: NoInternetConnectionException) {
            return@withLock RefreshFormResult.Failure(FormRefreshFailure.OFFLINE)
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
