package com.lumiformtest.form.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lumiformtest.form.presentation.components.FormRow
import com.lumiformtest.form.presentation.components.FormStatus
import com.lumiformtest.form.presentation.components.formFailureMessage
import com.lumiformtest.form.presentation.viewmodel.FormAction
import com.lumiformtest.form.presentation.viewmodel.FormUiState
import com.lumiformtest.form.presentation.model.ChoiceOptionUiInput
import com.lumiformtest.form.presentation.model.ChoiceWriteFailureReasonUi
import com.lumiformtest.form.presentation.navigation.imageTransitionViewport
import com.lumiformtest.resources.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormScreen(
    state: FormUiState,
    onAction: (FormAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val choicesEnabled = !state.hasReadFailure
    val openImage: (Long) -> Unit = remember(onAction) { { onAction(FormAction.OpenImage(it)) } }
    val selectOption: (ChoiceOptionUiInput) -> Unit = remember(onAction) { { onAction(FormAction.OptionClicked(it)) } }
    val hasFailure = state.hasReadFailure || state.refreshFailure != null
    val snackbarHost = remember { SnackbarHostState() }
    val latestAction by rememberUpdatedState(onAction)
    val choiceFailure = state.choiceWriteFailure
    val lifecycleOwner = LocalLifecycleOwner.current
    val choiceMessage = choiceFailure?.let {
        stringResource(when (it.reason) {
            ChoiceWriteFailureReasonUi.STORAGE -> R.string.choice_save_error
            ChoiceWriteFailureReasonUi.UNAVAILABLE -> R.string.choice_unavailable
            ChoiceWriteFailureReasonUi.READ_UNAVAILABLE -> R.string.choice_read_unavailable
        })
    }
    LaunchedEffect(choiceFailure?.id, choiceMessage, lifecycleOwner) {
        if (choiceFailure != null && choiceMessage != null) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                snackbarHost.showSnackbar(choiceMessage, withDismissAction = true, duration = SnackbarDuration.Long)
                latestAction(FormAction.DismissChoiceFailure(choiceFailure.id))
            }
        }
    }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.form_title)) },
                actions = {
                    TextButton(
                        onClick = { onAction(FormAction.Refresh) },
                        enabled = !state.isRefreshing && !state.isAwaitingSnapshot &&
                            !(state.hasReadFailure && state.isReadingCache)
                    ) {
                        Text(stringResource(if (hasFailure) R.string.form_retry else R.string.form_refresh))
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().imageTransitionViewport().testTag("form_list"),
                // Choice rows own their side padding so chips can scroll to the screen edges.
                contentPadding = PaddingValues(vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "status", contentType = "status") {
                    Box(Modifier.padding(horizontal = 20.dp)) { FormStatus(state) }
                }
                when {
                    state.isLoading -> item(key = "loading", contentType = "loading") {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.testTag("form_loading"))
                        }
                    }
                    state.content != null -> {
                        if (state.content.rows.isEmpty()) {
                            item(key = "empty", contentType = "empty") {
                                Text(stringResource(R.string.form_empty), modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                        // Saved node IDs are unique, so refresh can keep each row's scroll state.
                        items(state.content.rows, key = { it.id }, contentType = { it::class }) { row ->
                            FormRow(row, choicesEnabled, onImageClick = openImage, onChoiceClick = selectOption, horizontalPadding = 20.dp)
                        }
                    }
                    else -> item(key = "error", contentType = "error") {
                        Text(formFailureMessage(state), modifier = Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
