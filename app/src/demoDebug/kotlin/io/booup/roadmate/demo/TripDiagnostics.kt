package io.booup.roadmate.demo

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.booup.roadmate.RoadmateViewModel
import io.booup.roadmate.model.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun TripDiagnostics(viewModel: RoadmateViewModel) {
    val trip by viewModel.tripUiState.collectAsStateWithLifecycle()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    if (!ui.fullContentAllowed) return
    val coordinator = viewModel.trips ?: return
    Column {
        Text("TRIP_DIAGNOSTICS_DEMO_ONLY")
        Text("Trip State: ${trip.runtime.state} / recovery: ${trip.runtime.recoveryRequired}")
        Text("Active Trip Snapshot: ${trip.runtime.active}")
        Text("Post-drive: ${trip.postDrive} / storage error: ${trip.operationError}")
        Text("Consent: ${trip.storage.consents.mapValues { it.value.granted }}")
        Text("Saved Trips: ${trip.storage.trips.size} / memories: ${trip.storage.memories.size}")
        TextButton(onClick = { scope.launch { if (coordinator.userControlsAllowed()) coordinator.manual(true) } }) { Text("DEMO_FORCE_START_TRIP") }
        TextButton(onClick = { scope.launch { if (coordinator.userControlsAllowed()) coordinator.manual(false) } }) { Text("DEMO_FORCE_STOP_TRIP") }
        TextButton(onClick = { scope.launch { if (coordinator.userControlsAllowed()) coordinator.clearHistory() } }) { Text("DEMO_CLEAR_TRIP_HISTORY") }
        ConsentScope.entries.forEach { consent ->
            TextButton(onClick = { scope.launch { if (coordinator.userControlsAllowed()) coordinator.consent(consent, trip.storage.consents[consent]?.granted != true) } }) {
                Text("Demo consent toggle: $consent")
            }
        }
        trip.storage.trips.forEach { record ->
            Text("${record.tripId} · ${record.durationMillis} ms · ${record.distanceMeters ?: "unknown"} m · ${record.quality} · ${record.completionReason}")
            TextButton(onClick = { scope.launch { if (coordinator.userControlsAllowed()) coordinator.delete(record.tripId) } }) { Text("Demo delete ${record.tripId}") }
        }
    }
}
