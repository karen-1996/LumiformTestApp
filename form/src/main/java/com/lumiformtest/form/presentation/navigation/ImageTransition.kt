package com.lumiformtest.form.presentation.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import coil3.memory.MemoryCache
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.ui.LocalNavAnimatedContentScope

private class ImageTransitionScopes(
    val shared: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
    val state: FormImageTransitionState
)

private val LocalImageTransition = compositionLocalOf<ImageTransitionScopes?> { null }

@Composable
internal fun ProvideImageTransition(shared: SharedTransitionScope, state: FormImageTransitionState, content: @Composable () -> Unit) {
    val visibility = LocalNavAnimatedContentScope.current
    val scopes = remember(shared, visibility, state) { ImageTransitionScopes(shared, visibility, state) }
    CompositionLocalProvider(LocalImageTransition provides scopes, content = content)
}

@Composable
internal fun Modifier.sharedImageModifier(imageId: Long, isThumbnail: Boolean = false): Modifier {
    // Standalone screens and previews also work without a navigation transition scope.
    val thumbnailShape = MaterialTheme.shapes.medium
    val scopes = LocalImageTransition.current ?: return clip(if (isThumbnail) thumbnailShape else RectangleShape)
    val corners = scopes.visibility.transition.animateFloat(transitionSpec = { tween(350) }, label = "image corners") {
        if ((it == EnterExitState.Visible) == isThumbnail) 1f else 0f
    }
    val shape = remember(thumbnailShape, corners) {
        object : Shape {
            override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                val fraction = corners.value
                fun scaled(corner: CornerSize) = CornerSize(corner.toPx(size, density) * fraction)
                return thumbnailShape.copy(
                    topStart = scaled(thumbnailShape.topStart), topEnd = scaled(thumbnailShape.topEnd),
                    bottomStart = scaled(thumbnailShape.bottomStart), bottomEnd = scaled(thumbnailShape.bottomEnd)
                ).createOutline(size, layoutDirection, density)
            }
        }
    }
    return with(scopes.shared) {
        this@sharedImageModifier.sharedElement(
            sharedContentState = rememberSharedContentState(key = "form-image-$imageId"),
            animatedVisibilityScope = scopes.visibility,
            boundsTransform = { _, _ -> tween(350) }
        ).clip(shape)
    }
}

@Composable
internal fun imageTransitionPreview(url: String): ImageTransitionPreview? =
    LocalImageTransition.current?.state?.preview?.takeIf { it.image.url == url }

@Composable
internal fun isImageMoving(): Boolean {
    val scopes = LocalImageTransition.current ?: return false
    val transition = scopes.visibility.transition
    return scopes.shared.isTransitionActive || transition.currentState != transition.targetState
}

@Composable
internal fun movingImagePreview(url: String, isThumbnail: Boolean): coil3.Image? {
    val scopes = LocalImageTransition.current ?: return null
    val preview = imageTransitionPreview(url) ?: return null
    if (!isImageMoving()) return null
    val opening = (scopes.visibility.transition.targetState == EnterExitState.Visible) != isThumbnail
    return remember(preview, opening) {
        if (opening) preview.thumbnail
        else preview.loader?.memoryCache?.get(MemoryCache.Key("viewer:$url"))?.image ?: preview.thumbnail
    }
}
