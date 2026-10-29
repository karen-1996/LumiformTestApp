package com.lumiformtest.form.presentation.viewmodel

import com.lumiformtest.base.viewmodel.BaseCoroutineViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class FormViewModel @Inject constructor() : BaseCoroutineViewModel<
    FormAction, FormUiState, FormNavigationDestination, FormEvent
>(FormUiState()) {

    override fun onAction(action: FormAction, prevState: FormUiState) {
        when (action) {
            is FormAction.OpenImage -> navigateTo(FormNavigationDestination.OpenImage(action.imageId))
        }
    }
}
