package io.booup.roadmate.platform

import io.booup.roadmate.model.LowBeamState
import io.booup.roadmate.model.AdapterResult
import io.booup.roadmate.model.UnavailableReason

/** Product-owned port. SDK DTOs and Android services do not cross this boundary. */
interface VehicleReadSession {
    fun start(observer: VehicleReadObserver)
    fun refresh()
    suspend fun readTelltales(): AdapterResult<List<String>> = AdapterResult.Unavailable(UnavailableReason.UNSUPPORTED)
    fun close()
}

interface VehicleReadObserver {
    fun capability(property: String, readable: Boolean, raw: String)
    fun lowBeam(value: LowBeamState?, raw: String, origin: String)
    fun odometer(kilometers: Double?, raw: String, origin: String)
    fun washerWarning(active: Boolean?, raw: String, origin: String)
    fun frontLeftTargetTemperature(celsius: Double?, raw: String, origin: String)
    fun frontLeftSeatOccupied(occupied: Boolean?, raw: String, origin: String)
    fun failure(property: String, exception: Exception, reason: UnavailableReason =
        if (exception is SecurityException) UnavailableReason.PERMISSION_DENIED else UnavailableReason.SDK_ERROR)
}

/** Actual GET interval, not a timestamp heartbeat or an initialization retry. */
data class VehicleReadConfig(val refreshMillis: Long = 2_000) {
    init { require(refreshMillis > 0) }
}
