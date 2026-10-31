package com.lumiformtest.networking

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer

class ResponseTooLargeException : IOException("The response exceeds the download limit")

class ResponseSizeLimitInterceptor(
    private val maxBytes: Long = MAX_TEXT_RESPONSE_BYTES
) : Interceptor {
    init { require(maxBytes in 1 until Long.MAX_VALUE) }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        // Images have their own streaming budget in the retained-file store.
        if (chain.request().header("Accept")?.startsWith("image/") == true) return response
        val body = response.body
        if (body.contentLength() > maxBytes) {
            body.close()
            throw ResponseTooLargeException()
        }
        val boundedBody = object : ResponseBody() {
            private val boundedSource by lazy {
                object : ForwardingSource(body.source()) {
                    private var bytesRead = 0L

                    override fun read(sink: Buffer, byteCount: Long): Long {
                        val count = super.read(sink, minOf(byteCount, maxBytes - bytesRead + 1))
                        if (count != -1L) bytesRead += count
                        if (bytesRead > maxBytes) {
                            close()
                            throw ResponseTooLargeException()
                        }
                        return count
                    }
                }.buffer()
            }

            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun source() = boundedSource
        }
        return response.newBuilder().body(boundedBody).build()
    }

    companion object {
        const val MAX_TEXT_RESPONSE_BYTES: Long = 5L * 1024 * 1024
    }
}
