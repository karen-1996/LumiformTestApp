package com.lumiformtest.form.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.lumiformtest.form.presentation.model.FormRefreshFailureUi
import com.lumiformtest.form.presentation.viewmodel.FormUiState
import com.lumiformtest.resources.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun FormStatus(state: FormUiState) {
    val content = state.content ?: return
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val updated = remember(content.lastUpdatedEpochMillis, locale, zone) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)
            .format(Instant.ofEpochMilli(content.lastUpdatedEpochMillis))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.form_last_updated, updated), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.isRefreshing || state.isReadingCache) {
            Text(stringResource(R.string.form_refreshing), style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (state.hasReadFailure || state.refreshFailure != null) {
            Text(
                formFailureMessage(state), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
internal fun formFailureMessage(state: FormUiState): String = stringResource(
    if (state.hasReadFailure) {
        if (state.content == null) R.string.form_read_error else R.string.form_cached_read_error
    } else if (state.content == null) {
        when (state.refreshFailure) {
            FormRefreshFailureUi.OFFLINE -> R.string.no_internet_connection
            FormRefreshFailureUi.INVALID_CONTENT -> R.string.form_invalid_content
            FormRefreshFailureUi.STORAGE -> R.string.form_save_error
            else -> R.string.form_load_error
        }
    } else {
        when (state.refreshFailure) {
            FormRefreshFailureUi.OFFLINE -> R.string.no_internet_connection
            FormRefreshFailureUi.INVALID_CONTENT -> R.string.form_cached_invalid_content
            FormRefreshFailureUi.STORAGE -> R.string.form_cached_save_error
            else -> R.string.form_cached_refresh_error
        }
    }
)
