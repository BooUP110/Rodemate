package io.booup.roadmate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.booup.roadmate.model.*
import io.booup.roadmate.presentation.DrivingPresentationPolicy
import kotlinx.coroutines.launch
import java.util.Locale

private enum class RoadmatePage(val label: String, val symbol: String, val art: String,
    val title: String = label, val artIndex: Int = 3) {
    HOME("동행", "⌂", "home-parked.png"),
    MAP("탐험 지도", "◇", "map-seoul.png", "안개 너머, 우리의 한국", 0),
    COLLECTION("엽서 컬렉션", "✉", "map-gangneung.png", artIndex = 1),
    SEASON("시즌 패스", "✦", "map-gangneung.png"),
    MEMORY("드라이브 기억", "↗", "drive-memory.png", artIndex = 2),
    SETTINGS("설정", "⚙", "home-parked.png", "나의 동행 설정"),
    DEMO("시연 선택", "▷", "home-parked.png", "함께 체험할 이야기"),
}

internal enum class TourStop(val label: String) {
    HOME("동행"), STORY("강릉 이야기"), MAP("안개 지도"), ALBUM("엽서"),
    REWARDS("시즌 보상"), DRAW("패스권 뽑기"), VEHICLE("차량 상태"),
}

/** Demo controls are supplied only by the variant composition root. */
@Composable
internal fun RoadmateApp(viewModel: RoadmateViewModel, simulationLabel: String? = null,
    demoControls: (@Composable () -> Unit)? = null, parkSimulation: (() -> Unit)? = null,
    screenTour: (@Composable (visible: Boolean, close: () -> Unit) -> Unit)? = null, tourStop: TourStop? = null) {
    val context by viewModel.state.collectAsStateWithLifecycle()
    val trip by viewModel.tripUiState.collectAsStateWithLifecycle()
    val post by viewModel.postDriveUiState.collectAsStateWithLifecycle()
    val care by viewModel.uiState.collectAsStateWithLifecycle()
    val journey by viewModel.journey.collectAsStateWithLifecycle()
    val progress by viewModel.progressUiState.collectAsStateWithLifecycle()
    val game = viewModel.journeyGame
    val gameState = game?.state?.collectAsStateWithLifecycle()?.value
    val previews = viewModel.companionPreviews
    val activePreview = previews.firstOrNull { it.profile.id == care.companion.id }
    val startPage = when (tourStop) {
        TourStop.MAP -> RoadmatePage.MAP
        TourStop.ALBUM -> RoadmatePage.COLLECTION
        TourStop.REWARDS, TourStop.DRAW -> RoadmatePage.SEASON
        else -> RoadmatePage.HOME
    }
    var page by rememberSaveable { mutableStateOf(startPage) }
    var touring by remember { mutableStateOf(false) }
    var homeTrial by remember { mutableStateOf<CompanionPreview?>(null) }
    var returnToMap by remember { mutableStateOf(false) }
    var mapPlace by remember { mutableStateOf<JourneyPlace?>(null) }
    var expedition by remember { mutableStateOf(if (tourStop == TourStop.MAP) Expedition.GANGNEUNG else Expedition.SEOUL) }
    var albumPlace by remember { mutableStateOf<JourneyPlace?>(null) }
    var storyOpen by rememberSaveable { mutableStateOf(tourStop == TourStop.STORY) }
    var storyBeat by rememberSaveable {
        mutableStateOf(if (tourStop == TourStop.STORY) MoonlightBeat.GREETING else MoonlightBeat.COVER)
    }
    var storyChapter by rememberSaveable { mutableStateOf(MoonlightChapter.OJUKHEON) }
    var storyVisit by rememberSaveable { mutableStateOf(false) }
    var storyAction by remember { mutableStateOf(false) }
    var storySaveFailed by rememberSaveable { mutableStateOf(false) }
    val canPlayStory = viewModel.canSimulateVisit && context.mode == RuntimeMode.DEMO && gameState != null
    val openStory: () -> Unit = {
        val completed = gameState?.moonlightCompleted ?: 0
        storyChapter = MoonlightChapter.entries[completed.coerceAtMost(MoonlightChapter.entries.lastIndex)]
        storyBeat = MoonlightBeat.COVER
        storySaveFailed = false
        storyOpen = true
    }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clock = remember { AndroidContextClock() }
    val full = DrivingPresentationPolicy().mode(context, clock.now()) == DrivingPresentationMode.FULL_PARKED
    val canBrowse = full && post.contentAllowed
    val canStartTour = canBrowse && !journey.running && context.mode == RuntimeMode.DEMO && screenTour != null
    val modeLabel = if (previews.isNotEmpty()) "내부 콜라보 시연"
        else simulationLabel ?: "실제 차량 정보 · 읽기 전용"
    val showMap: (JourneyPlace?) -> Unit = { place ->
        mapPlace = place
        expedition = Expedition.containing(place?.id) ?: Expedition.SEOUL
        page = RoadmatePage.MAP
    }
    val navigate: (RoadmatePage) -> Unit = { target ->
        focus.clearFocus()
        keyboard?.hide()
        if (target == RoadmatePage.MAP && (page != RoadmatePage.MAP || mapPlace != null)) showMap(null)
        if (target == RoadmatePage.COLLECTION) albumPlace = null
        page = target
    }
    val navigation: @Composable () -> Unit = {
        if (tourStop == null) RoadmateDock {
            RoadmatePage.entries.filter { it != RoadmatePage.DEMO || demoControls != null }.forEach { target ->
                RoadmateDockItem(target.label, target.symbol, selected = page == target,
                    enabled = canBrowse || target in setOf(RoadmatePage.HOME, RoadmatePage.DEMO)) { navigate(target) }
            }
        }
    }
    val priorityCare: @Composable () -> Unit = {
        CareContent(care, { viewModel.respond(it) }, { id, revision -> viewModel.displayed(id, revision) },
            { viewModel.selectCompanion(it) }, showCompanionPicker = false)
    }
    LaunchedEffect(canBrowse) {
        if (!canBrowse) {
            if (tourStop == null) page = RoadmatePage.HOME
            focus.clearFocus(force = true)
            keyboard?.hide()
        }
        else viewModel.restoreJourneyCompanion()
    }
    LaunchedEffect(page, canBrowse) {
        if (page != RoadmatePage.HOME || !canBrowse) homeTrial = null
    }
    LaunchedEffect(journey.running, canBrowse, returnToMap) {
        if (returnToMap && !journey.running && canBrowse) {
            val visited = trip.storage.visits.filter { it.source == RegionEvidenceSource.DEMO }.map { it.regionId }.toSet()
            showMap(if (journey.saved) Expedition.containing(journey.place?.id)?.next(visited) ?: journey.place else journey.place)
            returnToMap = false
        }
    }
    LaunchedEffect(storyVisit, journey, canBrowse, storyChapter) {
        if (storyVisit && canBrowse && !journey.running) {
            storySaveFailed = !journey.saved || journey.place?.id != storyChapter.place.id
            if (!storySaveFailed) storyBeat = MoonlightBeat.PUZZLE
            storyVisit = false
        }
    }
    BackHandler(storyOpen && canBrowse && !storyVisit && !storyAction) { storyOpen = false }
    RoadmateTheme {
        if (touring && screenTour != null) {
            screenTour(canStartTour) {
                touring = false
                page = RoadmatePage.DEMO
            }
            if (canStartTour) return@RoadmateTheme
        }
        Scaffold { insets ->
            if (tourStop == TourStop.VEHICLE && canBrowse) {
                RoadmateChapter("차량 상태", "home-parked.png", 3, modeLabel, insets, navigation, priorityCare) {
                    HomeVehicleCard(context, demo = true, viewModel::readParkedWarnings, Modifier.widthIn(max = 540.dp))
                }
                return@Scaffold
            }
            if (storyOpen && canPlayStory && canBrowse) {
                val recorded = trip.storage.visits.any {
                    it.source == RegionEvidenceSource.DEMO && it.regionId == storyChapter.place.id
                }
                val earned = journey.tripId?.takeIf { journey.place?.id == storyChapter.place.id }?.let { tripId ->
                    trip.storage.progression.ledger.filter { it.sourceTripId == tripId }.sumOf { it.awardedXp }
                } ?: 0L
                MoonlightEpisode(storyChapter, storyBeat, care.companion, viewModel, gameState,
                    storyVisit || storyAction, storySaveFailed, earned,
                    modifier = Modifier.fillMaxSize(), insets = insets, close = { storyOpen = false },
                    advance = { next ->
                        storySaveFailed = false
                        if (next == MoonlightBeat.PUZZLE && !recorded) {
                            storyVisit = true
                            viewModel.simulateVisit(storyChapter.place)
                        } else storyBeat = next
                    },
                    beginFromStart = { storyChapter = MoonlightChapter.OJUKHEON; storyBeat = MoonlightBeat.GREETING },
                    solve = { answer ->
                        val chapter = storyChapter
                        storyAction = true
                        viewModel.journeyAction {
                            try {
                                storySaveFailed = !completeMoonlightChapter(chapter, answer)
                                if (!storySaveFailed) storyBeat = MoonlightBeat.RESTORED
                            } finally { storyAction = false }
                        }
                    },
                    nextChapter = {
                        storyChapter = MoonlightChapter.entries[storyChapter.ordinal + 1]
                        storyBeat = MoonlightBeat.GREETING
                    },
                    openAlbum = { albumPlace = storyChapter.place; page = RoadmatePage.COLLECTION; storyOpen = false },
                    priorityCare = {
                        CareContent(care, { viewModel.respond(it) }, { id, revision -> viewModel.displayed(id, revision) },
                            { viewModel.selectCompanion(it) }, showCompanionPicker = false)
                    })
                return@Scaffold
            }
            if (page == RoadmatePage.HOME && canBrowse) {
                val trial = homeTrial
                val owned = if (gameState == null) previews else previews.filter { it.profile.id in gameState.companions }
                RoadmateHomeStage(insets, trial?.profile ?: care.companion, trial ?: activePreview,
                    CompanionProfiles.all + owned.map { it.profile }, modeLabel,
                    UiCopy.trip(trip.runtime.state, trip.runtime.recoveryRequired), post.collection.size,
                    progress.active?.progress?.totalXp, openStory.takeIf { canPlayStory && !journey.running },
                    openMap = { showMap(journey.place.takeIf { journey.saved }) },
                    selectCompanion = { id ->
                        homeTrial = null
                        if (gameState == null) viewModel.selectCompanion(id)
                        else if (id in gameState.companions) viewModel.equipJourneyCompanion(id)
                        else viewModel.equipDefaultCompanion(id)
                    },
                    endTrial = if (trial == null) null else { { homeTrial = null } },
                    navigation = navigation, priorityCare = priorityCare) {
                    HomeVehicleCard(context, simulationLabel != null, viewModel::readParkedWarnings)
                    if (trip.runtime.state == TripState.COMPLETED) GameButton("이번 드라이브 기억 열기", {
                        trip.runtime.completed?.let { record -> viewModel.postDriveAction { it.open(record.tripId) } }
                        navigate(RoadmatePage.MEMORY)
                    })
                    if (!post.historyEnabled) GameButton("기록 저장 설정", { navigate(RoadmatePage.SETTINGS) })
                }
                return@Scaffold
            }
            val collabSeason = progress.active?.season?.seasonId == JOURNEY_SEASON_ID ||
                progress.archived.any { it.season.seasonId == JOURNEY_SEASON_ID }
            if (page == RoadmatePage.SEASON && canBrowse && game != null && collabSeason &&
                (previews.isNotEmpty() || tourStop == TourStop.DRAW)) {
                JourneyPassContent(viewModel, game, "정차 · ${UiCopy.connection(context.connection.status)}",
                    modifier = Modifier.fillMaxSize(), insets = insets,
                    openAlbum = { albumPlace = null; page = RoadmatePage.COLLECTION },
                    openMemory = { page = RoadmatePage.MEMORY }, openHome = { page = RoadmatePage.HOME },
                    openStory = openStory.takeIf { canPlayStory && !journey.running },
                    tryOnHome = { homeTrial = it; page = RoadmatePage.HOME },
                    startAtDraw = tourStop == TourStop.DRAW,
                    priorityCare = {
                        CareContent(care, { viewModel.respond(it) }, { id, revision -> viewModel.displayed(id, revision) },
                            { viewModel.selectCompanion(it) }, showCompanionPicker = false)
                    })
                return@Scaffold
            }
            if (page == RoadmatePage.SEASON && canBrowse) {
                SeasonContent(viewModel, activePreview ?: previews.firstOrNull(),
                    openStory.takeIf { canPlayStory && !journey.running }, insets, { navigate(RoadmatePage.HOME) }, priorityCare)
                return@Scaffold
            }
            if (canBrowse) {
                val art = if (page == RoadmatePage.MAP) "map-${expedition.asset}.png" else page.art
                val artIndex = if (page == RoadmatePage.MAP) expedition.postcardIndex else page.artIndex
                key(page) {
                    RoadmateChapter(page.title, art, artIndex, modeLabel, insets, navigation, priorityCare) {
                        when (page) {
                            RoadmatePage.DEMO -> {
                                if (canStartTour) RoadmateCard(Modifier.widthIn(max = 780.dp), compact = true) {
                                    Text("카펫 둘러보기", style = RoadmateType.cardTitle)
                                    Text("동행부터 차량 상태까지, 7개 화면을 바로 열어 볼 수 있어요.")
                                    GameButton("화면 둘러보기 시작", { touring = true }, primary = true)
                                }
                                demoControls?.invoke()
                            }
                            RoadmatePage.MEMORY -> PostDriveContent(viewModel,
                                openAlbum = { albumPlace = it; page = RoadmatePage.COLLECTION },
                                openMap = showMap,
                                openSeason = { page = RoadmatePage.SEASON }, showHeader = false)
                            RoadmatePage.MAP -> ExplorationContent(viewModel, context,
                                initialPlace = mapPlace, route = expedition, onRouteChange = { expedition = it },
                                openCollection = { albumPlace = it; page = RoadmatePage.COLLECTION },
                                openMemory = { page = RoadmatePage.MEMORY }, openSeason = { page = RoadmatePage.SEASON },
                                onSimulate = { place -> viewModel.simulateVisit(place); returnToMap = true })
                            RoadmatePage.COLLECTION -> CollectionContent(viewModel, activePreview, albumPlace, showMap)
                            RoadmatePage.SETTINGS -> RecordSettingsContent(viewModel)
                            RoadmatePage.HOME, RoadmatePage.SEASON -> Unit
                        }
                    }
                }
                return@Scaffold
            }
            Box(Modifier.fillMaxSize().padding(insets)) {
                Column(Modifier.fillMaxSize().imePadding().padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoadmateMasthead(modeLabel, decorated = full)
                    priorityCare()
                    key(page, canBrowse, full) {
                        val scroll = rememberScrollState()
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(bottom = 24.dp)) {
                            when {
                                page == RoadmatePage.DEMO && full && demoControls != null -> demoControls()
                                !full -> RoadmateCard {
                                    Text(UiCopy.connection(context.connection.status), style = MaterialTheme.typography.titleMedium)
                                    Text(UiCopy.trip(trip.runtime.state, trip.runtime.recoveryRequired), style = MaterialTheme.typography.headlineMedium)
                                    Text("기록과 설정은 정차 상태와 화면 허용을 확인한 뒤 열 수 있어요.",
                                        style = MaterialTheme.typography.bodyMedium)
                                    if (journey.running) {
                                        val returnTo = if (storyOpen) "이야기를 이어가요" else "지도로 돌아가요"
                                        Text("${journey.place?.name.orEmpty()} 방문 시연 중 · 정차 후 $returnTo.",
                                            style = MaterialTheme.typography.bodyMedium)
                                    } else if (parkSimulation != null) {
                                        OutlinedButton(onClick = parkSimulation, modifier = Modifier.heightIn(min = 56.dp)) {
                                            Text("시뮬레이션 정차")
                                        }
                                    }
                                }
                                else -> RoadmateCard {
                                    Text(if (trip.storage.error != null) UiCopy.storageError(requireNotNull(trip.storage.error))
                                        else if (!trip.storage.loaded) "이 기기의 기록을 불러오고 있어요."
                                        else "정차 상태와 화면 사용 가능 여부를 확인하고 있어요.")
                                }
                            }
                        }
                    }
                    if (full && demoControls != null) navigation()
                }
            }
        }
    }
}

