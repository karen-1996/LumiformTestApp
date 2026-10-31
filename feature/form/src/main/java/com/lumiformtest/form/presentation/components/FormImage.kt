package com.lumiformtest.form.presentation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.asPainter
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import com.lumiformtest.design.theme.Dimensions
import com.lumiformtest.form.presentation.screens.form.preview.FormPreviewTheme
import com.lumiformtest.form.presentation.navigation.imageTransitionPreview
import com.lumiformtest.form.presentation.navigation.movingImagePreview
import com.lumiformtest.resources.R

@Composable
internal fun FormImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    imageLoader: ImageLoader? = null,
    isThumbnail: Boolean = false
) {
    if (LocalInspectionMode.current) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = modifier.background(colorResource(R.color.image_canvas))
        )
        return
    }
    val context = LocalContext.current
    val canvas = colorResource(R.color.image_canvas)
    val thumbnail = imageTransitionPreview(url)?.thumbnail
    val placeholder = remember(context, thumbnail) { thumbnail?.asPainter(context) }
    val movingImage = movingImagePreview(url, isThumbnail)
    val movingPainter = remember(context, movingImage) { movingImage?.asPainter(context) }
    val loader = if (movingPainter == null) rememberFormImageLoader(imageLoader) else null
    val request = remember(context, url, isThumbnail) {
        ImageRequest.Builder(context).data(url)
            // A viewer bitmap should never become the list's thumbnail from memory cache.
            .memoryCacheKey("${if (isThumbnail) "thumbnail" else "viewer"}:$url")
            .apply {
                // AsyncImage resolves the measured bounds in pixels, including device density.
                if (isThumbnail) scale(Scale.FIT).precision(Precision.INEXACT)
                // Keep the thumbnail visible until the original is decoded.
                else placeholderMemoryCacheKey("thumbnail:$url")
            }
            .build()
    }
    var attempt by rememberSaveable(url) { mutableIntStateOf(0) }
    key(url, attempt) {
        var status by remember(movingPainter != null) {
            mutableStateOf(if (placeholder != null) ImageLoadStatus.PLACEHOLDER else ImageLoadStatus.LOADING)
        }
        val background = if (status == ImageLoadStatus.FAILED) MaterialTheme.colorScheme.surfaceContainer else canvas
        Box(modifier.background(background), contentAlignment = Alignment.Center) {
            if (movingPainter != null) {
                // Keep one loaded image and one layout node throughout the movement.
                Image(movingPainter, contentDescription, Modifier.fillMaxSize().testTag("image_content"), contentScale = contentScale)
            } else {
                // Cover even Coil's first empty frame when handing over to the original.
                if (placeholder != null && status != ImageLoadStatus.READY && status != ImageLoadStatus.FAILED) {
                    Image(placeholder, null, Modifier.fillMaxSize(), contentScale = contentScale)
                }
                if (loader != null) AsyncImage(
                    model = request,
                    imageLoader = loader,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    placeholder = placeholder,
                    modifier = Modifier.fillMaxSize().testTag("image_content"),
                    onLoading = { status = if (it.painter != null) ImageLoadStatus.PLACEHOLDER else ImageLoadStatus.LOADING },
                    onSuccess = { status = ImageLoadStatus.READY },
                    onError = { status = ImageLoadStatus.FAILED }
                )
                ImageFeedback(status, onRetry = { attempt++ })
            }
        }
    }
}

@Composable
private fun ImageFeedback(status: ImageLoadStatus, onRetry: () -> Unit) {
    when (status) {
        ImageLoadStatus.LOADING -> CircularProgressIndicator(Modifier.testTag("image_loading"))
        ImageLoadStatus.FAILED -> Column(
            Modifier.padding(Dimensions.spacingMedium), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.image_load_error), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.form_retry)) }
        }
        ImageLoadStatus.READY, ImageLoadStatus.PLACEHOLDER -> Unit
    }
}

private enum class ImageLoadStatus { LOADING, PLACEHOLDER, READY, FAILED }

@Preview(name = "Thumbnail", showBackground = true)
@Composable
private fun ThumbnailPreview() {
    FormPreviewTheme {
        Surface(shape = MaterialTheme.shapes.medium) {
            FormImage("preview://image", null, Modifier.size(Dimensions.imageThumbnailSize), isThumbnail = true)
        }
    }
}

@Preview(name = "Viewer image", showBackground = true)
@Composable
private fun ViewerImagePreview() {
    FormPreviewTheme {
        FormImage("preview://image", "Welcome image", Modifier.size(320.dp, 420.dp))
    }
}

@Preview(name = "Image loading", showBackground = true)
@Composable
private fun LoadingImagePreview() = PreviewImageFeedback(ImageLoadStatus.LOADING)

@Preview(name = "Image failed · dark", showBackground = true)
@Composable
private fun FailedImagePreview() = PreviewImageFeedback(ImageLoadStatus.FAILED, darkTheme = true)

@Composable
private fun PreviewImageFeedback(status: ImageLoadStatus, darkTheme: Boolean = false) {
    FormPreviewTheme(darkTheme = darkTheme) {
        Surface {
            Box(Modifier.size(320.dp, 180.dp), contentAlignment = Alignment.Center) { ImageFeedback(status) {} }
        }
    }
}
