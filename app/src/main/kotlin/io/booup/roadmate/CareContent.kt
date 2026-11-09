package io.booup.roadmate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.booup.roadmate.model.*

/** User-facing UI consumes only Presentation and Interaction output, never CareRules or raw Context. */
@Composable
fun CareContent(state: CareUiState, respond: (CareResponse) -> Unit, displayed: (String, Long) -> Unit,
    selectCompanion: (CompanionId) -> Unit, showCompanionPicker: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.presentation?.let { presentation ->
            LaunchedEffect(presentation.eventId, presentation.revision) { displayed(presentation.eventId, presentation.revision) }
            if (presentation.mode == DrivingPresentationMode.COMPACT_DRIVING) {
                CompactDrivingCard(presentation, respond)
            } else if (presentation.severity == VisualSeverity.SAFETY) CareBanner(presentation, respond)
            else CareSuggestionCard(presentation, respond)
        }
        state.notice?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        if (state.fullContentAllowed && showCompanionPicker) {
            Text("함께할 동반자", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompanionProfiles.all.forEach { companion ->
                    OutlinedButton(onClick = { selectCompanion(companion.id) }) { Text(companion.displayName) }
                }
            }
        }
    }
}

@Composable
private fun CareBanner(presentation: CarePresentation, respond: (CareResponse) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        CareCardBody(presentation, respond)
    }
}

@Composable
private fun CareSuggestionCard(presentation: CarePresentation, respond: (CareResponse) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        CareCardBody(presentation, respond)
    }
}

@Composable
private fun CompactDrivingCard(presentation: CarePresentation, respond: (CareResponse) -> Unit) {
    val colors = if (presentation.severity == VisualSeverity.SAFETY) CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors()
    Card(colors = colors) { CareCardBody(presentation, respond) }
}

@Composable
private fun CareCardBody(presentation: CarePresentation, respond: (CareResponse) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(presentation.avatarInitials, Modifier.background(MaterialTheme.colorScheme.surface, CircleShape).padding(12.dp),
                style = MaterialTheme.typography.titleLarge)
            Column {
                Text(presentation.title, style = MaterialTheme.typography.titleLarge)
                Text(presentation.companionName, style = MaterialTheme.typography.labelLarge)
            }
        }
        // Text wraps: compact never truncates or rewrites the fixed safety message.
        Text(presentation.body, style = MaterialTheme.typography.bodyLarge)
        presentation.detailText?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        // Vertical buttons remain usable on narrow screens without a horizontal overflow.
        presentation.actions.forEach { action ->
            Button(onClick = { respond(CareResponse(presentation.eventId, presentation.revision, action.intent, action.domainAction)) },
                modifier = Modifier.fillMaxWidth()) { Text(action.label) }
        }
    }
}
