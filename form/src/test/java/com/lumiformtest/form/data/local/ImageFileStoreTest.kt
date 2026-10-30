package com.lumiformtest.form.data.local

import com.lumiformtest.form.data.remote.ImageApi
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Response
import retrofit2.Retrofit

class ImageFileStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private val directory by lazy { File(temporary.root, "images") }
    private val validator by lazy { ImageFileValidator { it.readText().startsWith("IMAGE:") } }

    @Test
    fun `original bytes survive a new store and do not need the server`() = runBlocking {
        val server = MockWebServer()
        server.start()
        val url = server.url("/full.png?width=1000").toString()
        val api = Retrofit.Builder().baseUrl(server.url("/")).build().create(ImageApi::class.java)
        try {
            server.enqueue(MockResponse.Builder().body("IMAGE:full resolution").build())
            val file = store(api).original(url)
            assertEquals("IMAGE:full resolution", file.readText())
            val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            assertEquals("/full.png", request.url.encodedPath)
            assertEquals("1000", request.url.queryParameter("width"))
        } finally { server.close() }
        val offline = object : ImageApi {
            override suspend fun download(url: String): Response<okhttp3.ResponseBody> = error("No network expected")
        }
        assertEquals("IMAGE:full resolution", store(offline).original(url).readText())
        assertFalse(directory.listFiles().orEmpty().any { it.extension == "part" })
    }

    @Test
    fun `same URL requests share one download and return one complete original`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var requests = 0
        val api = object : ImageApi {
            override suspend fun download(url: String): Response<okhttp3.ResponseBody> {
                requests++
                started.complete(Unit)
                finish.await()
                return Response.success("IMAGE:complete".toResponseBody())
            }
        }
        val store = store(api)
        val first = async { store.original("https://example.com/a.png") }
        started.await()
        val second = async { store.original("https://example.com/a.png") }
        finish.complete(Unit)
        assertEquals(first.await(), second.await())
        assertEquals(1, requests)
        assertEquals("IMAGE:complete", first.await().readText())
    }

    @Test
    fun `failed HTTP and invalid bytes leave no published original and can retry`() = runBlocking {
        var requests = 0
        val api = object : ImageApi {
            override suspend fun download(url: String): Response<okhttp3.ResponseBody> = when (++requests) {
                1 -> Response.error(503, "Unavailable".toResponseBody())
                2 -> Response.success("not an image".toResponseBody())
                else -> Response.success("IMAGE:recovered".toResponseBody())
            }
        }
        val store = store(api)
        repeat(2) {
            try { store.original("https://example.com/a"); fail("Expected failure") } catch (_: IOException) { }
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }
        assertEquals("IMAGE:recovered", store.original("https://example.com/a").readText())
    }

    @Test
    fun `size limit checks declared and streamed lengths and closes the body`() = runBlocking {
        for (declared in listOf(30L, -1L)) {
            var closed = false
            val body = object : okhttp3.ResponseBody() {
                private val buffer = okio.Buffer().writeUtf8("IMAGE:" + "x".repeat(24))
                override fun contentType(): okhttp3.MediaType? = null
                override fun contentLength() = declared
                override fun source(): okio.BufferedSource = buffer
                override fun close() { closed = true; super.close() }
            }
            val api = object : ImageApi {
                override suspend fun download(url: String): Response<okhttp3.ResponseBody> = Response.success(body)
            }
            try { store(api, 10).original("https://example.com/$declared"); fail("Expected failure") } catch (_: IOException) { }
            assertTrue(closed)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
        }
    }

    @Test
    fun `cancelled request propagates and does not publish a partial file`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val api = object : ImageApi {
            override suspend fun download(url: String): Response<okhttp3.ResponseBody> {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val task = async { store(api).original("https://example.com/a") }
        started.await()
        task.cancelAndJoin()
        assertTrue(task.isCancelled)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `cancellation during streaming closes the body and deletes partial bytes`() = runBlocking {
        var closed = false
        val api = object : ImageApi {
            override suspend fun download(url: String): Response<okhttp3.ResponseBody> {
                val job = requireNotNull(currentCoroutineContext()[Job])
                val source = object : okio.ForwardingSource(okio.Buffer().writeUtf8("IMAGE:partial")) {
                    override fun read(sink: okio.Buffer, byteCount: Long): Long {
                        val count = super.read(sink, byteCount)
                        job.cancel()
                        return count
                    }
                    override fun close() { closed = true; super.close() }
                }
                return Response.success(object : okhttp3.ResponseBody() {
                    private val buffered = source.buffer()
                    override fun contentType(): okhttp3.MediaType? = null
                    override fun contentLength() = -1L
                    override fun source(): okio.BufferedSource = buffered
                })
            }
        }
        val task = async { store(api).original("https://example.com/partial") }
        task.join()
        assertTrue(task.isCancelled)
        assertTrue(closed)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `corrupt saved original and interrupted temporary file are replaced on retry`() = runBlocking {
        val api = object : ImageApi {
            override suspend fun download(url: String) = Response.success("IMAGE:good".toResponseBody())
        }
        val store = store(api)
        val file = store.original("https://example.com/a")
        file.writeText("corrupt")
        File(directory, "${file.name}.part").writeText("interrupted")
        assertEquals("IMAGE:good", store.original("https://example.com/a").readText())
        assertFalse(File(directory, "${file.name}.part").exists())
    }

    @Test
    fun `unchanged originals skip repeated validation but a new store validates saved data`() = runBlocking {
        var validations = 0
        var downloads = 0
        val validator = ImageFileValidator { validations++; it.readText().startsWith("IMAGE:") }
        val api = object : ImageApi {
            override suspend fun download(url: String): Response<okhttp3.ResponseBody> {
                downloads++
                return Response.success("IMAGE:good".toResponseBody())
            }
        }
        val store = ImageFileStore(directory, api, validator, Dispatchers.IO)
        val url = "https://example.com/a"
        val file = store.original(url)
        repeat(5) { assertEquals(file, store.original(url)) }
        assertEquals(1, validations)
        assertEquals(1, downloads)
        val reopened = ImageFileStore(directory, api, validator, Dispatchers.IO)
        assertEquals(file, reopened.original(url))
        assertEquals(2, validations)
        assertEquals(1, downloads)
        file.delete()
        assertEquals(file, store.original(url))
        assertEquals(3, validations)
        assertEquals(2, downloads)
    }

    @Test
    fun `distinct query URLs keep separate originals`() = runBlocking {
        val api = object : ImageApi {
            override suspend fun download(url: String) = Response.success("IMAGE:$url".toResponseBody())
        }
        val store = store(api)
        val a = store.original("https://example.com/a?v=1")
        val b = store.original("https://example.com/a?v=2")
        assertNotEquals(a, b)
        assertNotEquals(a.readText(), b.readText())
    }

    private fun store(api: ImageApi, maxBytes: Long = 20L * 1024 * 1024) =
        ImageFileStore(directory, api, validator, Dispatchers.IO, maxBytes)
}
