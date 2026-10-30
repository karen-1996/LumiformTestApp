package com.lumiformtest.networking

import java.util.concurrent.CopyOnWriteArrayList
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Streaming

class NetworkLoggingInterceptorTest {
    @Test
    fun `image logs keep headers without buffering or logging the body`() {
        val logs = CopyOnWriteArrayList<String>()
        val server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().addInterceptor(NetworkLoggingInterceptor { logs += it }).build()
        try {
            val api = Retrofit.Builder().baseUrl(server.url("/")).client(client).build().create(TestApi::class.java)
            server.enqueue(MockResponse.Builder().body("image-body-sentinel").build())
            val image = api.image().execute()
            image.body()!!.use {
                assertEquals(0L, it.source().buffer.size)
                assertEquals("image-body-sentinel", it.string())
            }
            assertTrue(logs.any { "200" in it })
            assertFalse(logs.any { "image-body-sentinel" in it })
            logs.clear()
            server.enqueue(MockResponse.Builder().body("form-body-sentinel").build())
            api.form().execute().body()!!.use { assertEquals("form-body-sentinel", it.string()) }
            assertTrue(logs.any { "form-body-sentinel" in it })
        } finally {
            server.close()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private interface TestApi {
        @Streaming @Headers("Accept: image/*") @GET("image") fun image(): Call<ResponseBody>
        @Streaming @GET("form") fun form(): Call<ResponseBody>
    }
}
