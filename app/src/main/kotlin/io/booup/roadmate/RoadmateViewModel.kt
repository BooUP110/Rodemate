package io.booup.roadmate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.booup.roadmate.model.*
import io.booup.roadmate.presentation.CareInteractionRuntime
import io.booup.roadmate.presentation.DrivingPresentationPolicy
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class RoadmateViewModel(factory: (CoroutineScope) -> RuntimeBinding) : ViewModel() {
    private val binding = factory(viewModelScope)
    val state = binding.repository.context
    val developerPanel = binding.developerPanel
    val companionPreviews = binding.companionPreviews
    val journeyGame = binding.journeyGame
    val canSimulateVisit = binding.simulateVisit != null
    private val mutableJourney = MutableStateFlow(JourneyVisitUi())
    val journey = mutableJourney.asStateFlow()
    fun simulateVisit(place: JourneyPlace) {
        val simulate = binding.simulateVisit ?: return
        if (mutableJourney.value.running) return
        mutableJourney.value = JourneyVisitUi(place, running = true)
        viewModelScope.launch {
            var saved = false
            try { saved = simulate(place) } finally {
                mutableJourney.value = JourneyVisitUi(place, saved = saved,
                    tripId = postDriveUiState.value.session.tripId.takeIf { saved })
            }
        }
    }
    val parkSimulation = binding.parkSimulation
    suspend fun readParkedWarnings(): AdapterResult<List<String>> {
        val clock = binding.clock ?: return AdapterResult.Unavailable(UnavailableReason.NOT_VERIFIED)
        fun parked() = DrivingPresentationPolicy().mode(state.value, clock.now()) == DrivingPresentationMode.FULL_PARKED
        if (!parked()) return AdapterResult.Unavailable(UnavailableReason.NOT_VERIFIED)
        val result = binding.readParkedWarnings?.invoke() ?: return AdapterResult.Unavailable(UnavailableReason.UNSUPPORTED)
        return if (parked()) result else AdapterResult.Unavailable(UnavailableReason.NOT_VERIFIED)
    }
    val onLaunch = binding.onLaunch
    val trips = binding.trips
    val tripUiState = trips?.uiState ?: MutableStateFlow(TripUiState()).asStateFlow()
    val postDrive = binding.postDrive
    val postDriveUiState = postDrive?.uiState ?: MutableStateFlow(PostDriveUiState()).asStateFlow()
    val progress=binding.progress
    val progressUiState=progress?.uiState ?: MutableStateFlow(ProgressUiState()).asStateFlow()
    private val interaction = binding.clock?.let {
        CareInteractionRuntime(it, simulationFeedback = binding.simulationFeedback,
            previewProfiles = companionPreviews.map { preview -> preview.profile })
    }
    val uiState = interaction?.uiState ?: MutableStateFlow(CareUiState()).asStateFlow()
    val interactionRecords = interaction?.records ?: MutableStateFlow<Map<String, InteractionRecord>>(emptyMap()).asStateFlow()
    private var presentationTicker: Job? = null
    private var visible = false
    private val mutableCareDecision = MutableStateFlow(CareDecision())
    val careDecision = mutableCareDecision.asStateFlow()
    init {
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
        val decision = binding.careEngine?.evaluate(state.value) ?: CareDecision()
        mutableCareDecision.value = decision
        binding.onCareDecision?.invoke(decision)
        interaction?.update(decision, state.value)
        postDrive?.let { post -> viewModelScope.launch { post.refresh() } }
        progress?.let { engine -> viewModelScope.launch { engine.refresh() } }
    }
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
    fun restoreJourneyCompanion() {
        journeyGame?.state?.value?.equipped?.let(::selectCompanion)
    }
    fun equipJourneyCompanion(id: CompanionId, onEquipped: () -> Unit = {}) = viewModelScope.launch {
        val game = journeyGame ?: return@launch
        if ((game.state.value.equipped == id || game.equip(id)) && interaction?.selectCompanion(id) == true) onEquipped()
    }
    fun equipDefaultCompanion(id: CompanionId) = viewModelScope.launch {
        val game = journeyGame
        if (game?.state?.value?.equipped == null || game.useDefaultCompanion()) interaction?.selectCompanion(id)
    }
    fun journeyAction(action: suspend JourneyGame.() -> Unit) = viewModelScope.launch { journeyGame?.action() }
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
