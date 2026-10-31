package com.lumiformtest.form.presentation.screens.image

import kotlinx.collections.immutable.toPersistentList
import com.lumiformtest.form.fixtures.toImageObservation
import kotlinx.coroutines.flow.map
import android.graphics.Bitmap
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lumiformtest.form.presentation.screens.form.FormScreen
import com.lumiformtest.design.theme.LumiformTestAppTheme
import com.lumiformtest.design.theme.MotionDurations.IMAGE_TRANSITION_MILLIS
import com.lumiformtest.form.FormScreenTestActivity
import com.lumiformtest.form.presentation.screens.image.model.ImageUiModel
import com.lumiformtest.form.presentation.screens.form.model.FormRowUiModel
import com.lumiformtest.form.presentation.navigation.FormDestination
import com.lumiformtest.form.presentation.navigation.ProvideImageTransition
import com.lumiformtest.form.presentation.navigation.rememberFormImageTransitionState
import com.lumiformtest.form.presentation.components.LocalFormImageLoader
import com.lumiformtest.form.presentation.screens.image.viewmodel.ImageViewModel
import com.lumiformtest.form.domain.repository.FormRepository
import com.lumiformtest.form.domain.model.FormObservation
import com.lumiformtest.form.domain.model.RefreshFormResult
import com.lumiformtest.form.domain.model.ChoiceSelectionResult
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.usecase.ObserveImageUseCase
import com.lumiformtest.form.data.local.ImageFileStore
import com.lumiformtest.form.data.local.BitmapImageFileValidator
import com.lumiformtest.form.data.remote.ImageApi
import com.lumiformtest.form.data.remote.StoredImageFetcher
import coil3.ImageLoader
import coil3.asImage
import coil3.memory.MemoryCache
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import retrofit2.Response
import okhttp3.ResponseBody
import com.lumiformtest.form.presentation.screens.form.preview.FormPreviewData
import com.lumiformtest.form.presentation.screens.form.viewmodel.FormAction
import com.lumiformtest.form.presentation.screens.form.viewmodel.FormUiState
import com.lumiformtest.form.presentation.screens.image.viewmodel.ImageUiState
import com.lumiformtest.resources.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageTransitionTest {
    @get:Rule val compose = createAndroidComposeRule<FormScreenTestActivity>()

    @OptIn(ExperimentalSharedTransitionApi::class)
    @Test
    fun compactImageExpandsContinuouslyAndBackReturnsToItsOriginalBounds() {
        val image = FormPreviewData.image
        compose.setContent {
            // A local drawable isolates animation/layout checks from network and decoder timing.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                LumiformTestAppTheme(darkTheme = true, dynamicColor = false) {
                    val stack = rememberNavBackStack(FormDestination.Form)
                    val imageTransitionState = rememberFormImageTransitionState()
                    SharedTransitionLayout {
                        NavDisplay(
                            backStack = stack,
                            transitionSpec = { fadeIn(tween(IMAGE_TRANSITION_MILLIS)) togetherWith fadeOut(tween(IMAGE_TRANSITION_MILLIS)) },
                            popTransitionSpec = { fadeIn(tween(IMAGE_TRANSITION_MILLIS)) togetherWith fadeOut(tween(IMAGE_TRANSITION_MILLIS)) },
                            entryProvider = entryProvider {
                                entry<FormDestination.Form> {
                                    ProvideImageTransition(this@SharedTransitionLayout, imageTransitionState) {
                                        FormScreen(FormUiState(content = FormPreviewData.content, isReadingCache = false), {
                                            if (it is FormAction.OpenImage) stack.add(FormDestination.Image(it.imageId))
                                        })
                                    }
                                }
                                entry<FormDestination.Image> {
                                    ProvideImageTransition(this@SharedTransitionLayout, imageTransitionState) {
                                        ImageScreen(
                                            ImageUiState(ImageUiModel(image.id, image.title, image.imageUrl), isLoading = false, imageId = image.id),
                                            { stack.removeLastOrNull() }, {}
                                        )
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        val thumbnail = compose.onNodeWithTag("image_thumbnail_${image.id}")
        val source = thumbnail.fetchSemanticsNode().boundsInRoot
        val density = compose.activity.resources.displayMetrics.density
        assertEquals(120f, source.width / density, 1f)
        assertEquals(source.width, source.height, 1f)
        val initialCorner = cornerInset(source)

        compose.mainClock.autoAdvance = false
        thumbnail.performClick()
        compose.mainClock.advanceTimeBy(160)
        val middle = viewerBounds(image.title)
        val middleCorner = cornerInset(middle)
        compose.mainClock.advanceTimeBy(500)
        val expanded = viewerBounds(image.title)
        val expandedCorner = cornerInset(expanded)
        assertTrue("Corners should flatten continuously: $initialCorner -> $middleCorner -> $expandedCorner",
            initialCorner > middleCorner && middleCorner > expandedCorner)
        assertTrue("The image should grow through intermediate widths", middle.width > source.width && middle.width < expanded.width)
        assertTrue(expanded.height > source.height * 2)
        compose.onNodeWithContentDescription(image.title).assertIsDisplayed()

        compose.onNodeWithContentDescription(compose.activity.getString(R.string.back)).performClick()
        compose.mainClock.advanceTimeBy(160)
        // The image draws in the shared overlay outside the small Surface during Back.
        val shrinking = thumbnail.getUnclippedBoundsInRoot()
        val shrinkingWidth = (shrinking.right - shrinking.left).value * density
        assertTrue("Back should shrink the image toward its thumbnail: $shrinkingWidth between ${source.width} and ${expanded.width}",
            shrinkingWidth > source.width && shrinkingWidth < expanded.width)
        val reverseBounds = Rect(shrinking.left.value * density, shrinking.top.value * density,
            shrinking.right.value * density, shrinking.bottom.value * density)
        val reverseCorner = cornerInset(reverseBounds)
        assertTrue("Corners should round during Back: $expandedCorner -> $reverseCorner -> $initialCorner",
            reverseCorner > expandedCorner && reverseCorner < initialCorner)
        compose.mainClock.advanceTimeBy(500)
        val returned = thumbnail.fetchSemanticsNode().boundsInRoot
        assertEquals(source.left, returned.left, 1f)
        assertEquals(source.top, returned.top, 1f)
        assertEquals(source.width, returned.width, 1f)
    }

    private fun viewerBounds(title: String): Rect =
        compose.onNodeWithContentDescription(title).fetchSemanticsNode().boundsInRoot

    private fun cornerInset(bounds: Rect): Int {
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val left = kotlin.math.ceil(bounds.left).toInt()
        val top = kotlin.math.ceil(bounds.top).toInt() + 2
        return (0..60).first { pixels[left + it, top].luminance() > 0.9f }
    }

    @Test
    fun partiallyHiddenThumbnailStaysBelowAppBarDuringOpenAndBack() {
        val image = FormPreviewData.image.copy(imageUrl = "https://example.com/clipped.png")
        val loader = ImageLoader.Builder(compose.activity).build()
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.MAGENTA) }
        val cached = MemoryCache.Value(bitmap.asImage())
        loader.memoryCache!![MemoryCache.Key("thumbnail:${image.imageUrl}")] = cached
        loader.memoryCache!![MemoryCache.Key("viewer:${image.imageUrl}")] = cached
        val rows = List(20) { FormRowUiModel.Text(1000L + it, 1, 1, "Before $it") } + image +
            List(20) { FormRowUiModel.Text(2000L + it, 1, 1, "After $it") }
        try {
            compose.setContent {
                val preparedLoader = rememberUpdatedState(loader)
                CompositionLocalProvider(LocalFormImageLoader provides preparedLoader) {
                    LumiformTestAppTheme(darkTheme = true, dynamicColor = false) {
                        val stack = rememberNavBackStack(FormDestination.Form)
                        val transitionState = rememberFormImageTransitionState()
                        SharedTransitionLayout {
                            NavDisplay(
                                backStack = stack,
                                transitionSpec = { fadeIn(tween(IMAGE_TRANSITION_MILLIS)) togetherWith fadeOut(tween(IMAGE_TRANSITION_MILLIS)) },
                                popTransitionSpec = { fadeIn(tween(IMAGE_TRANSITION_MILLIS)) togetherWith fadeOut(tween(IMAGE_TRANSITION_MILLIS)) },
                                entryProvider = entryProvider {
                                    entry<FormDestination.Form> {
                                        ProvideImageTransition(this@SharedTransitionLayout, transitionState) {
                                            FormScreen(FormUiState(content = FormPreviewData.content.copy(rows = rows.toPersistentList()), isReadingCache = false), {
                                                if (it is FormAction.OpenImage) {
                                                    transitionState.prepare(image, loader)
                                                    stack.add(FormDestination.Image(image.id))
                                                }
                                            })
                                        }
                                    }
                                    entry<FormDestination.Image> {
                                        ProvideImageTransition(this@SharedTransitionLayout, transitionState) {
                                            ImageScreen(ImageUiState(ImageUiModel(image.id, image.title, image.imageUrl), isLoading = false, imageId = image.id),
                                                { stack.removeLastOrNull() }, {})
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            val list = compose.onNodeWithTag("form_list")
            val density = compose.activity.resources.displayMetrics.density
            list.performScrollToKey(image.id)
            list.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 40f * density) }
            val top = list.fetchSemanticsNode().boundsInRoot.top
            val thumbnail = compose.onNodeWithTag("image_thumbnail_${image.id}")
            val originalTop = thumbnail.getUnclippedBoundsInRoot().top.value * density
            assertTrue("The test must start with part of the thumbnail hidden under the bar", originalTop < top - 5)
            thumbnail.assertIsDisplayed()
            compose.mainClock.autoAdvance = false
            thumbnail.performClick()
            for (time in listOf(32L, 32L, 64L, 96L, 96L, 64L)) {
                compose.mainClock.advanceTimeBy(time)
                assertImageBelowBar(top)
            }
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.back)).performClick()
            for (time in listOf(32L, 32L, 64L, 96L, 96L, 64L)) {
                compose.mainClock.advanceTimeBy(time)
                assertImageBelowBar(top)
            }
            compose.mainClock.advanceTimeBy(500)
            assertEquals(originalTop, thumbnail.getUnclippedBoundsInRoot().top.value * density, 1f)
        } finally { loader.shutdown() }
    }

    private fun assertImageBelowBar(top: Float) {
        compose.waitForIdle()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        var visiblePixels = 0
        for (y in 0 until pixels.height step 8) for (x in 0 until pixels.width step 8) {
            val color = pixels[x, y]
            if (color.red > 0.9f && color.blue > 0.9f && color.green < 0.1f) {
                assertTrue("The moving image must not draw over the app bar at ($x, $y), boundary=$top", y >= top - 1)
                visiblePixels++
            }
        }
        assertTrue("The moving image should remain visible below the bar", visiblePixels > 0)
    }

    @Test
    fun loadedThumbnailStaysVisibleWhileMetadataAndOriginalAreDelayed() {
        val image = FormPreviewData.image.copy(imageUrl = "https://example.com/transition.png")
        val observations = MutableSharedFlow<FormObservation>(replay = 1)
        val repository = object : FormRepository {
            override fun observeForm() = observations
            override fun observeImage(imageId: Long) = observations.map { it.toImageObservation(imageId) }
            override suspend fun refreshForm(): RefreshFormResult = error("Not used")
            override suspend fun toggleChoiceOption(option: ChoiceOption): ChoiceSelectionResult = error("Not used")
        }
        val viewModel = ImageViewModel(ObserveImageUseCase(repository), image.id)
        compose.activityRule.scenario.onActivity { it.viewModelStore.put("image-transition", viewModel) }
        val requests = AtomicInteger()
        val pendingOriginal = CompletableDeferred<Response<ResponseBody>>()
        val directory = File(compose.activity.filesDir, "test-image-transition-${System.nanoTime()}")
        val api = object : ImageApi {
            override suspend fun download(url: String): Response<ResponseBody> {
                requests.incrementAndGet()
                return pendingOriginal.await()
            }
        }
        val loader = ImageLoader.Builder(compose.activity).components {
            add(StoredImageFetcher.Factory(ImageFileStore(directory, api, BitmapImageFileValidator(), Dispatchers.IO)))
        }.build()
        val thumbnail = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.MAGENTA) }
        loader.memoryCache!![MemoryCache.Key("thumbnail:${image.imageUrl}")] = MemoryCache.Value(thumbnail.asImage())
        try {
            compose.setContent {
                val loaderState = rememberUpdatedState(loader)
                CompositionLocalProvider(LocalFormImageLoader provides loaderState) {
                    LumiformTestAppTheme(darkTheme = true, dynamicColor = false) {
                        val stack = rememberNavBackStack(FormDestination.Form)
                        val transitionState = rememberFormImageTransitionState()
                        SharedTransitionLayout {
                            NavDisplay(
                                backStack = stack,
                                transitionSpec = { fadeIn(tween(IMAGE_TRANSITION_MILLIS)) togetherWith fadeOut(tween(IMAGE_TRANSITION_MILLIS)) },
                                entryProvider = entryProvider {
                                    entry<FormDestination.Form> {
                                        ProvideImageTransition(this@SharedTransitionLayout, transitionState) {
                                            val content = FormPreviewData.content.copy(rows = listOf(image).toPersistentList())
                                            FormScreen(FormUiState(content = content, isReadingCache = false), {
                                                if (it is FormAction.OpenImage) {
                                                    transitionState.prepare(image, loader)
                                                    stack.add(FormDestination.Image(image.id))
                                                }
                                            })
                                        }
                                    }
                                    entry<FormDestination.Image> {
                                        ProvideImageTransition(this@SharedTransitionLayout, transitionState) {
                                            ImageRoute(image.id, { stack.removeLastOrNull() }, transitionState, viewModel)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            compose.waitUntil(5_000) { loader.memoryCache?.get(MemoryCache.Key("thumbnail:${image.imageUrl}")) != null }
            compose.waitForIdle()
            val sourceBounds = compose.onNodeWithTag("image_thumbnail_${image.id}").fetchSemanticsNode().boundsInRoot
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("image_thumbnail_${image.id}").performClick()
            var elapsed = 0L
            var middleWidth = 0f
            for (frame in listOf(32L, 64L, 96L, 96L, 16L, 16L, 16L, 16L, 16L, 16L, 96L)) {
                compose.mainClock.advanceTimeBy(frame)
                compose.waitForIdle()
                elapsed += frame
                compose.onAllNodesWithTag("image_loading").assertCountEquals(0)
                val bounds = viewerBounds(image.title)
                if (elapsed == 192L) middleWidth = bounds.width
                val capture = compose.onRoot().captureToImage()
                val pixels = capture.toPixelMap()
                val center = pixels[bounds.center.x.toInt(), bounds.center.y.toInt()]
                assertTrue("The image should stay opaque at $elapsed ms, bounds=$bounds, center=$center", center.red > 0.9f && center.blue > 0.9f && center.green < 0.1f)
                if (elapsed < 288) assertEquals("Original decoding should wait until motion finishes", 0, requests.get())
            }
            compose.mainClock.advanceTimeBy(500)
            compose.waitUntil(5_000) { requests.get() == 1 }
            assertTrue("The real Coil path should also scale continuously: ${sourceBounds.width} -> $middleWidth -> ${viewerBounds(image.title).width}",
                middleWidth > sourceBounds.width && middleWidth < viewerBounds(image.title).width)
            compose.onAllNodesWithTag("image_loading").assertCountEquals(0)
            compose.onNodeWithContentDescription(image.title).assertIsDisplayed()
            compose.runOnIdle { observations.tryEmit(FormObservation.ReadFailure) }
            compose.mainClock.autoAdvance = true
            compose.onNodeWithText(compose.activity.getString(R.string.image_read_error)).assertIsDisplayed()
        } finally {
            pendingOriginal.cancel()
            loader.shutdown()
            directory.deleteRecursively()
        }
    }
}