@Composable
private fun HomeVehicleCard(context: VehicleContext, demo: Boolean, readWarnings: suspend () -> AdapterResult<List<String>>,
    modifier: Modifier = Modifier) {
    RoadmateCard(modifier, compact = true, tinted = true) {
        val car = artwork("journey/design/care-car.png")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("차량 케어", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium)
                Text("연결된 차량 정보", style = MaterialTheme.typography.titleMedium)
            }
            if (car != null) Image(car, null, Modifier.size(96.dp, 64.dp), contentScale = ContentScale.Fit)
        }
        Text("하향등  " + (context.lowBeam.currentValue?.let { if (it == LowBeamState.ON) "켜짐" else "꺼짐" }
            ?: UiCopy.unavailable(context.lowBeam.availability)), style = MaterialTheme.typography.bodyMedium)
        if (demo) Text("이 화면의 값은 모두 시뮬레이션이에요.", style = MaterialTheme.typography.bodyMedium)
        else {
            Text("누적 주행거리  " + (context.totalOdometerKilometers.currentValue?.let { String.format(Locale.KOREA, "%,.0f km", it) }
                ?: UiCopy.unavailable(context.totalOdometerKilometers.availability)), style = MaterialTheme.typography.bodyMedium)
            Text("앞좌석 왼쪽 공조 설정  " + (context.frontLeftTargetTemperatureCelsius.currentValue?.let(UiCopy::temperature)
                ?: UiCopy.unavailable(context.frontLeftTargetTemperatureCelsius.availability)), style = MaterialTheme.typography.bodyMedium)
            Text("외기 온도 · 차량 센서  "+UiCopy.temperatureReading(context.outsideTemperatureCelsius),style=MaterialTheme.typography.bodyMedium)
            Text("일부 케어 안내는 필요한 차량 정보가 확인되면 이용할 수 있어요.",
                style = MaterialTheme.typography.bodyMedium)
            val scope = rememberCoroutineScope()
            var loading by remember { mutableStateOf(false) }
            var result by remember { mutableStateOf<AdapterResult<List<String>>?>(null) }
            OutlinedButton(enabled = !loading, modifier = Modifier.heightIn(min = 56.dp), onClick = {
                loading = true
                result = null
                scope.launch {
                    try { result = readWarnings() } finally { loading = false }
                }
            }) { Text(if (loading) "확인 중…" else "경고등 조회") }
            when (val response = result) {
                is AdapterResult.Available -> {
                    if (response.value.isEmpty()) Text("이번 조회에서 켜지거나 점멸한 경고등을 받지 못했어요.")
                    else response.value.forEach { Text(it) }
                    Text("조회 시점의 차량 정보예요. 계기판의 안내를 확인하세요.", style = MaterialTheme.typography.bodyMedium)
                }
                is AdapterResult.Unavailable -> Text("경고등 정보를 확인할 수 없어요.")
                null -> Unit
            }
        }
    }
}
