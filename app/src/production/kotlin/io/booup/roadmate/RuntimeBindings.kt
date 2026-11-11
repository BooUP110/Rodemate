package io.booup.roadmate

import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.care.DeterministicCareEngine
import io.booup.roadmate.contracts.VehicleAdapter
import io.booup.roadmate.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import io.booup.roadmate.storage.FileTripRepository
import io.booup.roadmate.trip.TripCoordinator
import io.booup.roadmate.trip.TripIdSource
import io.booup.roadmate.postdrive.PostDriveCoordinator

/** Honest unavailable source, not an SDK implementation or an automatic simulation fallback. */
class UnconfiguredVehicleAdapter : VehicleAdapter {
    override val source = DataSource.UNKNOWN
    private val mutableFrames = MutableStateFlow(VehicleFrame(
        connection = ConnectionState(ConnectionStatus.DISCONNECTED, UnavailableReason.NOT_VERIFIED),
        vehicle = emptyVehicleSnapshot(UnavailableReason.NOT_VERIFIED),
        restrictions = Reading.Unavailable(UnavailableReason.NOT_VERIFIED),
    ))
    override val frames = mutableFrames.asStateFlow()
    override suspend fun connect(): AdapterResult<Unit> {
        mutableFrames.value = mutableFrames.value.copy(sequence = mutableFrames.value.sequence + 1)
        return AdapterResult.Unavailable(UnavailableReason.NOT_VERIFIED)
    }
    override suspend fun disconnect() {
        mutableFrames.value = mutableFrames.value.invalidate(UnavailableReason.DISCONNECTED).copy(
            sequence = mutableFrames.value.sequence + 1,
            connection = ConnectionState(ConnectionStatus.DISCONNECTED, UnavailableReason.NOT_VERIFIED),
        )
    }
}

object RuntimeBindings {
    val runtimePermissions = listOf(android.car.Car.PERMISSION_SPEED)
    /** Android entry point always uses the actual SDK; missing service is never Demo fallback. */
    fun create(context: android.content.Context, scope: CoroutineScope, clock: ContextClock, storageRoot: File? = null): RuntimeBinding {
        val logging = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        val logger: (String) -> Unit = { if (logging) android.util.Log.i("RoadmatePleos", it) }
        val adapter = io.booup.roadmate.platform.PleosVehicleAdapter(clock, scope,
            { io.booup.roadmate.platform.SdkVehicleReadSession(context) }, log = logger,
            restrictionAdapter = io.booup.roadmate.platform.AaosDrivingRestrictionAdapter(context, clock, scope, log = logger),
            motionAdapter = io.booup.roadmate.platform.AaosMotionAdapter(context, clock, scope, log = logger),
            outsideAdapter = io.booup.roadmate.platform.AaosOutsideTemperatureAdapter(clock) {
                io.booup.roadmate.platform.AaosOutsideTemperatureSession(context,scope,logger)
            })
        val binding = bind(scope, clock, storageRoot, adapter,
            io.booup.roadmate.storage.AesGcmEnvelope(io.booup.roadmate.security.AndroidStoreKeys("roadmate.production.aggregate.v1"), RuntimeMode.PRODUCTION))
            .copy(readParkedWarnings = adapter::readParkedWarnings)
        if (logging) scope.launch {
            binding.repository.context.map { state ->
                "context connection=${state.connection.status} lowBeam=${state.lowBeam.currentValue} availability=${state.lowBeam.availability} source=${state.lowBeam.source} totalKm=${state.totalOdometerKilometers.currentValue} frontLeftTargetC=${state.frontLeftTargetTemperatureCelsius.currentValue} motion=${state.motion.currentValue} motionSource=${state.motion.source} seat=${state.frontLeftSeatOccupied.currentValue} driver=${state.driverPresent.currentValue} restriction=${state.restrictions.currentValue?.mode} seasonGate=${ContentGate().access(ContentArea.SEASON_PASS,state).allowed}"
            }.distinctUntilChanged().collect { android.util.Log.i("RoadmatePleos", it) }
        }
        if (logging) scope.launch {
            binding.trips?.uiState?.map { state ->
                "trip state=${state.runtime.state} active=${state.runtime.active?.tripId} completed=${state.runtime.completed?.tripId} saved=${state.storage.trips.map { it.tripId }} consent=${state.storage.consents[ConsentScope.TRIP_HISTORY]?.granted} postDrive=${state.postDrive} distance=${state.runtime.completed?.distanceMeters}"
            }?.distinctUntilChanged()?.collect { android.util.Log.i("RoadmateTrip", it) }
        }
        var lastCare: String? = null
        return if (!logging) binding else binding.copy(onCareDecision = { decision ->
            val summary = "care active=${decision.activeEvent?.ruleId} emitted=${decision.emittedEvents.map { it.ruleId }} evaluations=${decision.evaluations.map { it.ruleId to it.status }}"
            if (summary != lastCare) { android.util.Log.i("RoadmateCare", summary); lastCare = summary }
        })
    }
    /** Explicit platform-free composition retained for existing JVM contract tests. */
    fun create(scope: CoroutineScope, clock: ContextClock, storageRoot: File? = null,
        encryption: io.booup.roadmate.storage.StorageEncryption? = null): RuntimeBinding {
        return bind(scope, clock, storageRoot, UnconfiguredVehicleAdapter(), encryption)
    }
    private fun bind(scope: CoroutineScope, clock: ContextClock, storageRoot: File?, adapter: VehicleAdapter,
        encryption: io.booup.roadmate.storage.StorageEncryption?): RuntimeBinding {
        val context = VehicleContextStore(adapter, RuntimeMode.PRODUCTION, clock, scope)
        val storage = storageRoot?.let { FileTripRepository(File(it, "roadmate-production"), RuntimeMode.PRODUCTION,
            progressEngine = io.booup.roadmate.progress.SafeProgressEngine(explorationRewards = true),
            encryption = requireNotNull(encryption)) }
        val trips = storage?.let { TripCoordinator(it, RuntimeMode.PRODUCTION, clock, TripIdSource { UUID.randomUUID().toString() }) }
        val post=trips?.let { PostDriveCoordinator(it, requireNotNull(storage), RuntimeMode.PRODUCTION, clock,
            { context.context.value }, io.booup.roadmate.postdrive.RegionEvidenceProvider(::verifiedExpeditionArrival)) }
        return RuntimeBinding(context,
        careEngine = DeterministicCareEngine(clock),
        clock = clock,
        trips = trips,
        postDrive = post,
        comfort=storage?.let { io.booup.roadmate.local.ComfortCoordinator(it,RuntimeMode.PRODUCTION,clock,{context.context.value}) },
        progress=post?.let { io.booup.roadmate.progress.ProgressCoordinator(requireNotNull(storage),RuntimeMode.PRODUCTION,clock,{it.controlsAllowed()},
            listOf(io.booup.roadmate.progress.LocalSeasonCatalog.first)) },
    )
    }
}
