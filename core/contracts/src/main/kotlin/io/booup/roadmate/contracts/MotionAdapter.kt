package io.booup.roadmate.contracts

import io.booup.roadmate.model.*
import kotlinx.coroutines.flow.StateFlow

/** Read-only physical motion boundary, independent of UX restrictions. */
interface MotionAdapter {
    val observations: StateFlow<MotionObservation>
    suspend fun connect(): AdapterResult<Unit>
    suspend fun disconnect()
}
