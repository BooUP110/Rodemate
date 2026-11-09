package io.booup.roadmate.contracts

import io.booup.roadmate.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Read-only source. Reconnect changes connection only; new samples need new observation times. */
interface VehicleAdapter {
    val source: DataSource
    val frames: StateFlow<VehicleFrame>
    suspend fun connect(): AdapterResult<Unit>
    suspend fun disconnect()
}

/** No waypoint writes until the NaviHelper action contract is verified. */
interface NavigationAdapter {
    val snapshots: Flow<AdapterResult<NavigationSnapshot>>
    suspend fun connect(): AdapterResult<Unit>
    suspend fun disconnect()
}

/** Output only after policy/audio-focus checks; acceptance must not mean playback completed. */
interface AudioAdapter {
    suspend fun speak(message: CompanionMessage): AdapterResult<Unit>
    suspend fun stop()
}

/** Cold start, disconnect and stale policy must fail closed; retain raw platform flags. */
interface DrivingRestrictionAdapter {
    val restrictions: StateFlow<DrivingRestrictions>
    suspend fun connect(): AdapterResult<Unit>
    suspend fun disconnect()
}
