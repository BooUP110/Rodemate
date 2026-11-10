package io.booup.roadmate.platform

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyConfig
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.booup.roadmate.contracts.MotionAdapter
import io.booup.roadmate.context.MotionClassifier
import io.booup.roadmate.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** API 34 public AAOS read APIs only. GET timestamps are never replaced with receipt time. */
class AaosMotionAdapter(
    private val context: Context,
    private val clock: ContextClock,
    private val scope: CoroutineScope,
    config: MotionConfig = MotionConfig(),
    private val log: (String) -> Unit = {},
) : MotionAdapter {
    private val mutable = MutableStateFlow(MotionObservation())
    override val observations = mutable.asStateFlow()
    private val classifier = MotionClassifier(config)
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var work: Job? = null
    private var generation = 0L
    private var callback: CarPropertyManager.CarPropertyEventCallback? = null
    private val properties = listOf(VehiclePropertyIds.PERF_VEHICLE_SPEED,
        VehiclePropertyIds.GEAR_SELECTION, VehiclePropertyIds.PARKING_BRAKE_ON)

    override suspend fun connect(): AdapterResult<Unit> = withContext(Dispatchers.Main.immediate) {
        if (car != null) return@withContext AdapterResult.Available(Unit)
        if (Build.VERSION.SDK_INT < 29) return@withContext AdapterResult.Unavailable(UnavailableReason.UNSUPPORTED)
        val token = ++generation
        mutable.value = MotionObservation(); classifier.reset()
        try {
            car = Car.createCar(context.applicationContext, Handler(Looper.getMainLooper()),
                Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT, Car.CarServiceLifecycleListener { instance, ready ->
                    if (token == generation) {
                        work?.cancel()
                        if (!ready) {
                            manager = null; invalidateAll(UnavailableReason.DISCONNECTED)
                        } else {
                            val source = instance.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                            manager = source
                            if (source == null) invalidateAll(UnavailableReason.UNSUPPORTED)
                            else work = scope.launch(Dispatchers.Main.immediate) { observe(source, token) }
                        }
                    }
                })
            AdapterResult.Available(Unit)
        } catch (e: Exception) {
            invalidateAll(reason(e)); log("motion connect error=${e.javaClass.name}")
            AdapterResult.Unavailable(reason(e))
        }
    }

    private suspend fun observe(source: CarPropertyManager, token: Long) {
        val events = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>?) {
                if (token == generation && manager === source && value != null) publish(value, "CALLBACK")
            }
            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                if (token == generation && manager === source) unavailable(propertyId, UnavailableReason.SOURCE_ERROR)
            }
        }
        callback = events
        val readable = mutableListOf<Int>()
        try {
            for (id in properties) {
                val permission = if (id == VehiclePropertyIds.PERF_VEHICLE_SPEED) Car.PERMISSION_SPEED else Car.PERMISSION_POWERTRAIN
                if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    unavailable(id, UnavailableReason.PERMISSION_DENIED)
                    log("motion permission property=$id permission=$permission granted=false")
                    continue
                }
                try {
                    val capability = withContext(Dispatchers.IO) { source.getCarPropertyConfig(id) }
                    if (token != generation) return
                    log("motion capability property=$id permission=$permission granted=true access=${capability?.access} type=${capability?.propertyType?.name} areas=${capability?.areaIds?.toList()} changeMode=${capability?.changeMode} minHz=${capability?.minSampleRate} maxHz=${capability?.maxSampleRate}")
                    if (capability == null || capability.access !in setOf(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE) || !capability.areaIds.contains(0)) {
                        unavailable(id, UnavailableReason.UNSUPPORTED); continue
                    }
                    val expected = when (id) {
                        VehiclePropertyIds.PERF_VEHICLE_SPEED -> java.lang.Float::class.java
                        VehiclePropertyIds.GEAR_SELECTION -> java.lang.Integer::class.java
                        else -> java.lang.Boolean::class.java
                    }
                    if (capability.propertyType != expected) { unavailable(id, UnavailableReason.INVALID_DATA); continue }
                    val registered = withContext(Dispatchers.IO) {
                        source.registerCallback(events, id, if (capability.changeMode == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS)
                            CarPropertyManager.SENSOR_RATE_NORMAL.coerceIn(capability.minSampleRate, capability.maxSampleRate)
                        else CarPropertyManager.SENSOR_RATE_ONCHANGE)
                    }
                    log("motion registration property=$id callback=$registered")
                    if (!registered) { unavailable(id, UnavailableReason.SOURCE_ERROR); continue }
                    readable += id
                    read(source, id, token)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { unavailable(id, reason(e)); log("motion setup property=$id error=${e.javaClass.name}") }
            }
            while (currentCoroutineContext().isActive && token == generation) {
                delay(1_000)
                for (id in readable) read(source, id, token)
                mutable.value = mutable.value.copy(motion = classifier.classify(mutable.value.speedMetersPerSecond, clock.now()))
            }
        } finally {
            // Cancellation waits for an in-flight blocking GET/register before unregistering.
            withContext(NonCancellable + Dispatchers.IO) { source.unregisterCallback(events) }
        }
    }

    private suspend fun read(source: CarPropertyManager, id: Int, token: Long) {
        try {
            val value = withContext(Dispatchers.IO) { source.getProperty<Any>(id, 0) }
            if (token == generation) publish(value, "GET")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { if (token == generation) { unavailable(id, reason(e)); log("motion get property=$id error=${e.javaClass.name}") } }
    }

    private fun publish(value: CarPropertyValue<*>, origin: String) {
        val id = value.propertyId
        if (value.status != CarPropertyValue.STATUS_AVAILABLE || value.areaId != 0) {
            unavailable(id, UnavailableReason.NO_VALUE); return
        }
        val now = clock.now()
        val sampleMillis = value.timestamp / 1_000_000
        val age = now.monotonicMillis - sampleMillis
        if (value.timestamp <= 0 || age < 0 || now.epochMillis < age) { unavailable(id, UnavailableReason.INVALID_DATA); return }
        val metadata = DataMetadata(DataSource.SYSTEM, now.epochMillis - age, Freshness.FRESH,
            CapabilityState.SUPPORTED, PermissionState.GRANTED, DataQuality.VALID, sampleMillis)
        val frame = mutable.value
        mutable.value = when (id) {
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                val raw = value.value as? Float
                if (raw == null || !raw.isFinite()) { unavailable(id, UnavailableReason.INVALID_DATA); return }
                val speed = Reading.Observed(raw.toDouble(), metadata)
                if(speed.isOlderObservationThan(frame.speedMetersPerSecond)) return
                frame.copy(speedMetersPerSecond = speed, motion = classifier.classify(speed, now))
            }
            VehiclePropertyIds.GEAR_SELECTION -> {
                val raw = value.value as? Int ?: run { unavailable(id, UnavailableReason.INVALID_DATA); return }
                frame.copy(selectedGear = Reading.Observed(when (raw) {
                    VehicleGear.GEAR_PARK -> SelectedGear.PARK
                    VehicleGear.GEAR_DRIVE -> SelectedGear.DRIVE
                    VehicleGear.GEAR_REVERSE -> SelectedGear.REVERSE
                    VehicleGear.GEAR_NEUTRAL -> SelectedGear.NEUTRAL
                    else -> SelectedGear.OTHER
                }, metadata))
            }
            VehiclePropertyIds.PARKING_BRAKE_ON -> {
                val raw = value.value as? Boolean ?: run { unavailable(id, UnavailableReason.INVALID_DATA); return }
                frame.copy(parkingBrakeOn = Reading.Observed(raw, metadata))
            }
            else -> return
        }
        log("motion reading property=$id origin=$origin raw=${value.value} status=${value.status} sampleMs=$sampleMillis ageMs=$age mapped=${mutable.value.motion} epoch=${now.epochMillis}")
    }

    private fun unavailable(id: Int, reason: UnavailableReason) {
        val absent = Reading.Unavailable(reason, DataSource.SYSTEM)
        mutable.value = when (id) {
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> { classifier.reset(); mutable.value.copy(speedMetersPerSecond = absent, motion = absent) }
            VehiclePropertyIds.GEAR_SELECTION -> mutable.value.copy(selectedGear = absent)
            VehiclePropertyIds.PARKING_BRAKE_ON -> mutable.value.copy(parkingBrakeOn = absent)
            else -> mutable.value
        }
        log("motion unavailable property=$id reason=$reason")
    }
    private fun invalidateAll(reason: UnavailableReason) { for (id in properties) unavailable(id, reason) }
    private fun reason(e: Exception) = when (e) {
        is SecurityException -> UnavailableReason.PERMISSION_DENIED
        else -> UnavailableReason.SDK_ERROR
    }
    override suspend fun disconnect() = withContext(Dispatchers.Main.immediate) {
        generation += 1
        work?.cancelAndJoin(); work = null; callback = null; manager = null
        try { car?.disconnect() } finally { car = null; classifier.reset(); invalidateAll(UnavailableReason.DISCONNECTED) }
        log("motion session released")
    }
}
