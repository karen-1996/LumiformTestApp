package com.lumiformtest.app.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.lumiformtest.form.presentation.navigation.FormDestination
import com.lumiformtest.form.presentation.navigation.formEntries
import com.lumiformtest.form.presentation.navigation.rememberFormImageTransitionState

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AppNavigation(
    backStack: NavBackStack<NavKey> = rememberNavBackStack(FormDestination.Form)
) {
    val imageTransitionState = rememberFormImageTransitionState()
    val onBack: () -> Unit = {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    SharedTransitionLayout {
        NavDisplay(
            backStack = backStack,
            onBack = onBack,
            transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
            popTransitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
            predictivePopTransitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(350)) },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator()
            ),
            entryProvider = entryProvider {
                formEntries(
                    onNavigate = { destination ->
                        if (backStack.lastOrNull() != destination) backStack.add(destination)
                    },
                    onBack = onBack,
                    sharedTransitionScope = this@SharedTransitionLayout,
                    imageTransitionState = imageTransitionState
                )
            }
        )
    }
}
