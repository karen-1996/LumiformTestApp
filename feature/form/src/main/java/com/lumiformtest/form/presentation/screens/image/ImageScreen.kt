package com.lumiformtest.form.presentation.screens.image

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.lumiformtest.design.theme.Dimensions
import com.lumiformtest.form.presentation.components.FormImage
import com.lumiformtest.form.presentation.screens.image.model.ImageUiModel
import com.lumiformtest.form.presentation.navigation.sharedImageModifier
import com.lumiformtest.form.presentation.screens.form.preview.FormPreviewTheme
import com.lumiformtest.form.presentation.screens.image.viewmodel.ImageUiState
import com.lumiformtest.form.presentation.navigation.imageTransitionViewport
import com.lumiformtest.resources.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageScreen(state: ImageUiState, onBack: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.image_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            Box(Modifier.fillMaxSize().imageTransitionViewport()) {
                Column(Modifier.fillMaxSize().padding(Dimensions.screenPadding)) {
                    state.image?.let {
                        // Keep a long title readable without leaving no room for the image.
                        Text(it.title, style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.heightIn(max = Dimensions.imageTitleMaxHeight).verticalScroll(rememberScrollState()).padding(bottom = Dimensions.spacingMedium))
                    }
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            state.hasReadFailure -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(stringResource(R.string.image_read_error))
                                TextButton(onClick = onRetry, enabled = !state.isLoading) { Text(stringResource(R.string.form_retry)) }
                            }
                            state.image != null -> FormImage(
                                state.image.url, state.image.title, Modifier.sharedImageModifier(state.image.id).fillMaxSize()
                            )
                            state.isLoading -> CircularProgressIndicator()
                            else -> Text(stringResource(R.string.image_unavailable))
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Image missing", showBackground = true)
@Composable
private fun ImageScreenMissingPreview() {
    FormPreviewTheme() { ImageScreen(ImageUiState(isLoading = false), {}, {}) }
}

@Preview(name = "Saved image read failed", showBackground = true)
@Composable
private fun ImageScreenReadFailurePreview() {
    FormPreviewTheme() { ImageScreen(ImageUiState(isLoading = false, hasReadFailure = true), {}, {}) }
}

@Preview(name = "Image content", showBackground = true)
@Composable
private fun ImageScreenContentPreview() {
    FormPreviewTheme() {
        ImageScreen(ImageUiState(ImageUiModel(4, "Welcome image", "preview://image"), isLoading = false), {}, {})
    }
}

@Preview(name = "Image loading", showBackground = true)
@Composable
private fun ImageScreenLoadingPreview() {
    FormPreviewTheme() { ImageScreen(ImageUiState(), {}, {}) }
}

@Preview(name = "Long title · dark", showBackground = true, widthDp = 320, fontScale = 1.5f)
@Composable
private fun ImageScreenLongTitlePreview() {
    FormPreviewTheme(darkTheme = true) {
        ImageScreen(ImageUiState(ImageUiModel(4, "An inspection image with a longer title that should stay readable", "preview://image"), isLoading = false), {}, {})
    }
}
