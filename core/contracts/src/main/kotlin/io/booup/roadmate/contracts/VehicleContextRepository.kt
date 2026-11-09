package io.booup.roadmate.contracts

import io.booup.roadmate.model.VehicleContext
import kotlinx.coroutines.flow.StateFlow

interface VehicleContextRepository {
    val context: StateFlow<VehicleContext>
    suspend fun foreground()
    suspend fun background()
    fun close()
}
