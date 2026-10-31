package com.lumiformtest.form.presentation.screens.image

import android.graphics.Bitmap
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.lumiformtest.form.presentation.screens.image.model.ImageUiModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.EventListener
import coil3.decode.DataSource
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import com.lumiformtest.design.theme.Dimensions
import com.lumiformtest.design.theme.LumiformTestAppTheme
import com.lumiformtest.form.FormScreenTestActivity
import com.lumiformtest.form.data.local.BitmapImageFileValidator
import com.lumiformtest.form.data.local.ImageFileStore
import com.lumiformtest.form.data.remote.ImageApi
import com.lumiformtest.form.data.remote.StoredImageFetcher
import com.lumiformtest.form.presentation.components.FormImage
import com.lumiformtest.form.presentation.components.rememberFormImageLoader
import com.lumiformtest.form.presentation.components.rememberImageLoaderState
import com.lumiformtest.form.presentation.components.LocalFormImageLoader
import androidx.compose.material3.Text
import com.lumiformtest.form.presentation.screens.image.viewmodel.ImageUiState
import com.lumiformtest.resources.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class ImageScreenTest {
    @get:Rule val compose = createAndroidComposeRule<FormScreenTestActivity>()

    @Test
    fun missingImageKeepsBackAvailableAndDoesNotOfferMeaninglessRetry() {
        var backs = 0
        compose.setContent { LumiformTestAppTheme { ImageScreen(ImageUiState(isLoading = false), { backs++ }, {}) } }
        compose.onNodeWithText(compose.activity.getString(R.string.image_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.form_retry)).assertDoesNotExist()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.back)).performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @Test
    fun savedMetadataReadFailureHasExplicitRetry() {
        var retries = 0
        compose.setContent { LumiformTestAppTheme {
            ImageScreen(ImageUiState(isLoading = false, hasReadFailure = true), {}, { retries++ })
        } }
        compose.onNodeWithText(compose.activity.getString(R.string.image_read_error)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.form_retry)).performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun longTitleAtLargeFontKeepsTheImageAndBackAvailableOnSmallWidth() {
        val title = "A long image title that needs several lines. ".repeat(8)
        var backs = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                LumiformTestAppTheme(darkTheme = true) {
                    Box(Modifier.width(320.dp)) {
                        ImageScreen(ImageUiState(ImageUiModel(9, title, "https://example.com/a"), isLoading = false), { backs++ }, {})
                    }
                }
            }
        }
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("image_content").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("image_content").assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.back)).performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    @OptIn(DelicateCoilApi::class)
    @Test
    fun sharedLoaderPreparesOffMainAndIsReadyWhenRowsReenter() {
        val context = compose.activity.applicationContext
        val previous = SingletonImageLoader.get(context)
        val loader = ImageLoader.Builder(context).build()
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val factoryThread = AtomicReference<Thread>()
        val observed = AtomicReference<ImageLoader?>()
        val displays = CopyOnWriteArrayList<ImageLoader?>()
        val creations = AtomicInteger()
        var show by mutableStateOf(true)
        SingletonImageLoader.setUnsafe(SingletonImageLoader.Factory {
            creations.incrementAndGet()
            factoryThread.set(Thread.currentThread())
            started.countDown()
            check(finish.await(5, TimeUnit.SECONDS))
            loader
        })
        try {
            compose.setContent {
                val shared = rememberImageLoaderState()
                CompositionLocalProvider(LocalFormImageLoader provides shared) {
                    if (show) {
                        val current = rememberFormImageLoader(null)
                        SideEffect { observed.set(current); displays += current }
                        Text(if (current == null) "Preparing image loader" else "Image loader ready")
                    }
                }
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            compose.onNodeWithText("Preparing image loader").assertIsDisplayed()
            compose.runOnIdle { assertNull(observed.get()) }
            assertFalse(factoryThread.get() === Looper.getMainLooper().thread)
            finish.countDown()
            compose.waitUntil(5_000) { observed.get() != null }
            assertSame(loader, observed.get())
            compose.runOnIdle { show = false }
            compose.waitForIdle()
            displays.clear()
            compose.runOnIdle { show = true }
            compose.waitForIdle()
            assertSame(loader, displays.first())
            assertEquals(1, creations.get())
        } finally {
            finish.countDown()
            SingletonImageLoader.setUnsafe(previous)
            loader.shutdown()
        }
    }

    @Test
    fun thumbnailDownsamplesAndDoesNotReuseTheLargerViewerBitmap() {
        val context = compose.activity
        val directory = File(context.filesDir, "test-thumbnail-sizing-${System.nanoTime()}")
        val bytes = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888).let { bitmap ->
            bitmap.eraseColor(android.graphics.Color.BLUE)
            try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() } }
            finally { bitmap.recycle() }
        }
        val results = CopyOnWriteArrayList<SuccessResult>()
        val resolvedSizes = CopyOnWriteArrayList<Size>()
        val requests = AtomicInteger()
        val validations = AtomicInteger()
        val api = object : ImageApi {
            override suspend fun download(url: String): Response<okhttp3.ResponseBody> {
                requests.incrementAndGet()
                return Response.success(bytes.toResponseBody())
            }
        }
        val validator = com.lumiformtest.form.data.local.ImageFileValidator {
            validations.incrementAndGet()
            BitmapImageFileValidator().isValid(it)
        }
        val loader = ImageLoader.Builder(context).eventListener(object : EventListener() {
            override fun resolveSizeEnd(request: ImageRequest, size: Size) { resolvedSizes += size }
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { results += result }
        }).components {
            add(StoredImageFetcher.Factory(ImageFileStore(directory, api, validator, Dispatchers.IO)))
        }.build()
        var thumbnail by mutableStateOf(false)
        var thumbnailDensity by mutableStateOf(1f)
        try {
            compose.setContent { LumiformTestAppTheme {
                val density = if (thumbnail) Density(thumbnailDensity) else LocalDensity.current
                CompositionLocalProvider(LocalDensity provides density) {
                    // The app has separate layout nodes for the list and viewer.
                    key(thumbnail, thumbnailDensity) {
                        FormImage(
                            "https://example.com/large.png", "Large image",
                            modifier = if (thumbnail) Modifier.size(Dimensions.imageThumbnailSize)
                                else with(density) { Modifier.size(1024.toDp(), 512.toDp()) },
                            contentScale = ContentScale.Fit,
                            imageLoader = loader, isThumbnail = thumbnail
                        )
                    }
                }
            } }
            compose.waitUntil(5_000) { results.size == 1 }
            val viewer = results[0]
            assertTrue(viewer.image.width > 512)
            compose.runOnIdle { thumbnail = true }
            compose.waitUntil(5_000) { results.size == 2 }
            val small = results[1]
            val thumbnailPixels = with(Density(1f)) { Dimensions.imageThumbnailSize.roundToPx() }
            assertEquals(Size(thumbnailPixels, thumbnailPixels), resolvedSizes.last())
            assertTrue(small.image.width < viewer.image.width)
            assertNotEquals(viewer.memoryCacheKey, small.memoryCacheKey)
            assertEquals(1, requests.get())
            assertEquals(1, validations.get())
            assertEquals(bytes.size.toLong(), directory.listFiles()!!.single().length())
            compose.runOnIdle { thumbnail = false }
            compose.waitUntil(5_000) { results.size == 3 }
            assertEquals(DataSource.MEMORY_CACHE, results[2].dataSource)
            assertEquals(viewer.image.width, results[2].image.width)
            compose.runOnIdle { thumbnail = true }
            compose.waitUntil(5_000) { results.size == 4 }
            assertEquals(DataSource.MEMORY_CACHE, results[3].dataSource)
            assertEquals(small.image.width, results[3].image.width)
            // Recreate the same thumbnail at denser display scales.
            for (density in listOf(2f, 3f)) {
                val previousResults = results.size
                compose.runOnIdle { thumbnailDensity = density }
                compose.waitUntil(5_000) { results.size == previousResults + 1 }
                val target = with(Density(density)) { Dimensions.imageThumbnailSize.roundToPx() }
                assertEquals(Size(target, target), resolvedSizes.last())
                assertTrue(results.last().image.width >= target)
                assertTrue(results.last().image.width < viewer.image.width)
            }
            assertEquals(1, requests.get())
            assertEquals(1, validations.get())
        } finally { loader.shutdown(); directory.deleteRecursively() }
    }

    @Test
    fun imageFailureRetryLoadsAndRemovesTheError() {
        val context = compose.activity
        val directory = File(context.filesDir, "test-image-ui-${System.nanoTime()}")
        val bytes = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).let { bitmap ->
            try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() } }
            finally { bitmap.recycle() }
        }
        val requests = AtomicInteger()
        val decoded = CopyOnWriteArrayList<SuccessResult>()
        val api = object : ImageApi {
            override suspend fun download(url: String): Response<okhttp3.ResponseBody> {
                if (requests.incrementAndGet() == 1) throw IOException("Offline")
                return Response.success(bytes.toResponseBody())
            }
        }
        val loader = ImageLoader.Builder(context).eventListener(object : EventListener() {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { decoded += result }
        }).components {
            add(StoredImageFetcher.Factory(ImageFileStore(directory, api, BitmapImageFileValidator(), Dispatchers.IO)))
        }.build()
        try {
            compose.setContent { LumiformTestAppTheme {
                Box(Modifier.size(240.dp)) {
                    FormImage("https://example.com/a.png", "Test image", Modifier.size(200.dp), imageLoader = loader, isThumbnail = true)
                }
            } }
            compose.waitUntil(5_000) { requests.get() == 1 }
            compose.onNodeWithText(context.getString(R.string.image_load_error)).assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.form_retry)).performClick()
            compose.waitUntil(5_000) { requests.get() == 2 && directory.listFiles().orEmpty().any { it.extension != "part" } }
            compose.waitUntil(5_000) { decoded.isNotEmpty() }
            assertEquals(64, decoded.single().image.width)
            assertEquals(64, decoded.single().image.height)
            compose.waitForIdle()
            compose.onNodeWithTag("image_content").assertIsDisplayed()
            compose.onNodeWithText(context.getString(R.string.image_load_error)).assertDoesNotExist()
            compose.onNodeWithText(context.getString(R.string.form_retry)).assertDoesNotExist()
        } finally { loader.shutdown(); directory.deleteRecursively() }
    }
}
