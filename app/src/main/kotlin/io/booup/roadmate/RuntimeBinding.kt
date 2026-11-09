package io.booup.roadmate

import android.os.SystemClock
import androidx.compose.runtime.Composable
import io.booup.roadmate.contracts.VehicleContextRepository
import io.booup.roadmate.contracts.CareRuleEngine
import io.booup.roadmate.model.ContextClock
import io.booup.roadmate.model.ObservationTime
import io.booup.roadmate.presentation.SimulationFeedback
import android.content.Intent
import io.booup.roadmate.trip.TripCoordinator
import io.booup.roadmate.postdrive.PostDriveCoordinator

class AndroidContextClock : ContextClock {
    override fun now() = ObservationTime(System.currentTimeMillis(), SystemClock.elapsedRealtime())
}

/** Variant-owned composition root; production supplies no developer UI. */
data class RuntimeBinding(
    val repository: VehicleContextRepository,
    val developerPanel: (@Composable () -> Unit)? = null,
    val careEngine: CareRuleEngine? = null,
    val clock: ContextClock? = null,
    val simulationFeedback: SimulationFeedback? = null,
    val onLaunch: ((Intent) -> Unit)? = null,
    val presentationTickMillis: Long = 1_000,
    val trips: TripCoordinator? = null,
    val postDrive: PostDriveCoordinator? = null,
    val progress: io.booup.roadmate.progress.ProgressCoordinator? = null,
    val onCareDecision: ((io.booup.roadmate.model.CareDecision) -> Unit)? = null,
) {
    init { require(presentationTickMillis > 0) }
}
