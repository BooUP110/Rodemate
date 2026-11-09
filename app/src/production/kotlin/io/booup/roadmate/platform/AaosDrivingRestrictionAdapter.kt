package io.booup.roadmate.platform

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.booup.roadmate.contracts.DrivingRestrictionAdapter
import io.booup.roadmate.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

fun normalizeRestriction(requiresDo: Boolean?, flags: Int?, epoch: Long?): DrivingRestrictions =
    DrivingRestrictions(when {
        requiresDo == null || flags == null -> UIMode.UNKNOWN_RESTRICTED
        !requiresDo && flags == 0 -> UIMode.PARKED
        else -> UIMode.RESTRICTED
    }, flags, requiresDo, epoch)

/** UX authority is the platform listener/current getter, never speed, gear or brake. */
class AaosDrivingRestrictionAdapter(
    private val context: Context,
    private val clock: ContextClock,
    private val scope: CoroutineScope,
    private val config: VehicleReadConfig = VehicleReadConfig(),
    private val log: (String) -> Unit = {},
) : DrivingRestrictionAdapter {
    private val mutable = MutableStateFlow(DrivingRestrictions())
    override val restrictions = mutable.asStateFlow()
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var job: Job? = null
    private var generation = 0L
    private var listener: CarUxRestrictionsManager.OnUxRestrictionsChangedListener? = null

    override suspend fun connect(): AdapterResult<Unit> {
        if (car != null) return AdapterResult.Available(Unit)
        if (Build.VERSION.SDK_INT < 29) return AdapterResult.Unavailable(UnavailableReason.UNSUPPORTED)
        val token = ++generation
        mutable.value = DrivingRestrictions()
        return try {
            car = Car.createCar(context.applicationContext, Handler(Looper.getMainLooper()),
                Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT, Car.CarServiceLifecycleListener { instance, ready ->
                    if (token == generation) {
                        if (!ready) { manager = null; mutable.value = DrivingRestrictions(); log("car service unavailable") }
                        else try {
                            val current = instance.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
                            manager = current
                            val updates = CarUxRestrictionsManager.OnUxRestrictionsChangedListener { value ->
                                if (token == generation) publish(value, "CALLBACK")
                            }
                            listener = updates
                            current?.registerListener(updates)
                            refresh()
                        } catch (e: Exception) { unavailable(e) }
                    }
                })
            job = scope.launch { while (isActive) { delay(config.refreshMillis); refresh() } }
            AdapterResult.Available(Unit)
        } catch (e: Exception) { unavailable(e); AdapterResult.Unavailable(UnavailableReason.SDK_ERROR) }
    }

    private fun refresh() {
        val current = manager ?: return
        try { publish(current.currentCarUxRestrictions, "GET") } catch (e: Exception) { unavailable(e) }
    }
    private fun publish(value: CarUxRestrictions?, origin: String) {
        val now = clock.now()
        mutable.value = normalizeRestriction(value?.isRequiresDistractionOptimization,
            value?.activeRestrictions, if (value == null) null else now.epochMillis)
        log("restriction origin=$origin mode=${mutable.value.mode} flags=${mutable.value.activeRestrictions} requiresDO=${mutable.value.requiresDistractionOptimization} epoch=${mutable.value.observedAtEpochMillis}")
    }
    private fun unavailable(e: Exception) {
        mutable.value = DrivingRestrictions()
        log("restriction unavailable type=${e.javaClass.name} message=${e.message}")
    }
    override suspend fun disconnect() {
        generation += 1
        job?.cancel(); job = null
        try { if (listener != null) manager?.unregisterListener() }
        finally {
            manager = null; listener = null
            try { car?.disconnect() } finally { car = null; mutable.value = DrivingRestrictions() }
        }
    }
}
