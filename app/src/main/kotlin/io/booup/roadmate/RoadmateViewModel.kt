package io.booup.roadmate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.booup.roadmate.model.*
import io.booup.roadmate.presentation.CareInteractionRuntime
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class RoadmateViewModel(factory: (CoroutineScope) -> RuntimeBinding) : ViewModel() {
    private val binding = factory(viewModelScope)
    val state = binding.repository.context
    val developerPanel = binding.developerPanel
    val onLaunch = binding.onLaunch
    val trips = binding.trips
    val tripUiState = trips?.uiState ?: MutableStateFlow(TripUiState()).asStateFlow()
    val postDrive = binding.postDrive
    val postDriveUiState = postDrive?.uiState ?: MutableStateFlow(PostDriveUiState()).asStateFlow()
    val progress=binding.progress
    val progressUiState=progress?.uiState ?: MutableStateFlow(ProgressUiState()).asStateFlow()
    val comfort=binding.comfort
    val comfortUiState=comfort?.uiState ?: MutableStateFlow(ComfortUiState()).asStateFlow()
    private val mutableRecall=MutableStateFlow(MemoryRecallState())
    val recallUiState=mutableRecall.asStateFlow()
    private var openedMemoryId:String?=null
    private var openedMemoryGeneration=-1L
    private val interaction = binding.clock?.let { CareInteractionRuntime(it, simulationFeedback = binding.simulationFeedback) }
    val uiState = interaction?.uiState ?: MutableStateFlow(CareUiState()).asStateFlow()
    val interactionRecords = interaction?.records ?: MutableStateFlow<Map<String, InteractionRecord>>(emptyMap()).asStateFlow()
    private var presentationTicker: Job? = null
    private var visible = false
    private val mutableCareDecision = MutableStateFlow(CareDecision())
    val careDecision = mutableCareDecision.asStateFlow()
    init {
        viewModelScope.launch { state.collect { refreshLocalExperience() } }
        trips?.let { trip -> viewModelScope.launch { trip.repository.state.collect { refreshLocalExperience() } } }
        trips?.let { trip -> viewModelScope.launch { trip.initialize(); progress?.initialize() } }
        progress?.let { engine -> viewModelScope.launch { engine.repository.state.collect { if(it.loaded) engine.refresh() } } }
        postDrive?.let { post -> viewModelScope.launch { tripUiState.collect { post.refresh() } } }
        binding.careEngine?.let { engine ->
            viewModelScope.launch { state.collect {
                val decision = engine.evaluate(it)
                mutableCareDecision.value = decision
                binding.onCareDecision?.invoke(decision)
                interaction?.update(decision, it)
                trips?.let { trip -> viewModelScope.launch { trip.update(it, decision) } }
            } }
        }
    }
    private fun refreshPresentation() {
        refreshLocalExperience()
        val decision = binding.careEngine?.evaluate(state.value) ?: CareDecision()
        mutableCareDecision.value = decision
        binding.onCareDecision?.invoke(decision)
        interaction?.update(decision, state.value)
        postDrive?.let { post -> viewModelScope.launch { post.refresh() } }
        progress?.let { engine -> viewModelScope.launch { engine.refresh() } }
    }
    private fun refreshLocalExperience() {
        val clock=binding.clock ?: return
        val store=trips?.repository?.state?.value ?: return
        if(openedMemoryGeneration!=store.captureGeneration) openedMemoryId=null
        val projected=io.booup.roadmate.local.MemoryRecallProjector().project(store,state.value,clock.now(),openedMemoryId)
        if(projected.openedMemoryId==null) openedMemoryId=null
        mutableRecall.value=projected
        comfort?.let { viewModelScope.launch { it.refresh() } }
    }
    fun openRecall(memoryId:String) {
        openedMemoryGeneration=trips?.repository?.state?.value?.captureGeneration ?: -1
        openedMemoryId=memoryId; refreshLocalExperience()
    }
    fun closeRecall() { openedMemoryId=null; refreshLocalExperience() }
    fun comfortAction(action:suspend (io.booup.roadmate.local.ComfortCoordinator)->Unit)=viewModelScope.launch { comfort?.let { action(it) } }
    fun foreground() = viewModelScope.launch {
        visible = true
        binding.repository.foreground()
        presentationTicker?.cancel()
        presentationTicker = viewModelScope.launch {
            while (isActive) {
                delay(binding.presentationTickMillis)
                refreshPresentation()
            }
        }
    }
    fun refreshVehiclePermissions() = viewModelScope.launch {
        binding.repository.background()
        if (visible) binding.repository.foreground()
    }
    fun background() = viewModelScope.launch {
        visible = false
        presentationTicker?.cancel(); presentationTicker = null
        binding.repository.background()
        refreshPresentation()
        trips?.update(state.value, careDecision.value)
    }
    fun displayed(eventId: String, revision: Long) = viewModelScope.launch {
        interaction?.displayed(eventId, revision)
    }
    fun respond(response: CareResponse) = viewModelScope.launch {
        refreshPresentation(); interaction?.respond(response)
    }
    fun selectCompanion(id: CompanionId) = viewModelScope.launch {
        interaction?.selectCompanion(id)
    }
    fun postDriveAction(action: suspend (io.booup.roadmate.postdrive.PostDriveCoordinator) -> Unit) = viewModelScope.launch {
        postDrive?.let { action(it) }
    }
    fun progressAction(action:suspend (io.booup.roadmate.progress.ProgressCoordinator)->Unit)=viewModelScope.launch { progress?.let { action(it) } }
    override fun onCleared() {
        binding.repository.close()
        binding.careEngine?.reset()
        presentationTicker?.cancel()
        interaction?.close()
        super.onCleared()
    }
}
