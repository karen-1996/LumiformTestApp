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
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import com.lumiformtest.form.presentation.preview.FormPreviewTheme
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
    val loader = rememberFormImageLoader(imageLoader)
    val request = remember(context, url, isThumbnail) {
        ImageRequest.Builder(context).data(url)
            // A viewer bitmap should never become the list's thumbnail from memory cache.
            .memoryCacheKey("${if (isThumbnail) "thumbnail" else "viewer"}:$url")
            .apply {
                if (isThumbnail) size(512).scale(Scale.FIT).precision(Precision.INEXACT)
            }
            .build()
    }
    var attempt by rememberSaveable(url) { mutableIntStateOf(0) }
    key(url, attempt) {
        var status by remember { mutableStateOf(ImageLoadStatus.LOADING) }
        Box(modifier, contentAlignment = Alignment.Center) {
            if (loader != null) AsyncImage(
                model = request,
                imageLoader = loader,
                contentDescription = contentDescription,
                contentScale = contentScale,
                // Transparent black artwork should also stay readable in dark mode.
                modifier = Modifier.fillMaxSize().then(
                    if (status == ImageLoadStatus.READY) Modifier.background(colorResource(R.color.image_canvas)) else Modifier
                ).testTag("image_content"),
                onLoading = { status = ImageLoadStatus.LOADING },
                onSuccess = { status = ImageLoadStatus.READY },
                onError = { status = ImageLoadStatus.FAILED }
            )
            ImageFeedback(status, onRetry = { attempt++ })
        }
    }
}

@Composable
private fun ImageFeedback(status: ImageLoadStatus, onRetry: () -> Unit) {
    when (status) {
        ImageLoadStatus.LOADING -> CircularProgressIndicator(Modifier.testTag("image_loading"))
        ImageLoadStatus.FAILED -> Column(
            Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.image_load_error), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRetry) { Text(stringResource(R.string.form_retry)) }
        }
        ImageLoadStatus.READY -> Unit
    }
}

private enum class ImageLoadStatus { LOADING, READY, FAILED }

@Preview(name = "Thumbnail", showBackground = true)
@Composable
private fun ThumbnailPreview() {
    FormPreviewTheme() {
        Surface(shape = MaterialTheme.shapes.medium) {
            FormImage("preview://image", null, Modifier.size(320.dp, 180.dp), contentScale = ContentScale.Crop, isThumbnail = true)
        }
    }
}

@Preview(name = "Viewer image", showBackground = true)
@Composable
private fun ViewerImagePreview() {
    FormPreviewTheme() {
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
            Box(Modifier.size(320.dp, 180.dp), contentAlignment = Alignment.Center) { ImageFeedback(status, {}) }
        }
    }
}
