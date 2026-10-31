package com.lumiformtest.form.data.remote

import com.lumiformtest.form.di.FormIoDispatcher
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.networking.NetworkConnectivity
import com.lumiformtest.networking.ResponseTooLargeException
import dagger.Lazy
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import retrofit2.HttpException

internal fun interface FormRemoteDataSource {
    suspend fun fetchForm(): FormDocument
}

internal class RetrofitFormRemoteDataSource @Inject constructor(
    private val api: FormApi,
    private val parser: FormJsonParser,
    @FormIoDispatcher private val dispatcher: CoroutineDispatcher,
    private val connectivity: Lazy<NetworkConnectivity>
) : FormRemoteDataSource {
    override suspend fun fetchForm(): FormDocument = withContext(dispatcher) {
        // The existing parser owns decoding and validation for every incoming snapshot.
        try {
            val response = api.fetchForm()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                throw HttpException(response)
            }
            val body = response.body() ?: throw InvalidFormContentException("The form response has no body")
            body.use { parser.parse(it.string()) }
        } catch (error: ResponseTooLargeException) {
            currentCoroutineContext().ensureActive()
            throw InvalidFormContentException("The form response is too large", error)
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            // A failed request alone does not mean the device is offline.
            if (!connectivity.get().isInternetAvailable()) throw NoInternetConnectionException(error)
            throw error
        }
    }
}
