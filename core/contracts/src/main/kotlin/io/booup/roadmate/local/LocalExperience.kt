package io.booup.roadmate.local

import io.booup.roadmate.model.*
import io.booup.roadmate.contracts.ComfortPreferenceRepository
import io.booup.roadmate.presentation.DrivingPresentationPolicy
import io.booup.roadmate.trip.TripEvidenceMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneOffset

/** Physical motion AND current UX authority; never occupancy or DO approval. */
fun localContentAllowed(context:VehicleContext,now:ObservationTime):Boolean =
    context.foreground && context.connection.status in setOf(ConnectionStatus.CONNECTED,ConnectionStatus.DEGRADED) &&
        TripEvidenceMapper().map(context,now).motion in setOf(MotionState.PARKED,MotionState.STATIONARY) &&
        DrivingPresentationPolicy().mode(context,now)==DrivingPresentationMode.FULL_PARKED

/** User-requested stationary advice. It does not emit CareEvent, ControlIntent, XP or driver facts. */
class StationaryComfortRule(private val policies:ContextFreshnessPolicies=ContextFreshnessPolicies()) {
    fun evaluate(context:VehicleContext,preference:PreferredCabinTemperature?,now:ObservationTime,requested:Boolean):ComfortAdvice? {
        if(!requested || preference==null || !localContentAllowed(context,now) || preference.confirmedAtEpochMillis>now.epochMillis) return null
        val simulated=context.mode==RuntimeMode.DEMO
        if(preference.source!=if(simulated) DataSource.SIMULATED else DataSource.USER_INPUT) return null
        val outside=context.outsideTemperatureCelsius.observation.evaluate(now,policies.external,context.connection,context.foreground)
        val target=context.frontLeftTargetTemperatureCelsius.observation.evaluate(now,policies.hvac,context.connection,context.foreground)
        if(outside.source!=if(simulated) DataSource.SIMULATED else DataSource.SYSTEM) return null
        if(target.source!=if(simulated) DataSource.SIMULATED else DataSource.VEHICLE_SDK) return null
        val a=outside.currentValue ?: return null; val b=target.currentValue ?: return null
        if(!a.isFinite() || !b.isFinite()) return null
        return ComfortAdvice(a,b,preference.valueCelsius)
    }
}

class ComfortCoordinator(private val repository:ComfortPreferenceRepository,private val mode:RuntimeMode,
    private val clock:ContextClock,private val context:()->VehicleContext) {
    private val mutex=Mutex(); private var requested=false; private var error:StorageError?=null
    private val mutable=MutableStateFlow(ComfortUiState()); val uiState=mutable.asStateFlow()
    suspend fun refresh()=mutex.withLock { publish() }
    suspend fun dismiss()=mutex.withLock { requested=false; publish() }
    suspend fun request():Boolean=mutex.withLock {
        requested=allowed(); publish(); mutable.value.advice!=null
    }
    suspend fun save(valueCelsius:Double):Boolean=mutex.withLock {
        if(!allowed() || !valueCelsius.isFinite() || valueCelsius !in 16.0..30.0) { publish(); return@withLock false }
        val now=clock.now(); val source=if(mode==RuntimeMode.DEMO) DataSource.SIMULATED else DataSource.USER_INPUT
        val result=repository.setPreferredTemperature(PreferredCabinTemperature(valueCelsius,source,now.epochMillis),now.epochMillis,source)
        error=(result as? StorageResult.Failure)?.error; requested=false; publish(); result is StorageResult.Success
    }
    suspend fun delete():Boolean=mutex.withLock {
        if(!allowed()) { publish(); return@withLock false }
        val result=repository.setPreferredTemperature(null,clock.now().epochMillis,if(mode==RuntimeMode.DEMO) DataSource.SIMULATED else DataSource.USER_INPUT)
        error=(result as? StorageResult.Failure)?.error; requested=false; publish(); result is StorageResult.Success
    }
    private fun allowed()=context().mode==mode && localContentAllowed(context(),clock.now()) && repository.state.value.loaded && repository.state.value.error==null
    private fun publish() {
        val store=repository.state.value; val allow=allowed()
        if(!allow) requested=false
        val preference=store.preferredCabinTemperature.takeIf { store.consents[ConsentScope.FUNCTIONAL_PREFERENCE]?.granted==true }
        val advice=if(allow) StationaryComfortRule().evaluate(context(),preference,clock.now(),requested) else null
        if(requested && advice==null) requested=false
        mutable.value=ComfortUiState(allow,if(allow) preference else null,advice,error ?: store.error)
    }
}

/** Reprojects current storage; no summary cache and no inferences about mood, route or fatigue. */
class MemoryRecallProjector {
    fun project(store:TripStorageState,context:VehicleContext,now:ObservationTime,openedMemoryId:String?=null):MemoryRecallState {
        if(!store.loaded || store.error!=null || !localContentAllowed(context,now)) return MemoryRecallState()
        if(store.consents[ConsentScope.TRIP_HISTORY]?.granted!=true) return MemoryRecallState(contentAllowed=true)
        val simulated=context.mode==RuntimeMode.DEMO
        val trips=store.trips.filter { it.state==TripState.COMPLETED && it.quality!=TripDataQuality.INVALID &&
            it.endedAtEpochMillis?.let { end -> end<=now.epochMillis }==true &&
            (if(simulated) it.source==DataSource.SIMULATED else it.source !in setOf(DataSource.SIMULATED,DataSource.UNKNOWN)) }
        val date=Instant.ofEpochMilli(now.epochMillis).atZone(ZoneOffset.UTC)
        val start=date.withDayOfMonth(1).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val end=date.withDayOfMonth(1).toLocalDate().plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val period=trips.filter { it.startedAtEpochMillis in start until end }
        val duration=try { period.fold(0L) { a,t -> Math.addExact(a,t.durationMillis) } } catch(_:ArithmeticException) { null }
        val reflections=period.count { store.postDrive[it.tripId]?.reflection?.let { r ->
            r.persistence==ReflectionPersistence.TRIP_LOCAL && (r.mood!=null || !r.text.isNullOrBlank()) }==true }
        val memories=if(store.consents[ConsentScope.EMOTIONAL_MEMORY]?.granted!=true) emptyList() else store.memories.filter { memory ->
            memory.confirmedByUser && memory.createdAtEpochMillis<=now.epochMillis &&
                memory.source==if(simulated) DataSource.SIMULATED else DataSource.USER_INPUT
        }.filter { memory -> trips.any { it.tripId==memory.tripId } &&
            (memory.memoryId!="reflection:${memory.tripId}" || store.postDrive[memory.tripId]?.reflection?.let { r ->
                memory.text==(r.text ?: r.mood?.name) }==true) }
        val recent=memories.sortedWith(compareByDescending<PersistentEmotionalMemory> { it.createdAtEpochMillis }.thenBy { it.memoryId }).firstOrNull()
        val opened=memories.singleOrNull { it.memoryId==openedMemoryId }
        return MemoryRecallState(true,PeriodMemorySummary(date.year,date.monthValue,"UTC",period.size,duration,reflections),
            recent?.let { m -> RecentMemoryPreview(m.memoryId,m.tripId,m.createdAtEpochMillis,trips.single { it.tripId==m.tripId }.durationMillis) },
            opened?.memoryId,opened?.text)
    }
}
