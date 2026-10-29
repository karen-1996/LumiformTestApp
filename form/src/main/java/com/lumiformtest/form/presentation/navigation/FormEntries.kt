package com.lumiformtest.form.presentation.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.lumiformtest.form.presentation.screens.FormRoute
import com.lumiformtest.form.presentation.screens.ImageScreen

fun EntryProviderScope<NavKey>.formEntries(
    onNavigate: (FormDestination) -> Unit,
    onBack: () -> Unit
) {
    entry<FormDestination.Form> {
        FormRoute(onOpenImage = { id -> onNavigate(FormDestination.Image(id)) })
    }
    entry<FormDestination.Image> {
        ImageScreen(onBack = onBack)
    }
}
