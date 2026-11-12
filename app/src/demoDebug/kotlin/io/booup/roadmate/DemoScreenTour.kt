package io.booup.roadmate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.booup.roadmate.demo.journeySeason
import io.booup.roadmate.model.*
import io.booup.roadmate.presentation.DrivingPresentationPolicy
import io.booup.roadmate.progress.SafeProgressEngine
import io.booup.roadmate.security.AndroidStoreKeys
import io.booup.roadmate.storage.AesGcmEnvelope
import io.booup.roadmate.storage.FileTripRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
internal fun DemoScreenTour(visible: Boolean, close: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val clock = remember { AndroidContextClock() }
    var tourModel by remember { mutableStateOf<RoadmateViewModel?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(context) {
        val owner = ViewModelStore()
        val directory = File(context.cacheDir, "roadmate-tour-${UUID.randomUUID()}")
        try {
            val storage = FileTripRepository(directory, RuntimeMode.DEMO,
                progressEngine = SafeProgressEngine(explorationRewards = true),
                encryption = AesGcmEnvelope(AndroidStoreKeys("roadmate.demo.aggregate.v1"), RuntimeMode.DEMO))
            if (!prepareTourStorage(storage, clock.now())) {
                failed = true
                return@LaunchedEffect
            }
            tourModel = ViewModelProvider(owner, object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    requireNotNull(modelClass.cast(RoadmateViewModel { scope ->
                        RuntimeBindings.create(scope, clock, tourStorage = storage)
                    }))
            })[RoadmateViewModel::class.java]
            awaitCancellation()
        } finally {
            owner.clear()
            withContext(NonCancellable + Dispatchers.IO) { directory.deleteRecursively() }
        }
    }

    val viewModel = tourModel
    BackHandler(enabled = visible, onBack = close)
    if (viewModel == null) {
        if (visible) Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            RoadmateCard(Modifier.widthIn(max = 460.dp).padding(16.dp)) {
                Text(if (failed) "둘러보기를 준비하지 못했어요" else "둘러보기를 준비하고 있어요",
                    style = RoadmateType.cardTitle)
                GameButton("시연 선택으로", close)
            }
        }
        return
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.foreground()
                Lifecycle.Event.ON_STOP -> viewModel.background()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val vehicle by viewModel.state.collectAsStateWithLifecycle()
    val post by viewModel.postDriveUiState.collectAsStateWithLifecycle()
    val parked = DrivingPresentationPolicy().mode(vehicle, clock.now()) == DrivingPresentationMode.FULL_PARKED
    var stop by remember { mutableStateOf(TourStop.HOME) }
    var showIndex by remember { mutableStateOf(false) }
    LaunchedEffect(visible, parked, post.contentAllowed) {
        if (!visible || !parked || !post.contentAllowed) showIndex = false
    }
    if (!visible) return
    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), containerColor = GameInk,
        bottomBar = {
            if (parked && post.contentAllowed) TourNavigation(stop,
                select = { stop = it }, showIndex = { showIndex = true }, close = close)
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            key(stop) {
                RoadmateApp(viewModel, simulationLabel = "공모전 시연 · SIMULATED",
                    parkSimulation = viewModel.parkSimulation, tourStop = stop)
            }
        }
    }
    if (showIndex && parked && post.contentAllowed) {
        AlertDialog(onDismissRequest = { showIndex = false }, containerColor = GameInk,
            title = { Text("화면 목차", color = GamePaper, style = RoadmateType.cardTitle) },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TourStop.entries.forEach { target ->
                        GameButton("${target.ordinal + 1}. ${target.label}", {
                            stop = target
                            showIndex = false
                        }, Modifier.fillMaxWidth(), primary = stop == target)
                    }
                }
            }, confirmButton = { GameButton("닫기", { showIndex = false }) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TourNavigation(stop: TourStop, select: (TourStop) -> Unit, showIndex: () -> Unit, close: () -> Unit) {
    val stops = TourStop.entries
    val previous = stops.getOrNull(stop.ordinal - 1)
    val next = stops.getOrNull(stop.ordinal + 1)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(18.dp), color = GameInk,
            border = BorderStroke(1.dp, GameGold.copy(alpha = .38f))) {
            FlowRow(Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.Center) {
                GameButton("‹ 이전", { previous?.let(select) }, enabled = previous != null)
                Text("${stop.ordinal + 1}/${stops.size}  ${stop.label}",
                    Modifier.align(Alignment.CenterVertically).padding(horizontal = 7.dp),
                    color = GameGold, style = RoadmateType.metric)
                GameButton("다음 ›", { next?.let(select) }, primary = next != null, enabled = next != null)
                GameButton("목차", showIndex)
                GameButton("둘러보기 종료", close)
            }
        }
    }
}

internal suspend fun prepareTourStorage(storage: FileTripRepository, now: ObservationTime): Boolean {
    val day = 86_400_000L
    val season = journeySeason(null).copy(title = "카펫 여행 시즌",
        startsAtEpochMillis = (now.epochMillis - day).coerceAtLeast(0), endsAtEpochMillis = now.epochMillis + day)
    if (storage.installSeasons(listOf(season)) !is StorageResult.Success) return false
    val end = now.epochMillis - 30 * 60_000L
    val duration = 14 * 60_000L
    val start = end - duration
    if (storage.setConsent(ConsentScope.TRIP_HISTORY, true, start - 1, DataSource.SIMULATED) !is StorageResult.Success) {
        return false
    }
    val place = Expedition.GANGNEUNG.places.first()
    val origin = TripLocation("강릉 시연 출발지", DataSource.SIMULATED)
    val destination = TripLocation(place.name, DataSource.SIMULATED)
    val trip = TripRecord("tour-gangneung", TripState.COMPLETED, start, end, DataSource.SIMULATED,
        duration, duration, distanceMeters = 8_000, startLocation = origin, endLocation = destination,
        routeSummary = RouteSummary(origin, destination, 8_000), completionReason = TripCompletionReason.MANUAL_DEMO,
        quality = TripDataQuality.COMPLETE, createdAtEpochMillis = end)
    val generation = storage.state.value.captureGeneration
    if (storage.save(trip, generation) !is StorageResult.Success) return false
    val arrival = RegionEvidence(trip.tripId, place.id, place.name, RegionEvidenceSource.DEMO, end, verified = true)
    val calendar = CalendarAttributes(CalendarSeason.AUTUMN, CalendarTimeOfDay.EVENING, "UTC")
    return storage.present(trip.tripId, arrival, calendar, generation) == StorageResult.Success(true)
}
