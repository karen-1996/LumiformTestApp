package com.lumiformtest.app.navigation

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

@Composable
internal fun AppNavigation(
    backStack: NavBackStack<NavKey> = rememberNavBackStack(FormDestination.Form)
) {
    val onBack: () -> Unit = {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    NavDisplay(
        backStack = backStack,
        onBack = onBack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            formEntries(
                onNavigate = { destination ->
                    if (backStack.lastOrNull() != destination) backStack.add(destination)
                },
                onBack = onBack
            )
        }
    )
}
