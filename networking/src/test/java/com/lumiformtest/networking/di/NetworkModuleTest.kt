package com.lumiformtest.networking.di

import com.lumiformtest.networking.BuildConfig
import dagger.Lazy as DaggerLazy
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.ResponseBody
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Call
import retrofit2.http.GET

class NetworkModuleTest {
    private val serverDelegate: Lazy<MockWebServer> = lazy { MockWebServer().apply { start() } }
    private val server: MockWebServer by serverDelegate
    private var loggingCreations = 0
    private val logging: HttpLoggingInterceptor by lazy {
        loggingCreations++
        NetworkModule.loggingInterceptor()
    }
    private val clientDelegate: Lazy<OkHttpClient> = lazy { NetworkModule.client(DaggerLazy { logging }) }
    private val client: OkHttpClient by clientDelegate

    @After
    fun tearDown() {
        if (clientDelegate.isInitialized()) {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
        if (serverDelegate.isInitialized()) server.close()
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
    fun `only debug clients create and install the body logger`() {
        val interceptors = client.interceptors.filterIsInstance<HttpLoggingInterceptor>()
        assertEquals(
            if (BuildConfig.DEBUG) listOf(HttpLoggingInterceptor.Level.BODY) else emptyList(),
            interceptors.map { it.level }
        )
        assertEquals(if (BuildConfig.DEBUG) 1 else 0, loggingCreations)
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
