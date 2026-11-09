package io.booup.roadmate.context

import io.booup.roadmate.contracts.VehicleAdapter
import io.booup.roadmate.contracts.VehicleContextRepository
import io.booup.roadmate.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Platform-neutral runtime. Lifecycle, age and source validation precede every UI projection. */
class VehicleContextStore(
    private val adapter: VehicleAdapter,
    private val mode: RuntimeMode,
    private val clock: ContextClock,
    private val scope: CoroutineScope,
    private val policies: ContextFreshnessPolicies = ContextFreshnessPolicies(),
    private val tickMillis: Long = 1_000,
) : VehicleContextRepository {
    init {
        require(tickMillis > 0)
        require((mode == RuntimeMode.DEMO) == (adapter.source == DataSource.SIMULATED)) {
            "Runtime binding and source disagree"
        }
    }

    private val lifecycleMutex = Mutex()
    private val stateLock = Any()
    private var active = false
    private var closed = false
    private var generation = 0L
    private var careSessionId = 0L
    private var lastSequence = -1L
    private var invalidatedAt: ObservationTime? = null
    private var invalidationReason = UnavailableReason.BACKGROUND
    private var frame = VehicleFrame()
    private var session: Job? = null
    private val mutableContext = MutableStateFlow(project())
    override val context = mutableContext.asStateFlow()

    override suspend fun foreground() = lifecycleMutex.withLock {
        val token = synchronized(stateLock) {
            if (closed || active) return@withLock
            active = true
            generation += 1
            publish()
            generation
        }
        session = scope.launch {
            try {
                launch(start = CoroutineStart.UNDISPATCHED) {
                    adapter.frames.collect { incoming ->
                        synchronized(stateLock) {
                            if (active && !closed && token == generation && incoming.sequence > lastSequence) {
                                lastSequence = incoming.sequence
                                val validated = validate(incoming)
                                if (frame.connection.isLive() != validated.connection.isLive()) careSessionId += 1
                                if (frame.connection.isLive() && !validated.connection.isLive()) {
                                    invalidatedAt = clock.now()
                                    invalidationReason = UnavailableReason.DISCONNECTED
                                }
                                frame = validated
                                publish()
                            }
                        }
                    }
                }
                launch {
                    while (isActive) {
                        delay(tickMillis)
                        synchronized(stateLock) { if (active && token == generation) publish() }
                    }
                }
                adapter.connect()
                awaitCancellation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().cancelChildren()
                reportSourceFailure(token)
            } finally {
                currentCoroutineContext().cancelChildren()
                withContext(NonCancellable) {
                    try {
                        adapter.disconnect()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        reportSourceFailure(token)
                    }
                }
            }
        }
    }

    override suspend fun background() = lifecycleMutex.withLock {
        synchronized(stateLock) {
            if (!active) return@withLock
            deactivate()
        }
        session?.cancelAndJoin()
        session = null
    }

    override fun close() {
        synchronized(stateLock) {
            closed = true
            deactivate()
        }
        session?.cancel()
    }

    private fun deactivate() {
        active = false
        generation += 1
        careSessionId += 1
        invalidatedAt = clock.now()
        invalidationReason = UnavailableReason.BACKGROUND
        frame = frame.invalidate(UnavailableReason.BACKGROUND).copy(
            connection = ConnectionState(ConnectionStatus.DISCONNECTED, UnavailableReason.BACKGROUND),
        )
        publish()
    }

    private fun reportSourceFailure(token: Long) = synchronized(stateLock) {
        if (active && !closed && token == generation) {
            careSessionId += 1
            invalidatedAt = clock.now()
            invalidationReason = UnavailableReason.SOURCE_ERROR
            frame = frame.invalidate(UnavailableReason.SOURCE_ERROR).copy(
                connection = ConnectionState(ConnectionStatus.FAILED, UnavailableReason.SOURCE_ERROR),
            )
            publish()
        }
    }

    private fun validate(incoming: VehicleFrame): VehicleFrame {
        val wrongSource = incoming.readings().any { reading ->
            when (reading) {
                is Reading.Observed -> if (mode == RuntimeMode.DEMO) reading.metadata.source != DataSource.SIMULATED
                    else reading.metadata.source == DataSource.SIMULATED || reading.metadata.source == DataSource.UNKNOWN
                is Reading.Unavailable -> mode == RuntimeMode.PRODUCTION && reading.source == DataSource.SIMULATED
            }
        }
        if (wrongSource) return VehicleFrame(
            sequence = incoming.sequence,
            connection = ConnectionState(ConnectionStatus.FAILED, UnavailableReason.SOURCE_MISMATCH),
            vehicle = emptyVehicleSnapshot(UnavailableReason.SOURCE_MISMATCH),
        )
        return when (incoming.connection.status) {
            ConnectionStatus.CONNECTED, ConnectionStatus.DEGRADED -> incoming
            else -> incoming.invalidate(UnavailableReason.DISCONNECTED)
        }
    }

    private fun <T : Any> evaluate(reading: Reading<T>, policy: FreshnessPolicy): EvaluatedReading<T> {
        val cutoff = invalidatedAt
        val observation = if (reading is Reading.Observed && cutoff != null) {
            val isOld = reading.metadata.observedAtMonotonicMillis?.let { it <= cutoff.monotonicMillis }
                ?: (reading.metadata.observedAtEpochMillis <= cutoff.epochMillis)
            if (isOld) reading.invalidate(invalidationReason) else reading
        } else reading
        return observation.evaluate(clock.now(), policy, frame.connection, active)
    }

    private fun project(): VehicleContext = VehicleContext(
        mode, active, frame.connection,
        evaluate(frame.vehicle.lowBeam, policies.light),
        evaluate(frame.vehicle.targetTemperatureCelsius, policies.hvac),
        evaluate(frame.vehicle.telltaleIdentifiers, policies.safety),
        evaluate(frame.vehicle.currentTripDrivingSeconds, policies.trip),
        evaluate(frame.vehicle.currentTripDistanceMeters, policies.trip),
        evaluate(frame.vehicle.driverPresent, policies.occupancy),
        evaluate(frame.vehicle.outsideTemperatureCelsius, policies.external),
        evaluate(frame.restrictions, policies.restrictions),
        evaluate(frame.motion, policies.occupancy),
        evaluate(frame.locationLabel, policies.external),
        evaluate(frame.navigationLabel, policies.external),
        CareContext(
            evaluate(frame.care.lighting, policies.external),
            evaluate(frame.care.lightSwitch, policies.light),
            evaluate(frame.care.preference, policies.preference),
            evaluate(frame.care.acEnabled, policies.hvac),
            evaluate(frame.care.defrostEnabled, policies.hvac),
            evaluate(frame.care.continuousDriving, policies.trip),
            evaluate(frame.care.safetySignals, policies.safety),
            evaluate(frame.care.fogRisk, policies.external),
        ),
        careSessionId,
        evaluate(frame.tripEnd, policies.occupancy),
        totalOdometerKilometers = evaluate(frame.vehicle.totalOdometerKilometers, policies.trip),
        frontLeftTargetTemperatureCelsius = evaluate(frame.vehicle.frontLeftTargetTemperatureCelsius, policies.hvac),
        frontLeftSeatOccupied = evaluate(frame.vehicle.frontLeftSeatOccupied, policies.occupancy),
    )

    private fun publish() { mutableContext.value = project() }

    private fun ConnectionState.isLive() = status == ConnectionStatus.CONNECTED || status == ConnectionStatus.DEGRADED
}
