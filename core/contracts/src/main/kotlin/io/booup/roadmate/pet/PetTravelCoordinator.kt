package io.booup.roadmate.pet

import io.booup.roadmate.contracts.PetTravelRepository
import io.booup.roadmate.local.localContentAllowed
import io.booup.roadmate.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Owner-selected context only. Never changes Care priorities, vehicle controls, XP or Trip records. */
class PetTravelCoordinator(
    private val repository:PetTravelRepository, private val mode:RuntimeMode, private val clock:ContextClock,
    private val context:()->VehicleContext, private val trips:()->TripUiState, private val ids:()->String,
) {
    private val mutex=Mutex()
    private var actionError:StorageError?=null
    private val mutable=MutableStateFlow(PetTravelUiState())
    val uiState=mutable.asStateFlow()
    private val restTimer=PetRestTimer()
    private var restBase:PetRestSession?=null
    private var lastRestSave=0L

    suspend fun initialize()=mutex.withLock {
        val loaded=repository.load()
        actionError=(loaded as? StorageResult.Failure)?.error
        restBase=repository.state.value.data.restSession
        restTimer.adopt(restBase,context(),clock.now(),recovery=true)
        checkpointRest(force=true); reconcile(); publish()
    }
    suspend fun refresh()=mutex.withLock {
        if(repository.state.value.loaded) { reconcile(); checkpointRest() }; publish()
    }
    private suspend fun checkpointRest(force:Boolean=false) {
        if(!repository.state.value.loaded || repository.state.value.error!=null) return
        val current=repository.state.value.data.restSession
        val now=clock.now()
        if(current!=restBase) { restBase=current; restTimer.adopt(current,context(),now,recovery=true) }
        val available=context().mode==mode && trips().storage.loaded && trips().storage.error==null
        restTimer.tick(if(available) context() else context().copy(foreground=false),now)
        val next=restTimer.value
        val transition=next?.paused!=current?.paused || next?.due!=current?.due
        if(next!=current && (force || transition || now.monotonicMillis-lastRestSave>=15_000)) {
            val result=repository.update { if(it.restSession==current) it.copy(restSession=next) else it }
            actionError=(result as? StorageResult.Failure)?.error
            restBase=repository.state.value.data.restSession
            restTimer.adopt(restBase,context(),now)
            lastRestSave=now.monotonicMillis
        }
    }
    private fun allowed():Boolean {
        val store=trips().storage
        return context().mode==mode && store.loaded && store.error==null && localContentAllowed(context(),clock.now())
    }
    private fun historyOn()=trips().storage.consents[ConsentScope.TRIP_HISTORY]?.granted==true
    private fun valid(record:TripRecord)=record.state==TripState.COMPLETED && record.quality!=TripDataQuality.INVALID &&
        record.endedAtEpochMillis?.let { it<=clock.now().epochMillis }==true &&
        record.source in if(mode==RuntimeMode.DEMO) setOf(DataSource.SIMULATED) else setOf(DataSource.DERIVED,DataSource.SYSTEM,DataSource.VEHICLE_SDK)
    private fun completed()=trips().storage.trips.filter(::valid)
    private fun privateProjection(data:PetTravelData):PetTravelData {
        val store=trips().storage
        val validIds=if(historyOn()) completed().map { it.tripId }.toSet() else emptySet()
        val plan=data.plan?.takeIf { historyOn() && it.captureGeneration==store.captureGeneration }
        return data.copy(cards=data.cards.filter { it.tripId in validIds },
            memos=data.memos.filter { it.sourceTripId==null || it.sourceTripId in validIds },plan=plan)
    }
    private suspend fun reconcile() {
        if(!trips().storage.loaded || trips().storage.error!=null) return
        val current=repository.state.value.data
        var next=privateProjection(current)
        val plan=next.plan
        if(next.enabled && plan!=null && plan.tripId==null) {
            val active=trips().runtime.active?.takeIf {
                it.startedAtEpochMillis>=plan.createdAtEpochMillis && it.startedAtEpochMillis<=clock.now().epochMillis &&
                    it.source in if(mode==RuntimeMode.DEMO) setOf(DataSource.SIMULATED) else setOf(DataSource.DERIVED,DataSource.SYSTEM,DataSource.VEHICLE_SDK)
            }
            val finished=completed().filter { it.startedAtEpochMillis>=plan.createdAtEpochMillis }
                .minByOrNull { it.startedAtEpochMillis }
            val id=active?.tripId ?: finished?.tripId
            if(id!=null) next=next.copy(plan=plan.copy(tripId=id))
        }
        if(next!=current) {
            val expected=current
            val result=repository.update { actual -> if(actual==expected) next else privateProjection(actual) }
            actionError=(result as? StorageResult.Failure)?.error
        }
    }
    private suspend fun edit(requireEnabled:Boolean=true, change:(PetTravelData)->PetTravelData?):Boolean=mutex.withLock {
        checkpointRest(force=true)
        if(!allowed() || !repository.state.value.loaded || repository.state.value.error!=null) { publish(); return@withLock false }
        var accepted=false
        val result=repository.update { stored ->
            if(!allowed() || (requireEnabled && (!stored.enabled || !stored.consentGranted || stored.profile==null))) stored
            else { val next=change(privateProjection(stored)); if(next==null) stored else { accepted=true; next } }
        }
        actionError=(result as? StorageResult.Failure)?.error
        restBase=repository.state.value.data.restSession
        restTimer.adopt(restBase,context(),clock.now())
        lastRestSave=clock.now().monotonicMillis
        publish(); accepted && result is StorageResult.Success
    }
    suspend fun saveProfile(name:String,kind:PetKind,consent:Boolean):Boolean {
        val clean=name.trim()
        if(!consent || clean.isBlank() || clean.length>24 || clean.any(Char::isISOControl)) return false
        return edit(false) { if(it.plan!=null || it.restSession!=null) null else it.copy(consentGranted=true,profile=PetProfile(clean,kind),enabled=if(it.profile==null) true else it.enabled) }
    }
    suspend fun setEnabled(enabled:Boolean)=edit(false) { data ->
        if(!data.consentGranted || data.profile==null) null
        else data.copy(enabled=enabled,plan=if(enabled) data.plan else null,packing=if(enabled) data.packing else emptySet(),
            restSession=if(enabled) data.restSession else null)
    }
    suspend fun deleteAll()=edit(false) { PetTravelData() }
    suspend fun choosePurpose(purpose:PetTravelPurpose)=edit { if(it.plan==null) it.copy(purpose=purpose) else null }
    suspend fun togglePacking(item:PetPackingItem)=edit { it.copy(packing=if(item in it.packing) it.packing-item else it.packing+item) }
    suspend fun beginOuting():Boolean=edit { data ->
        if(!historyOn() || data.plan!=null) null else data.copy(plan=PetTravelPlan(ids(),data.purpose,clock.now().epochMillis,trips().storage.captureGeneration))
    }
    suspend fun cancelOuting()=edit { it.copy(plan=null,packing=emptySet()) }
    suspend fun toggleStop(item:PetStopItem)=edit { data ->
        val plan=data.plan ?: return@edit null
        data.copy(plan=plan.copy(stopChecks=if(item in plan.stopChecks) plan.stopChecks-item else plan.stopChecks+item))
    }
    suspend fun finishOuting(note:String,nextDeparture:String):Boolean {
        val clean=note.trim(); val next=nextDeparture.trim()
        if(!textValid(clean,240,true) || !textValid(next,180,true)) return false
        return edit { data ->
            val plan=data.plan; val trip=plan?.tripId?.let { id -> completed().singleOrNull { it.tripId==id } }
            if(plan==null || trip==null || !historyOn() || data.cards.any { it.tripId==trip.tripId }) null
            else data.copy(plan=null,packing=emptySet(),
                cards=data.cards+PetTravelCard(trip.tripId,requireNotNull(data.profile).name,plan.purpose,clean,clock.now().epochMillis),
                memos=if(next.isEmpty()) data.memos else data.memos+PetDepartureMemo(ids(),next,trip.tripId))
        }
    }
    suspend fun deleteCard(tripId:String)=edit { data -> data.copy(cards=data.cards.filterNot { it.tripId==tripId },
        memos=data.memos.filterNot { it.sourceTripId==tripId }) }
    suspend fun addMemo(text:String):Boolean {
        val clean=text.trim(); if(!textValid(clean,180)) return false
        return edit { it.copy(memos=it.memos+PetDepartureMemo(ids(),clean,null)) }
    }
    suspend fun toggleMemo(id:String)=edit { it.copy(memos=it.memos.map { m -> if(m.id==id) m.copy(checked=!m.checked) else m }) }
    suspend fun deleteMemo(id:String)=edit { it.copy(memos=it.memos.filterNot { m -> m.id==id }) }
    suspend fun savePlace(id:String?,title:String,note:String,returnWanted:Boolean):Boolean {
        val clean=title.trim(); val body=note.trim()
        if(!textValid(clean,60) || !textValid(body,240,true)) return false
        return edit { data ->
            if(id!=null && data.places.none { it.id==id }) null
            else data.copy(places=data.places.filterNot { it.id==id }+PetPlaceNote(id ?: ids(),clean,body,returnWanted))
        }
    }
    suspend fun deletePlace(id:String)=edit { it.copy(places=it.places.filterNot { p -> p.id==id }) }
    suspend fun chooseRestInterval(minutes:Int):Boolean {
        if(minutes !in 15..240) return false
        return edit { if(it.restSession==null) it.copy(restIntervalMinutes=minutes) else null }
    }
    suspend fun startRestSession(storageConsent:Boolean=false):Boolean {
        if(!storageConsent) return false
        return edit { data ->
            val interval=data.restIntervalMinutes
            if(data.restSession!=null || interval==null) null else data.copy(restSession=PetRestSession(ids(),clock.now().epochMillis,interval))
        }
    }
    suspend fun pauseRestSession()=edit { data -> data.restSession?.let { data.copy(restSession=it.copy(paused=true)) } }
    suspend fun resumeRestSession()=edit { data -> data.restSession?.takeIf { !it.resting && it.elapsedTotalMillis<PetRestTimer.MAX_ELAPSED }
        ?.let { data.copy(restSession=it.copy(paused=false)) } }
    suspend fun beginPetRest()=edit { data -> data.restSession?.takeIf { !it.resting }
        ?.let { data.copy(restSession=it.copy(resting=true,paused=true,checks=emptySet())) } }
    suspend fun toggleRestCheck(item:PetRestCheck)=edit { data -> data.restSession?.takeIf { it.resting }?.let { s ->
        data.copy(restSession=s.copy(checks=if(item in s.checks) s.checks-item else s.checks+item)) } }
    suspend fun confirmPetRest()=edit { data -> data.restSession?.takeIf { it.resting && it.checks.isNotEmpty() &&
        it.confirmedRests<10_000 && it.elapsedTotalMillis<PetRestTimer.MAX_ELAPSED }?.let { s ->
        data.copy(restSession=s.copy(resting=false,paused=false,elapsedSinceRestMillis=0,confirmedRests=s.confirmedRests+1,checks=emptySet())) } }
    suspend fun cancelPetRest()=edit { data -> data.restSession?.takeIf { it.resting }?.let {
        data.copy(restSession=it.copy(resting=false,paused=true,checks=emptySet())) } }
    suspend fun stopRestSession()=edit { it.copy(restSession=null) }
    suspend fun claim(reward:PetPassReward)=edit { if(reward in earned(it) || reward in it.claimed) it.copy(claimed=it.claimed+reward) else null }
    suspend fun applyFrame(frame:PetCardFrame)=edit { data ->
        if(frame==PetCardFrame.PAPER || (frame==PetCardFrame.PAW && PetPassReward.PAW_FRAME in data.claimed) ||
            (frame==PetCardFrame.STAR && PetPassReward.STAR_FRAME in data.claimed)) data.copy(frame=frame) else null
    }
    private fun earned(data:PetTravelData)=buildSet {
        if(data.profile!=null) add(PetPassReward.WELCOME_BADGE)
        if(data.cards.isNotEmpty()) add(PetPassReward.PAW_FRAME)
        if(data.memos.isNotEmpty()) add(PetPassReward.STAR_FRAME)
    }
    private fun publish() {
        val stored=repository.state.value; val allow=allowed() && stored.loaded && stored.error==null
        if(!allow) { mutable.value=PetTravelUiState(loaded=stored.loaded,error=stored.error ?: actionError); return }
        val data=privateProjection(stored.data).let { if(it.restSession==restBase) it.copy(restSession=restTimer.value) else it }
        val cards=data.cards.mapNotNull { card -> completed().singleOrNull { it.tripId==card.tripId }?.let { card to it } }
            .sortedByDescending { it.second.startedAtEpochMillis }
        mutable.value=PetTravelUiState(true,true,data,data.plan?.tripId?.let { id -> completed().singleOrNull { it.tripId==id } },
            cards,earned(data)-data.claimed,historyOn(),actionError)
    }
    private fun textValid(text:String,max:Int,empty:Boolean=false)=text.length<=max && (empty || text.isNotBlank()) && text.none { it.isISOControl() && it!='\n' }
}
