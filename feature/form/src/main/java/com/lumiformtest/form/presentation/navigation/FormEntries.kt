package com.lumiformtest.form.presentation.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.lumiformtest.form.presentation.screens.FormRoute
import com.lumiformtest.form.presentation.screens.ImageRoute

fun EntryProviderScope<NavKey>.formEntries(
    onNavigate: (FormDestination) -> Unit,
    onBack: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    imageTransitionState: FormImageTransitionState
) {
    entry<FormDestination.Form> {
        ProvideImageTransition(sharedTransitionScope, imageTransitionState) {
            FormRoute(onOpenImage = { id -> onNavigate(FormDestination.Image(id)) }, imageTransitionState = imageTransitionState)
        }
    }
    entry<FormDestination.Image> {
        ProvideImageTransition(sharedTransitionScope, imageTransitionState) {
            ImageRoute(imageId = it.imageId, onBack = onBack, imageTransitionState = imageTransitionState)
        }
    }
}
