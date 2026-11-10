package io.booup.roadmate

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.booup.roadmate.model.*
import io.booup.roadmate.presentation.DrivingPresentationPolicy
import kotlinx.coroutines.launch
import java.util.Locale

private enum class RoadmatePage(val label: String) {
    HOME("동행"), MAP("탐험 지도"), COLLECTION("엽서 컬렉션"), SEASON("시즌 패스"),
    MEMORY("드라이브 기억"), DEMO("시연 선택"), SETTINGS("설정")
}

/** Demo controls are supplied only by the variant composition root. */
@Composable
fun RoadmateApp(viewModel: RoadmateViewModel, simulationLabel: String? = null,
    demoControls: (@Composable () -> Unit)? = null, parkSimulation: (() -> Unit)? = null) {
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
    var page by remember { mutableStateOf(RoadmatePage.HOME) }
    var homeTrial by remember { mutableStateOf<CompanionPreview?>(null) }
    var returnToMap by remember { mutableStateOf(false) }
    var mapPlace by remember { mutableStateOf<JourneyPlace?>(null) }
    var albumPlace by remember { mutableStateOf<JourneyPlace?>(null) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val clock = remember { AndroidContextClock() }
    val full = DrivingPresentationPolicy().mode(context, clock.now()) == DrivingPresentationMode.FULL_PARKED
    val canBrowse = full && post.contentAllowed
    LaunchedEffect(canBrowse) {
        if (!canBrowse) { page = RoadmatePage.HOME; focus.clearFocus(force = true); keyboard?.hide() }
        else viewModel.restoreJourneyCompanion()
    }
    LaunchedEffect(page, canBrowse) {
        if (page != RoadmatePage.HOME || !canBrowse) homeTrial = null
    }
    LaunchedEffect(journey.running, canBrowse, returnToMap) {
        if (returnToMap && !journey.running && canBrowse) {
            mapPlace = journey.place
            page = RoadmatePage.MAP
            returnToMap = false
        }
    }
    RoadmateTheme {
        Scaffold { insets ->
            if (page == RoadmatePage.SEASON && canBrowse && game != null) {
                JourneyPassContent(viewModel, game, "정차 · ${UiCopy.connection(context.connection.status)}",
                    modifier = Modifier.fillMaxSize().padding(insets),
                    openAlbum = { albumPlace = null; page = RoadmatePage.COLLECTION },
                    openMemory = { page = RoadmatePage.MEMORY }, openHome = { page = RoadmatePage.HOME },
                    tryOnHome = { homeTrial = it; page = RoadmatePage.HOME },
                    priorityCare = {
                        CareContent(care, { viewModel.respond(it) }, { id, revision -> viewModel.displayed(id, revision) },
                            { viewModel.selectCompanion(it) }, showCompanionPicker = false)
                    })
                return@Scaffold
            }
            Column(Modifier.fillMaxSize().padding(insets).imePadding().padding(horizontal = 28.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Text("AI Roadmate", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                    RoadmateBadge(if (previews.isNotEmpty()) "내부 콜라보 예시 · SIMULATED"
                        else simulationLabel ?: "실제 차량 정보 · 읽기 전용", demo = simulationLabel != null)
                }
                if (simulationLabel != null) Text("시뮬레이션으로 서비스 흐름을 체험해요. 실제 차량은 변경하지 않아요.",
                    style = MaterialTheme.typography.bodyMedium)
                if (canBrowse || (full && demoControls != null)) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RoadmatePage.entries.filter { it != RoadmatePage.DEMO || demoControls != null }.forEach { target ->
                            FilterChip(selected = page == target, enabled = canBrowse || target in setOf(RoadmatePage.HOME, RoadmatePage.DEMO),
                                onClick = {
                                    focus.clearFocus(); keyboard?.hide()
                                    if (target == RoadmatePage.MAP) mapPlace = null
                                    if (target == RoadmatePage.COLLECTION) albumPlace = null
                                    page = target
                                },
                                label = { Text(target.label) }, modifier = Modifier.heightIn(min = 56.dp))
                        }
                    }
                }
                CareContent(care, { viewModel.respond(it) }, { id, revision -> viewModel.displayed(id, revision) },
                    { viewModel.selectCompanion(it) }, showCompanionPicker = false)
                key(page, canBrowse, full) {
                    val scroll = rememberScrollState()
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        if (canBrowse && page in setOf(RoadmatePage.MAP, RoadmatePage.COLLECTION, RoadmatePage.SEASON)) {
                            Text("정차 상태 · ${UiCopy.connection(context.connection.status)}", style = MaterialTheme.typography.labelMedium)
                        }
                        when {
                            page == RoadmatePage.DEMO && full && demoControls != null -> demoControls()
                            page == RoadmatePage.MEMORY && canBrowse -> PostDriveContent(viewModel,
                                openAlbum = { albumPlace = it; page = RoadmatePage.COLLECTION },
                                openMap = { mapPlace = it; page = RoadmatePage.MAP }, openSeason = { page = RoadmatePage.SEASON })
                            page == RoadmatePage.MAP && canBrowse -> ExplorationContent(viewModel, context.mode,
                                initialPlace = mapPlace,
                                openCollection = { albumPlace = it; page = RoadmatePage.COLLECTION },
                                openMemory = { page = RoadmatePage.MEMORY }, openSeason = { page = RoadmatePage.SEASON }, onSimulate = { place ->
                                    viewModel.simulateVisit(place)
                                    returnToMap = true
                                })
                            page == RoadmatePage.SEASON && canBrowse -> SeasonContent(viewModel, activePreview ?: previews.firstOrNull())
                            page == RoadmatePage.COLLECTION && canBrowse -> CollectionContent(viewModel, activePreview, albumPlace) {
                                mapPlace = it; page = RoadmatePage.MAP
                            }
                            page == RoadmatePage.SETTINGS && canBrowse -> RecordSettingsContent(viewModel)
                            !full -> RoadmateCard {
                                Text(UiCopy.connection(context.connection.status), style = MaterialTheme.typography.titleMedium)
                                Text(UiCopy.trip(trip.runtime.state, trip.runtime.recoveryRequired), style = MaterialTheme.typography.headlineMedium)
                                Text("기록과 설정은 정차 상태와 화면 허용을 확인한 뒤 열 수 있어요.",
                                    style = MaterialTheme.typography.bodyMedium)
                                if (journey.running) {
                                    Text("${journey.place?.name.orEmpty()} 방문 시연 중 · 정차 후 지도로 돌아가요.",
                                        style = MaterialTheme.typography.bodyMedium)
                                } else if (parkSimulation != null) {
                                    OutlinedButton(onClick = parkSimulation, modifier = Modifier.heightIn(min = 56.dp)) {
                                        Text("시뮬레이션 정차")
                                    }
                                }
                            }
                            else -> {
                                if (canBrowse && gameState != null) RoadmateCard(tinted = true) {
                                    val found = journey.place.takeIf { journey.saved }
                                    val xp = progress.active?.progress?.totalXp ?: 0
                                    Text("오늘의 발견", style = MaterialTheme.typography.titleMedium)
                                    Text(found?.let { "${it.name} 방문 기록 · 시즌 $xp XP" } ?: "모은 엽서 ${post.collection.size}장 · 시즌 $xp XP")
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = { mapPlace = found; page = RoadmatePage.MAP }, modifier = Modifier.heightIn(min = 52.dp)) { Text("탐험 기록") }
                                        OutlinedButton(onClick = { page = RoadmatePage.SEASON }, modifier = Modifier.heightIn(min = 52.dp)) { Text("시즌 보상") }
                                    }
                                }
                                val trial = homeTrial.takeIf { canBrowse }
                                HomeContent(context, trip, trial?.profile ?: care.companion, post, canBrowse, simulationLabel != null,
                                openMemory = {
                                    trip.runtime.completed?.let { record -> viewModel.postDriveAction { it.open(record.tripId) } }
                                    page = RoadmatePage.MEMORY
                                }, openSettings = { page = RoadmatePage.SETTINGS }, readWarnings = viewModel::readParkedWarnings,
                                previews = if (gameState == null) previews else previews.filter { it.profile.id in gameState.companions },
                                activePreview = trial ?: activePreview,
                                endTrial = if (trial == null) null else { { homeTrial = null } }, selectCompanion = { id ->
                                    homeTrial = null
                                    if (gameState == null) viewModel.selectCompanion(id)
                                    else if (id in gameState.companions) viewModel.equipJourneyCompanion(id)
                                    else viewModel.equipDefaultCompanion(id)
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeContent(context: VehicleContext, trip: TripUiState, companionProfile: CompanionProfile, post: PostDriveUiState,
    canBrowse: Boolean, demo: Boolean, openMemory: () -> Unit, openSettings: () -> Unit,
    readWarnings: suspend () -> AdapterResult<List<String>>, previews: List<CompanionPreview>,
    activePreview: CompanionPreview?, endTrial: (() -> Unit)?, selectCompanion: (CompanionId) -> Unit) {
    BoxWithConstraints {
        val wide = maxWidth >= 960.dp
        val companion: @Composable () -> Unit = {
            RoadmateCard(tinted = true) {
                if (canBrowse && previews.isNotEmpty()) {
                    Text("동반자 꾸미기", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (previews.map { it.profile } + CompanionProfiles.all).forEach { profile ->
                            FilterChip(selected = companionProfile.id == profile.id,
                                onClick = { selectCompanion(profile.id) }, label = { Text(profile.displayName) },
                                modifier = Modifier.heightIn(min = 56.dp))
                        }
                    }
                }
                if (activePreview == null) CompanionPortrait(companionProfile.displayName, companionProfile.tone == CompanionTone.WARM,
                    Modifier.fillMaxWidth().heightIn(max = 260.dp))
                else CompanionPreviewPortrait(activePreview, Modifier.fillMaxWidth().height(224.dp), roam = canBrowse)
                Text(companionProfile.displayName, style = MaterialTheme.typography.titleLarge)
                Text("함께 달리고, 잠시 쉬고,\n오늘의 드라이브를 기억해요.", style = MaterialTheme.typography.bodyLarge)
                Text(if (activePreview == null) "오리지널 동반자 · 임시 디자인" else "가상 콜라보 · 내부 시연",
                    style = MaterialTheme.typography.labelMedium)
                if (canBrowse && activePreview?.idleAtlas != null) {
                    Text("캐릭터를 누르면 인사해요.", style = MaterialTheme.typography.labelMedium)
                }
                if (endTrial != null) OutlinedButton(onClick = endTrial, modifier = Modifier.heightIn(min = 56.dp)) {
                    Text("체험 끝내기")
                }
            }
        }
        val status: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(if (trip.runtime.state == TripState.COMPLETED) "오늘의 드라이브,\n어떤 기억으로 남길까요?" else "오늘도 함께,\n당신의 드라이브 메이트",
                    style = MaterialTheme.typography.headlineLarge)
                Text(UiCopy.connection(context.connection.status), style = MaterialTheme.typography.bodyLarge)
                RoadmateCard {
                    Text("지금의 드라이브", style = MaterialTheme.typography.labelLarge)
                    Text(UiCopy.trip(trip.runtime.state, trip.runtime.recoveryRequired), style = MaterialTheme.typography.headlineMedium)
                    Text(when (trip.runtime.state) {
                        TripState.START_CANDIDATE -> "이동이 이어지는지 확인하고 있어요."
                        TripState.IN_PROGRESS -> "안전하게 다녀오세요. 기록 정리는 정차 후에 할 수 있어요."
                        TripState.STOP_CANDIDATE -> "잠깐의 정차인지 확인하고 있어요. 이어 달리면 같은 드라이브로 남아요."
                        TripState.COMPLETED -> "확인된 운행 기록으로 기억 카드를 준비했어요."
                        else -> "이동이 확인되면 드라이브가 시작돼요."
                    }, style = MaterialTheme.typography.bodyMedium)
                    if (canBrowse && trip.runtime.state == TripState.COMPLETED) {
                        Button(onClick = openMemory, modifier = Modifier.heightIn(min = 60.dp)) { Text("이번 드라이브 기억 열기") }
                    }
                }
                if (canBrowse && !post.historyEnabled) RoadmateCard {
                    Text("기억은, 원할 때만", style = MaterialTheme.typography.titleMedium)
                    Text("기록 저장은 꺼져 있어요. 켜면 완료된 드라이브와 직접 저장한 회고를 이 기기에 보관해요.",
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = openSettings, modifier = Modifier.heightIn(min = 56.dp)) { Text("기록 저장 설정") }
                }
            }
        }
        if (wide) Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Box(Modifier.weight(.36f)) { companion() }
            Box(Modifier.weight(.64f)) { status() }
        } else Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (previews.isNotEmpty()) { companion(); status() } else { status(); companion() }
        }
    }
    if (!canBrowse) RoadmateCard {
        Text("기억과 시즌은 정차 후에", style = MaterialTheme.typography.titleMedium)
        Text(if (trip.storage.error != null) "저장 정보를 읽지 못했어요. 기존 기록을 지우지 않았어요."
            else if (!trip.storage.loaded) "이 기기의 기록을 불러오고 있어요."
            else "정차 상태와 화면 사용 가능 여부를 확인하면 기록·회고·시즌을 열 수 있어요.", style = MaterialTheme.typography.bodyMedium)
    }
    if (canBrowse) RoadmateCard {
        Text("연결된 차량 정보", style = MaterialTheme.typography.titleMedium)
        Text("하향등  " + (context.lowBeam.currentValue?.let { if (it == LowBeamState.ON) "켜짐" else "꺼짐" }
            ?: UiCopy.unavailable(context.lowBeam.availability)), style = MaterialTheme.typography.bodyMedium)
        if (demo) Text("이 화면의 값은 모두 시뮬레이션이에요.", style = MaterialTheme.typography.bodyMedium)
        else {
            Text("누적 주행거리  " + (context.totalOdometerKilometers.currentValue?.let { String.format(Locale.KOREA, "%,.0f km", it) }
                ?: UiCopy.unavailable(context.totalOdometerKilometers.availability)), style = MaterialTheme.typography.bodyMedium)
            Text("앞좌석 왼쪽 공조 설정  " + (context.frontLeftTargetTemperatureCelsius.currentValue?.let(UiCopy::temperature)
                ?: UiCopy.unavailable(context.frontLeftTargetTemperatureCelsius.availability)), style = MaterialTheme.typography.bodyMedium)
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
