package com.lumiformtest.form.data.remote

import com.lumiformtest.form.data.mapper.FormDataMapper
import com.lumiformtest.networking.NetworkConnectivity
import dagger.Lazy
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.Response
import okhttp3.ResponseBody

class RetrofitFormRemoteDataSourceTest {
    private val server = MockWebServer()
    private var serverClosed = false

    @Before
    fun setUp() { server.start() }

    @After
    fun tearDown() { if (!serverClosed) server.close() }

    @Test
    fun `fetch uses the challenge path and validates the real response`() = runBlocking {
        val input = requireNotNull(javaClass.getResourceAsStream("/fixtures/lumiform-android-test.json"))
        val body = input.bufferedReader().use { it.readText() }
        server.enqueue(MockResponse.Builder().body(body).build())

        val document = source().fetchForm()

        assertEquals(listOf("Main Page", "Second Page"), document.pages.map { it.title })
        val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals(
            "/aruana-lumiform/383e1291df3e0cc1d49aeb14d45f5b94/raw/lumiform-android-test.json",
            request.url.encodedPath
        )
    }

    @Test
    fun `an unsuccessful HTTP response is a fetch failure`() {
        server.enqueue(MockResponse.Builder().code(503).body("Unavailable").build())

        assertThrows(HttpException::class.java) {
            runBlocking { source(Lazy { error("HTTP errors should not query connectivity") }).fetchForm() }
        }
    }

    @Test
    fun `invalid response content fails before it reaches storage`() {
        server.enqueue(MockResponse.Builder().body("""[{"type":"unsupported"}]""").build())

        assertThrows(InvalidFormContentException::class.java) { runBlocking { source().fetchForm() } }
    }

    @Test
    fun `a successful HTTP status without content is not treated as an empty form`() {
        server.enqueue(MockResponse.Builder().code(204).build())

        assertThrows(InvalidFormContentException::class.java) { runBlocking { source().fetchForm() } }
    }

    @Test
    fun `connection failures remain distinguishable from invalid content`() {
        val source = source()
        server.close()
        serverClosed = true

        val failure = assertThrows(IOException::class.java) { runBlocking { source.fetchForm() } }
        assertFalse(failure is NoInternetConnectionException)
    }

    @Test
    fun `a transport failure without internet gets a distinct offline cause`() {
        val source = source(Lazy { NetworkConnectivity { false } })
        server.close()
        serverClosed = true
        val failure = assertThrows(NoInternetConnectionException::class.java) { runBlocking { source.fetchForm() } }
        assertTrue(failure.cause is IOException)
    }

    @Test
    fun `cancelled requests do not query connectivity or become offline failures`() = runBlocking {
        var connectivityCreated = false
        val api = object : FormApi {
            override suspend fun fetchForm(): Response<ResponseBody> {
                currentCoroutineContext().cancel()
                throw IOException("Cancelled transport")
            }
        }
        val source = RetrofitFormRemoteDataSource(api, FormJsonParser(FormDataMapper()), Dispatchers.IO, Lazy {
            connectivityCreated = true
            NetworkConnectivity { false }
        })
        val request = launch { source.fetchForm() }
        request.join()
        assertTrue(request.isCancelled)
        assertFalse(connectivityCreated)
    }

    @Test
    fun `successful requests do not depend on or initialize the connectivity checker`() = runBlocking {
        server.enqueue(MockResponse.Builder().body("[]").build())
        val source = source(Lazy { error("A successful request must not initialize connectivity") })
        assertTrue(source.fetchForm().pages.isEmpty())
    }

    private fun source(connectivity: Lazy<NetworkConnectivity> = Lazy { NetworkConnectivity { true } }): RetrofitFormRemoteDataSource {
        val api = Retrofit.Builder().baseUrl(server.url("/")).build().create(FormApi::class.java)
        return RetrofitFormRemoteDataSource(api, FormJsonParser(FormDataMapper()), Dispatchers.IO, connectivity)
    }
}
