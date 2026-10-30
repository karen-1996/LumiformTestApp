package com.lumiformtest.form.fixtures

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.EventListener
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.lumiformtest.design.theme.LumiformTestAppTheme
import com.lumiformtest.form.FormScreenTestActivity
import com.lumiformtest.form.data.local.BitmapImageFileValidator
import com.lumiformtest.form.data.local.ImageFileStore
import com.lumiformtest.form.data.mapper.FormDataMapper
import com.lumiformtest.form.data.remote.FormJsonParser
import com.lumiformtest.form.data.remote.ImageApi
import com.lumiformtest.form.data.remote.StoredImageFetcher
import com.lumiformtest.form.domain.model.FormSnapshot
import com.lumiformtest.form.presentation.components.LocalFormImageLoader
import com.lumiformtest.form.presentation.mapper.FormUiMapper
import com.lumiformtest.form.presentation.model.FormRowUiModel
import com.lumiformtest.form.presentation.model.ImageUiModel
import com.lumiformtest.form.presentation.screens.FormScreen
import com.lumiformtest.form.presentation.screens.ImageScreen
import com.lumiformtest.form.presentation.viewmodel.FormAction
import com.lumiformtest.form.presentation.viewmodel.FormUiState
import com.lumiformtest.form.presentation.viewmodel.ImageUiState
import com.lumiformtest.resources.R
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class FormFixtureImageTest {
    @get:Rule val compose = createAndroidComposeRule<FormScreenTestActivity>()

    @Test
    fun fixtureImagesDecodeRetryAndReuseOriginalsWithoutExternalNetwork() {
        val context = compose.activity
        val body = InstrumentationRegistry.getInstrumentation().context.assets
            .open("${FormFixture.ROOT}/valid/image-variants.json").bufferedReader().use { it.readText() }
        val content = FormUiMapper().map(FormSnapshot(FormJsonParser(FormDataMapper()).parse(body), Instant.EPOCH))
        val images = content.rows.filterIsInstance<FormRowUiModel.Image>()
        val directory = File(context.filesDir, "fixture-images-${System.nanoTime()}")
        val recovered = AtomicBoolean(false)
        val requests = ConcurrentHashMap<String, AtomicInteger>()
        val successes = CopyOnWriteArrayList<Pair<String, SuccessResult>>()
        val landscape by lazy { png(2048, 512, android.graphics.Color.BLUE) }
        val portrait by lazy { png(512, 2048, android.graphics.Color.GREEN) }
        val transparent by lazy { png(256, 256, android.graphics.Color.TRANSPARENT) }
        val api = object : ImageApi {
            override suspend fun download(url: String): Response<ResponseBody> {
                requests.getOrPut(url) { AtomicInteger() }.incrementAndGet()
                return when {
                    url.endsWith("missing.png") && !recovered.get() -> Response.error(404, "Missing".toResponseBody())
                    url.endsWith("not-image.png") -> Response.success("<html>Not an image</html>".toResponseBody())
                    url.endsWith("portrait.png") -> Response.success(portrait.toResponseBody())
                    url.endsWith("transparent.png") -> Response.success(transparent.toResponseBody())
                    else -> Response.success(landscape.toResponseBody())
                }
            }
        }
        val loader = ImageLoader.Builder(context).components {
            add(StoredImageFetcher.Factory(ImageFileStore(directory, api, BitmapImageFileValidator(), Dispatchers.IO)))
        }.eventListener(object : EventListener() {
            override fun onSuccess(request: ImageRequest, result: SuccessResult) { successes += request.data.toString() to result }
        }).build()
        var viewer by mutableStateOf<FormRowUiModel.Image?>(null)
        try {
            compose.setContent {
                CompositionLocalProvider(LocalFormImageLoader provides rememberUpdatedState<ImageLoader?>(loader)) {
                    LumiformTestAppTheme {
                        val image = viewer
                        if (image != null) ImageScreen(
                            ImageUiState(ImageUiModel(image.id, image.title, image.imageUrl), isLoading = false),
                            onBack = { viewer = null }, onRetry = {}
                        ) else FormScreen(FormUiState(content = content, isReadingCache = false), { action ->
                            if (action is FormAction.OpenImage) viewer = images.single { it.id == action.imageId }
                        })
                    }
                }
            }
            for (image in images) {
                compose.onNodeWithTag("form_list").performScrollToKey(image.id)
                if (image.imageUrl.endsWith("missing.png") || image.imageUrl.endsWith("not-image.png")) {
                    compose.waitUntil(5_000) {
                        compose.onAllNodes(hasText(context.getString(R.string.image_load_error)) and
                            hasAnyAncestor(hasTestTag("form_row_${image.id}"))).fetchSemanticsNodes().isNotEmpty()
                    }
                    compose.onNode(hasText(context.getString(R.string.image_load_error)) and
                        hasAnyAncestor(hasTestTag("form_row_${image.id}"))).assertIsDisplayed()
                    if (image.imageUrl.endsWith("missing.png")) {
                        recovered.set(true)
                        compose.onNode(hasText(context.getString(R.string.form_retry)) and
                            hasAnyAncestor(hasTestTag("form_row_${image.id}"))).performClick()
                        compose.waitUntil(5_000) { successes.any { it.first == image.imageUrl } }
                    }
                } else {
                    compose.waitUntil(5_000) { successes.any { it.first == image.imageUrl } }
                }
            }
            val first = images.first()
            val thumbnailWidth = successes.first { it.first == first.imageUrl }.second.image.width
            compose.onNodeWithTag("form_list").performScrollToKey(first.id)
            compose.onNodeWithTag("image_thumbnail_${first.id}").performClick()
            compose.waitUntil(5_000) { successes.any { it.first == first.imageUrl && it.second.image.width > thumbnailWidth } }
            compose.onNodeWithContentDescription(first.title).assertIsDisplayed()
            assertEquals("The same URL should reuse one retained original", 1, requests[first.imageUrl]?.get())
            assertTrue("Broken downloads must not leave partial files", directory.listFiles().orEmpty().none { it.name.endsWith(".part") })
        } finally {
            loader.shutdown()
            directory.deleteRecursively()
        }
    }

    private fun png(width: Int, height: Int, color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(color)
            ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        } finally { bitmap.recycle() }
    }
}
