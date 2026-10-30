package com.lumiformtest.form.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Streaming
import retrofit2.http.Url

internal interface ImageApi {
    @Headers("Accept: image/*")
    @Streaming
    @GET
    suspend fun download(@Url url: String): Response<ResponseBody>
}
