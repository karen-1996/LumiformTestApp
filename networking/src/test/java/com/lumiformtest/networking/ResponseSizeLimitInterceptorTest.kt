package com.lumiformtest.networking

import java.util.concurrent.CopyOnWriteArrayList
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.GzipSink
import okio.buffer
import org.junit.Assert.*
import org.junit.Test
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Streaming

class ResponseSizeLimitInterceptorTest {
    @Test
    fun `streaming Retrofit response stays unread and bounded without a logger`() {
        val client = OkHttpClient.Builder().addInterceptor(ResponseSizeLimitInterceptor(32)).build()
        MockWebServer().use { server ->
            server.start()
            try {
                server.enqueue(MockResponse.Builder().chunkedBody("x".repeat(1024), 16).build())
                val api = Retrofit.Builder().baseUrl(server.url("/")).client(client).build().create(TestApi::class.java)
                api.form().execute().body()!!.use { body ->
                    assertEquals(0L, body.source().buffer.size)
                    assertThrows(ResponseTooLargeException::class.java) { body.string() }
                }
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }

    @Test
    fun `known chunked compressed and error bodies are bounded before debug logging`() {
        val oversized = "body-sentinel".repeat(1000)
        val compressed = Buffer().apply {
            GzipSink(this).buffer().use { it.writeUtf8(oversized) }
        }
        val responses = listOf(
            MockResponse.Builder().body(oversized).build(),
            MockResponse.Builder().chunkedBody(oversized, 16).build(),
            MockResponse.Builder().body(compressed).addHeader("Content-Encoding", "gzip").build(),
            MockResponse.Builder().code(503).chunkedBody(oversized, 16).build()
        )
        val logs = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor(NetworkLoggingInterceptor { logs += it })
            .addInterceptor(ResponseSizeLimitInterceptor(32)).build()
        MockWebServer().use { server ->
            server.start()
            try {
                for (response in responses) {
                    server.enqueue(response)
                    assertThrows(ResponseTooLargeException::class.java) {
                        client.newCall(Request.Builder().url(server.url("/")).build()).execute().use { it.body.string() }
                    }
                }
                assertFalse(logs.any { "body-sentinel" in it })
                server.enqueue(MockResponse.Builder().body("[]").build())
                client.newCall(Request.Builder().url(server.url("/")).build()).execute().use {
                    assertEquals("[]", it.body.string())
                }
                assertTrue(logs.any { it == "[]" })
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }

    @Test
    fun `exact limit passes for known and unknown lengths while image budget stays separate`() {
        val client = OkHttpClient.Builder().addInterceptor(ResponseSizeLimitInterceptor(4)).build()
        MockWebServer().use { server ->
            server.start()
            try {
                for (response in listOf(MockResponse.Builder().body("1234").build(),
                    MockResponse.Builder().chunkedBody("1234", 1).build())) {
                    server.enqueue(response)
                    client.newCall(Request.Builder().url(server.url("/")).build()).execute().use {
                        assertEquals("1234", it.body.string())
                    }
                }
                server.enqueue(MockResponse.Builder().body("original-image-body").build())
                client.newCall(Request.Builder().url(server.url("/")).header("Accept", "image/*").build()).execute().use {
                    assertEquals("original-image-body", it.body.string())
                }
            } finally {
                client.connectionPool.evictAll()
                client.dispatcher.executorService.shutdown()
            }
        }
    }

    private interface TestApi {
        @Streaming @GET("form") fun form(): Call<ResponseBody>
    }
}
