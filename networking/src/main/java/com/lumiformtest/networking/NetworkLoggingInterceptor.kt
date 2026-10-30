package com.lumiformtest.networking

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

internal class NetworkLoggingInterceptor(
    private val logger: HttpLoggingInterceptor.Logger = HttpLoggingInterceptor.Logger.DEFAULT
) : Interceptor {
    private val bodyLogger by lazy {
        HttpLoggingInterceptor(logger).apply { level = HttpLoggingInterceptor.Level.BODY }
    }
    private val imageLogger by lazy {
        HttpLoggingInterceptor(logger).apply { level = HttpLoggingInterceptor.Level.HEADERS }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        // Body logging buffers the whole response, so image downloads only log headers.
        val selected = if (chain.request().header("Accept")?.startsWith("image/") == true) imageLogger else bodyLogger
        return selected.intercept(chain)
    }
}
