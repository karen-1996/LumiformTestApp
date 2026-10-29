package com.lumiformtest.networking.di

import com.lumiformtest.networking.BuildConfig
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.http.GET

class NetworkModuleTest {
    private val server = MockWebServer()
    private val logging = NetworkModule.loggingInterceptor()
    private val client = NetworkModule.client(logging)

    @Before
    fun setUp() { server.start() }

    @After
    fun tearDown() {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        server.close()
    }

    @Test
    fun `provided retrofit uses the shared client and preserves successful content`() {
        server.enqueue(MockResponse.Builder().body("""[{"type":"page"}]""").build())

        val response = api().fetch().execute()

        assertTrue(response.isSuccessful)
        assertEquals("""[{"type":"page"}]""", response.body()?.use { it.string() })
        assertEquals("/content", requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)).url.encodedPath)
    }

    @Test
    fun `HTTP errors keep their status and body for feature error handling`() {
        server.enqueue(MockResponse.Builder().code(503).body("Unavailable").build())

        val response = api().fetch().execute()

        assertFalse(response.isSuccessful)
        assertEquals(503, response.code())
        assertEquals("Unavailable", response.errorBody()?.use { it.string() })
    }

    @Test
    fun `body logging is installed only in debug builds`() {
        assertEquals(
            if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE,
            logging.level
        )
        assertEquals(BuildConfig.DEBUG, client.interceptors.any { it is HttpLoggingInterceptor })
    }

    private fun api(): TestApi = NetworkModule.retrofit(client).newBuilder()
        .baseUrl(server.url("/"))
        .build()
        .create(TestApi::class.java)

    private interface TestApi {
        @GET("content")
        fun fetch(): Call<ResponseBody>
    }
}
