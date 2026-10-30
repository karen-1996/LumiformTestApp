package com.lumiformtest.form.data.local

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.lumiformtest.form.data.remote.ImageApi
import com.lumiformtest.form.data.remote.StoredImageFetcher
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class ImagePipelineTest {
    private val context by lazy { InstrumentationRegistry.getInstrumentation().targetContext }

    @Test
    fun thumbnailThenNewLoaderCanDecodeFullOriginalWithoutNetworkOrCoilCache() = runBlocking {
        val directory = File(context.filesDir, "test-image-pipeline-${System.nanoTime()}")
        var requests = 0
        var offline = false
        val png = png()
        val api = object : ImageApi {
            override suspend fun download(url: String): Response<okhttp3.ResponseBody> {
                requests++
                if (offline) throw IOException("Offline")
                return Response.success(png.toResponseBody())
            }
        }
        fun loader() = ImageLoader.Builder(context).diskCache(null).components {
            add(StoredImageFetcher.Factory(ImageFileStore(directory, api, BitmapImageFileValidator(), Dispatchers.IO)))
        }.build()
        val first = loader()
        var second: ImageLoader? = null
        try {
            val thumbnail = first.execute(ImageRequest.Builder(context).data("https://example.com/full.png").size(48).build())
            assertTrue(thumbnail is SuccessResult)
            assertTrue((thumbnail as SuccessResult).image.width <= 96)
            assertEquals(1, requests)
            assertEquals(png.size.toLong(), directory.listFiles()!!.single().length())
            val cached = first.execute(ImageRequest.Builder(context).data("https://example.com/full.png").size(48).build())
            assertEquals(DataSource.MEMORY_CACHE, (cached as SuccessResult).dataSource)
            assertEquals(1, requests)
            first.shutdown()
            offline = true
            second = loader()
            val original = second.execute(ImageRequest.Builder(context).data("https://example.com/full.png").size(512).build())
            assertTrue(original is SuccessResult)
            assertEquals(512, (original as SuccessResult).image.width)
            assertEquals(1, requests)
            val missing = second.execute(ImageRequest.Builder(context).data("https://example.com/missing.png").build())
            assertTrue(missing is ErrorResult)
            assertEquals(2, requests)
            assertEquals(1, directory.listFiles()!!.size)
        } finally {
            first.shutdown()
            second?.shutdown()
            directory.deleteRecursively()
        }
    }

    @Test
    fun invalidNetworkBytesAreNotRetainedAndRetryRecovers() = runBlocking {
        val directory = File(context.filesDir, "test-image-invalid-${System.nanoTime()}")
        var requests = 0
        val api = object : ImageApi {
            override suspend fun download(url: String): Response<okhttp3.ResponseBody> =
                Response.success((if (++requests == 1) "not an image".toByteArray() else png()).toResponseBody())
        }
        val loader = ImageLoader.Builder(context).components {
            add(StoredImageFetcher.Factory(ImageFileStore(directory, api, BitmapImageFileValidator(), Dispatchers.IO)))
        }.build()
        try {
            val request = ImageRequest.Builder(context).data("https://example.com/a.png").size(80).build()
            assertTrue(loader.execute(request) is ErrorResult)
            assertTrue(directory.listFiles().orEmpty().isEmpty())
            assertTrue(loader.execute(request) is SuccessResult)
            assertEquals(1, directory.listFiles()!!.size)
        } finally { loader.shutdown(); directory.deleteRecursively() }
    }

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(512, 256, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        return try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() } }
        finally { bitmap.recycle() }
    }
}
