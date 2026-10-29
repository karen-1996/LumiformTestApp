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
import androidx.compose.runtime.Composable
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
import com.lumiformtest.resources.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FormScreen(
    state: FormUiState,
    onAction: (FormAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val hasFailure = state.hasReadFailure || state.refreshFailure != null
    Scaffold(
        modifier = modifier,
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
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("form_list"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "status") { FormStatus(state) }
            when {
                state.isLoading -> item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.testTag("form_loading"))
                    }
                }
                state.content != null -> {
                    if (state.content.rows.isEmpty()) {
                        item(key = "empty") {
                            Text(stringResource(R.string.form_empty), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    items(state.content.rows, key = { it.id }, contentType = { it::class }) { FormRow(it) }
                }
                else -> item(key = "error") {
                    Text(formFailureMessage(state), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
