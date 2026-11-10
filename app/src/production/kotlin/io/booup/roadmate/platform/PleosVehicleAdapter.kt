package io.booup.roadmate.platform

import io.booup.roadmate.contracts.VehicleAdapter
import io.booup.roadmate.contracts.DrivingRestrictionAdapter
import io.booup.roadmate.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Read-only transport. Late callbacks from a released session cannot revive observations. */
class PleosVehicleAdapter(
    private val clock: ContextClock,
    private val scope: CoroutineScope,
    private val sessionFactory: () -> VehicleReadSession,
    private val config: VehicleReadConfig = VehicleReadConfig(),
    private val log: (String) -> Unit = {},
    private val restrictionAdapter: DrivingRestrictionAdapter? = null,
    private val motionAdapter: io.booup.roadmate.contracts.MotionAdapter? = null,
    private val outsideAdapter: io.booup.roadmate.contracts.OutsideTemperatureAdapter? = null,
) : VehicleAdapter {
    override val source = DataSource.VEHICLE_SDK
    private val mutableFrames = MutableStateFlow(VehicleFrame())
    override val frames = mutableFrames.asStateFlow()
    private val lock = Any()
    private var generation = 0L
    private var transportFailed = false
    private var session: VehicleReadSession? = null
    private var polling: Job? = null
    private var restrictionJob: Job? = null
    private var motionJob: Job? = null
    private var outsideJob: Job? = null

    override suspend fun connect(): AdapterResult<Unit> {
        val token = synchronized(lock) {
            if (session != null) return AdapterResult.Available(Unit)
            generation += 1
            transportFailed = false
            publish(mutableFrames.value.invalidate(UnavailableReason.NOT_CONNECTED).copy(
                connection = ConnectionState(ConnectionStatus.CONNECTING)))
            generation
        }
        return try {
            val next = sessionFactory()
            synchronized(lock) { session = next }
            next.start(object : VehicleReadObserver {
                override fun capability(property: String, readable: Boolean, raw: String) = receive(token) {
                    log("capability property=$property readable=$readable raw=$raw")
                    val frame = mutableFrames.value.copy(connection = ConnectionState(ConnectionStatus.CONNECTED))
                    publish(if (!readable) unavailable(frame, property, UnavailableReason.UNSUPPORTED) else frame)
                }
                override fun lowBeam(value: LowBeamState?, raw: String, origin: String) = receive(token) {
                    val at = clock.now()
                    val reading = value?.let { Reading.Observed(it, DataMetadata(source, at.epochMillis,
                        Freshness.FRESH, CapabilityState.SUPPORTED, PermissionState.GRANTED, DataQuality.VALID,
                        at.monotonicMillis)) } ?: Reading.Unavailable(UnavailableReason.NO_VALUE, source)
                    publish(mutableFrames.value.copy(connection = ConnectionState(ConnectionStatus.CONNECTED),
                        vehicle = mutableFrames.value.vehicle.copy(lowBeam = reading)))
                    log("reading property=lowBeam origin=$origin raw=$raw mapped=$value epoch=${at.epochMillis} source=$source availability=${reading.availability()}")
                }
                override fun odometer(kilometers: Double?, raw: String, origin: String) = receive(token) {
                    val at = clock.now()
                    val reading = when {
                        kilometers == null -> Reading.Unavailable(UnavailableReason.NO_VALUE, source)
                        !kilometers.isFinite() || kilometers < 0 -> Reading.Unavailable(UnavailableReason.INVALID_DATA, source)
                        else -> Reading.Observed(kilometers, DataMetadata(source, at.epochMillis, Freshness.FRESH,
                            CapabilityState.SUPPORTED, PermissionState.GRANTED, DataQuality.VALID, at.monotonicMillis))
                    }
                    publish(mutableFrames.value.copy(connection = ConnectionState(ConnectionStatus.CONNECTED),
                        vehicle = mutableFrames.value.vehicle.copy(totalOdometerKilometers = reading)))
                    log("reading property=odometer origin=$origin raw=$raw mapped=$kilometers unit=km epoch=${at.epochMillis} source=$source availability=${reading.availability()}")
                }
                override fun washerWarning(active: Boolean?, raw: String, origin: String) = receive(token) {
                    val at = clock.now()
                    val signals = active?.let { if (it) listOf(SafetySignal("LOW_WASHER_FLUID", SafetyClassification.UNCLASSIFIED)) else emptyList() }
                    val meta = DataMetadata(source, at.epochMillis, Freshness.FRESH, CapabilityState.SUPPORTED,
                        PermissionState.GRANTED, DataQuality.VALID, at.monotonicMillis)
                    val warnings = signals?.let { Reading.Observed(it.map { signal -> signal.subject }, meta) }
                        ?: Reading.Unavailable(UnavailableReason.NO_VALUE, source)
                    val reading = signals?.let { Reading.Observed(it, meta) } ?: Reading.Unavailable(UnavailableReason.NO_VALUE, source)
                    publish(mutableFrames.value.copy(connection = ConnectionState(ConnectionStatus.CONNECTED),
                        vehicle = mutableFrames.value.vehicle.copy(telltaleIdentifiers = warnings),
                        care = mutableFrames.value.care.copy(safetySignals = reading)))
                    log("reading property=washerWarning origin=$origin raw=$raw mapped=$signals epoch=${at.epochMillis} source=$source availability=${reading.availability()}")
                }
                override fun frontLeftTargetTemperature(celsius: Double?, raw: String, origin: String) = receive(token) {
                    val at = clock.now()
                    val reading = when {
                        celsius == null -> Reading.Unavailable(UnavailableReason.NO_VALUE, source)
                        !celsius.isFinite() -> Reading.Unavailable(UnavailableReason.INVALID_DATA, source)
                        else -> Reading.Observed(celsius, DataMetadata(source, at.epochMillis, Freshness.FRESH,
                            CapabilityState.SUPPORTED, PermissionState.GRANTED, DataQuality.VALID, at.monotonicMillis))
                    }
                    publish(mutableFrames.value.copy(connection = ConnectionState(ConnectionStatus.CONNECTED),
                        vehicle = mutableFrames.value.vehicle.copy(frontLeftTargetTemperatureCelsius = reading)))
                    log("reading property=frontLeftTarget origin=$origin raw=$raw mapped=$celsius unit=CELSIUS area=ROW_1_LEFT epoch=${at.epochMillis} source=$source availability=${reading.availability()}")
                }
                override fun frontLeftSeatOccupied(occupied: Boolean?, raw: String, origin: String) = receive(token) {
                    val at = clock.now()
                    // On this AVD the Console occupied flag and SDK Boolean are opposite.
                    // Keep the raw observation for diagnosis; it cannot be trusted as occupancy.
                    val reading = occupied?.let { Reading.Observed(it, DataMetadata(source, at.epochMillis,
                        Freshness.FRESH, CapabilityState.SUPPORTED, PermissionState.GRANTED, DataQuality.UNKNOWN,
                        at.monotonicMillis)) } ?: Reading.Unavailable(UnavailableReason.NO_VALUE, source)
                    publish(mutableFrames.value.copy(connection = ConnectionState(ConnectionStatus.CONNECTED),
                        vehicle = mutableFrames.value.vehicle.copy(frontLeftSeatOccupied = reading)))
                    log("reading property=frontLeftOccupied origin=$origin raw=$raw mapped=UNVERIFIED area=ROW_1_LEFT epoch=${at.epochMillis} source=$source availability=${reading.availability()}")
                }
                override fun failure(property: String, exception: Exception, reason: UnavailableReason) = receive(token) {
                    val frame = mutableFrames.value
                    val disconnected = reason == UnavailableReason.DISCONNECTED || property == "session"
                    if (disconnected) { transportFailed = true; polling?.cancel(); restrictionJob?.cancel(); motionJob?.cancel(); outsideJob?.cancel() }
                    publish((if (disconnected) frame.invalidate(reason) else unavailable(frame, property, reason)).copy(
                        connection = ConnectionState(if (disconnected) ConnectionStatus.FAILED else ConnectionStatus.DEGRADED, reason)))
                    log("failure property=$property type=${exception.javaClass.name} message=${exception.message} mapped=$reason")
                }
            })
            restrictionAdapter?.let { sourceAdapter ->
                restrictionJob = scope.launch {
                    sourceAdapter.restrictions.collect { restriction -> receive(token) {
                        val epoch = restriction.observedAtEpochMillis
                        val reading = if (epoch != null && restriction.mode != UIMode.UNKNOWN_RESTRICTED)
                            Reading.Observed(restriction, DataMetadata(DataSource.SYSTEM, epoch, Freshness.FRESH,
                                CapabilityState.SUPPORTED, PermissionState.GRANTED, DataQuality.VALID))
                        else Reading.Unavailable(UnavailableReason.NOT_CONNECTED, DataSource.SYSTEM)
                        publish(mutableFrames.value.copy(restrictions = reading))
                    } }
                }
                sourceAdapter.connect()
            }
            motionAdapter?.let { sourceAdapter ->
                motionJob = scope.launch {
                    sourceAdapter.observations.collect { observation -> receive(token) {
                        publish(mutableFrames.value.copy(motion = observation.motion))
                    } }
                }
                sourceAdapter.connect()
            }
            outsideAdapter?.let { sourceAdapter ->
                outsideJob=scope.launch {
                    sourceAdapter.readings.collect { reading -> receive(token) {
                        publish(mutableFrames.value.copy(vehicle=mutableFrames.value.vehicle.copy(outsideTemperatureCelsius=reading)))
                    } }
                }
                sourceAdapter.connect()
            }
            polling = scope.launch { while (isActive) { delay(config.refreshMillis); next.refresh() } }
            AdapterResult.Available(Unit) // Session started; connection stays CONNECTING until a real response.
        } catch (e: Exception) {
            synchronized(lock) {
                publish(mutableFrames.value.invalidate(UnavailableReason.SDK_ERROR).copy(
                    connection = ConnectionState(ConnectionStatus.FAILED, UnavailableReason.SDK_ERROR)))
            }
            log("initialization failed type=${e.javaClass.name} message=${e.message}")
            try { session?.close() } finally { session = null }
            AdapterResult.Unavailable(UnavailableReason.SDK_ERROR)
        }
    }

    override suspend fun disconnect() {
        val previous = synchronized(lock) {
            generation += 1
            polling?.cancel(); polling = null
            restrictionJob?.cancel(); restrictionJob = null
            motionJob?.cancel(); motionJob = null
            outsideJob?.cancel(); outsideJob=null
            val old = session; session = null
            publish(mutableFrames.value.invalidate(UnavailableReason.DISCONNECTED).copy(
                connection = ConnectionState(ConnectionStatus.DISCONNECTED, UnavailableReason.DISCONNECTED)))
            old
        }
        try { previous?.close() } finally {
            try { restrictionAdapter?.disconnect() } finally {
                try { motionAdapter?.disconnect() } finally { outsideAdapter?.disconnect() }
            }
        }
        log("session released")
    }

    private fun receive(token: Long, block: () -> Unit) = synchronized(lock) {
        if (token == generation && session != null && !transportFailed) block()
    }
    private fun unavailable(frame: VehicleFrame, property: String, reason: UnavailableReason): VehicleFrame = when (property) {
        "lowBeam" -> frame.copy(vehicle = frame.vehicle.copy(lowBeam = Reading.Unavailable(reason, source)))
        "odometer" -> frame.copy(vehicle = frame.vehicle.copy(totalOdometerKilometers = Reading.Unavailable(reason, source)))
        "washerWarning" -> frame.copy(vehicle = frame.vehicle.copy(telltaleIdentifiers = Reading.Unavailable(reason, source)),
            care = frame.care.copy(safetySignals = Reading.Unavailable(reason, source)))
        "frontLeftTarget" -> frame.copy(vehicle = frame.vehicle.copy(frontLeftTargetTemperatureCelsius = Reading.Unavailable(reason, source)))
        "frontLeftOccupied" -> frame.copy(vehicle = frame.vehicle.copy(frontLeftSeatOccupied = Reading.Unavailable(reason, source)))
        "sdkTripDistanceProbe", "sdkTripDurationProbe" -> frame // Diagnostic capabilities, no product field populated.
        else -> frame.invalidate(reason)
    }
    private fun publish(frame: VehicleFrame) { mutableFrames.value = frame.copy(sequence = mutableFrames.value.sequence + 1) }
}
