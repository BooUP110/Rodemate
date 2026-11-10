package io.booup.roadmate.contracts

import io.booup.roadmate.model.*
import kotlinx.coroutines.flow.StateFlow

/** Read-only Celsius observation; no SDK DTO or weather inference crosses this boundary. */
interface OutsideTemperatureAdapter {
    val readings: StateFlow<Reading<Double>>
    suspend fun connect(): AdapterResult<Unit>
    suspend fun disconnect()
}
