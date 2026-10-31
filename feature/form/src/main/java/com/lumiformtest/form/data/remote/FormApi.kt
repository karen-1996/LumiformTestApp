package com.lumiformtest.form.data.remote

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Streaming

internal interface FormApi {
    // Return the raw body without Retrofit's extra buffer; parsing stays on IO.
    @Streaming
    @GET("aruana-lumiform/383e1291df3e0cc1d49aeb14d45f5b94/raw/lumiform-android-test.json")
    suspend fun fetchForm(): Response<ResponseBody>
}
