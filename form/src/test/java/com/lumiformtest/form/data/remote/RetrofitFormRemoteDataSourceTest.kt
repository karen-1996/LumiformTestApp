package com.lumiformtest.form.data.remote

import com.lumiformtest.form.data.mapper.FormDataMapper
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit

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

        assertThrows(HttpException::class.java) { runBlocking { source().fetchForm() } }
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

        assertThrows(IOException::class.java) { runBlocking { source.fetchForm() } }
    }

    private fun source(): RetrofitFormRemoteDataSource {
        val api = Retrofit.Builder().baseUrl(server.url("/")).build().create(FormApi::class.java)
        return RetrofitFormRemoteDataSource(api, FormJsonParser(FormDataMapper()), Dispatchers.IO)
    }
}
