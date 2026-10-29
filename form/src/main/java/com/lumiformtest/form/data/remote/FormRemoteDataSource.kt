package com.lumiformtest.form.data.remote

import com.lumiformtest.form.di.FormIoDispatcher
import com.lumiformtest.form.domain.model.FormDocument
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException

internal fun interface FormRemoteDataSource {
    suspend fun fetchForm(): FormDocument
}

internal class RetrofitFormRemoteDataSource @Inject constructor(
    private val api: FormApi,
    private val parser: FormJsonParser,
    @FormIoDispatcher private val dispatcher: CoroutineDispatcher
) : FormRemoteDataSource {
    override suspend fun fetchForm(): FormDocument = withContext(dispatcher) {
        // The existing parser owns decoding and validation for every incoming snapshot.
        val response = api.fetchForm()
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw HttpException(response)
        }
        val body = response.body() ?: throw InvalidFormContentException("The form response has no body")
        body.use { parser.parse(it.string()) }
    }
}
