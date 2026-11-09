package io.booup.roadmate.platform

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.Light
import ai.pleos.playground.vehicle.api.Odometer
import ai.pleos.playground.vehicle.api.Safety
import ai.pleos.playground.vehicle.api.HVAC
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import io.booup.roadmate.model.LowBeamState

/** API shapes and CAR_LIGHTS annotations verified against Vehicle-2.3.2.aar. No setters. */
class SdkVehicleReadSession(context: Context) : VehicleReadSession {
    private val handler = Handler(Looper.getMainLooper())
    private fun onMain(block: () -> Unit) {
        val guarded = {
            try { block() } catch (e: Exception) {
                if (active) observer?.failure("session", e, SdkErrorMapping.reason(e))
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) guarded() else handler.post { guarded() }
    }
    private val vehicle = Vehicle(context.applicationContext)
    private var light: Light? = null
    private var odometer: Odometer? = null
    private var safety: Safety? = null
    private var hvac: HVAC? = null
    private val frontLeft = SeatArea(SeatValue.ROW_1_LEFT)
    private var observer: VehicleReadObserver? = null
    @Volatile private var active = false
    @Volatile private var readable = false
    @Volatile private var pending = false
    private var registered = false
    @Volatile private var odometerReadable = false
    @Volatile private var odometerPending = false
    private var odometerRegistered = false
    @Volatile private var safetyReadable = false
    @Volatile private var safetyPending = false
    @Volatile private var hvacReadable = false
    @Volatile private var hvacPending = false
    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) { deliverOdometer(value, "CALLBACK") }
        override fun onFailed(e: Exception) { fail("odometer", e) }
    }
    private val listener = object : LightStateListener {
        override fun onLightStateUpdated(value: LightState?) { deliver(value, "CALLBACK") }
        override fun onFailed(e: Exception) { fail("lowBeam", e) }
    }
    override fun start(observer: VehicleReadObserver) = onMain {
        this.observer = observer; active = true
        vehicle.initialize()
        val domain = vehicle.getLight(); light = domain
        domain.checkLowBeamLightStateCapabilities(onComplete = { config, values -> onMain {
            if (active) {
                readable = config.accessType in setOf(AccessType.READ, AccessType.READ_WRITE)
                observer.capability("lowBeam", readable, "access=${config.accessType},values=$values")
                if (readable) {
                    domain.registerLowBeamLightState(listener); registered = true
                    refresh()
                }
            }
        } }, onFailed = { fail("lowBeam", it) })
        val distanceDomain = vehicle.getOdometer(); odometer = distanceDomain
        // Read-only capability probes: even public API presence does not establish AVD support
        // or reset/unit semantics. Never map these values into an app trip until verified.
        distanceDomain.checkTripCurrentDrivingDistanceCapabilities(onComplete = { config -> onMain {
            if (active) observer.capability("sdkTripDistanceProbe", false, "apiAccess=${config.accessType};mapping=NOT_VERIFIED")
        } }, onFailed = { fail("sdkTripDistanceProbe", it) })
        distanceDomain.checkTripCurrentDrivingTimeCapabilities(onComplete = { config -> onMain {
            if (active) observer.capability("sdkTripDurationProbe", false, "apiAccess=${config.accessType};mapping=NOT_VERIFIED")
        } }, onFailed = { fail("sdkTripDurationProbe", it) })
        distanceDomain.checkCurrentDistanceDrivenCapabilities(onComplete = { config -> onMain {
            if (active) {
                odometerReadable = config.accessType in setOf(AccessType.READ, AccessType.READ_WRITE)
                observer.capability("odometer", odometerReadable, "access=${config.accessType}")
                if (odometerReadable) {
                    distanceDomain.registerDistanceDriven(odometerListener); odometerRegistered = true
                    refresh()
                }
            }
        } }, onFailed = { fail("odometer", it) })
        // Aggregate Telltales capability/register require PRIVATE_CAR_SAFETY in this AAR.
        // Use the public CAR_SAFETY individual warning instead; never request private access.
        val safetyDomain = vehicle.getSafety(); safety = safetyDomain
        safetyDomain.checkLowWasherFluidWarningStateCapabilities(onComplete = { config -> onMain {
            if (active) {
                safetyReadable = config.accessType in setOf(AccessType.READ, AccessType.READ_WRITE)
                observer.capability("washerWarning", safetyReadable, "access=${config.accessType}")
                refresh()
            }
        } }, onFailed = { fail("washerWarning", it) })
        val climateDomain = vehicle.getHVAC(); hvac = climateDomain
        climateDomain.checkHvacTemperatureCapabilities(onComplete = { config, bounds, temperatureConfig -> onMain {
            if (active) {
                hvacReadable = config.accessType in setOf(AccessType.READ, AccessType.READ_WRITE) && bounds.containsKey(frontLeft)
                observer.capability("frontLeftTarget", hvacReadable, "access=${config.accessType},bounds=$bounds,temperatureConfig=$temperatureConfig")
                refresh()
            }
        } }, onFailed = { fail("frontLeftTarget", it) })
        // Seat boolean semantics remain unresolved on this AVD. No product read or permission.
        // Prior raw SDK/sample evidence is retained in the immutable Resume report.
    }
    override fun refresh() = onMain {
        if (!active) return@onMain
        if (readable && !pending) {
            pending = true
            light?.getCurrentLowBeamLightState(onComplete = { value -> onMain { pending = false; deliver(value, "GET") } },
                onFailed = { e -> onMain { pending = false; fail("lowBeam", e) } })
        }
        if (odometerReadable && !odometerPending) {
            odometerPending = true
            odometer?.getCurrentDistanceDriven(onComplete = { value -> onMain { odometerPending = false; deliverOdometer(value, "GET") } },
                onFailed = { e -> onMain { odometerPending = false; fail("odometer", e) } })
        }
        if (safetyReadable && !safetyPending) {
            safetyPending = true
            safety?.hasLowWasherFluidWarning(onComplete = { value -> onMain {
                safetyPending = false
                if (active) observer?.washerWarning(value, value?.toString() ?: "null", "GET")
            } }, onFailed = { e -> onMain { safetyPending = false; fail("washerWarning", e) } })
        }
        if (hvacReadable && !hvacPending) {
            hvacPending = true
            hvac?.getHvacTemperature(frontLeft, TemperatureUnit.CELSIUS, onComplete = { value -> onMain {
                hvacPending = false
                if (active) observer?.frontLeftTargetTemperature(value?.toDouble(), value?.toString() ?: "null", "GET")
            } }, onFailed = { e -> onMain { hvacPending = false; fail("frontLeftTarget", e) } })
        }
    }
    private fun deliver(value: LightState?, origin: String) = onMain {
        if (!active) return@onMain
        val normalized = when (value) { LightState.ON -> LowBeamState.ON; LightState.OFF -> LowBeamState.OFF; else -> null }
        observer?.lowBeam(normalized, value?.name ?: "null", origin)
    }
    private fun deliverOdometer(value: Float?, origin: String) = onMain {
        if (active) observer?.odometer(value?.toDouble(), value?.toString() ?: "null", origin)
    }
    private fun fail(property: String, exception: Exception) = onMain {
        if (active) observer?.failure(property, exception, SdkErrorMapping.reason(exception))
    }
    override fun close() = onMain {
        active = false
        try {
            try { if (registered) light?.unregisterLowBeamLightState(listener) }
            finally { if (odometerRegistered) odometer?.unregisterDistanceDriven(odometerListener) }
        }
        finally {
            registered = false; observer = null; vehicle.release()
        }
    }
}
