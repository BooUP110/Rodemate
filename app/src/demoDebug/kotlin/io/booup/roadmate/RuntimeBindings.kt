package io.booup.roadmate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.booup.roadmate.context.VehicleContextStore
import io.booup.roadmate.demo.DemoScenarios
import io.booup.roadmate.demo.DemoScenarioId
import io.booup.roadmate.demo.DemoVehicleAdapter
import io.booup.roadmate.demo.DemoJourney
import io.booup.roadmate.demo.journeySeason
import io.booup.roadmate.demo.CareDemoScenarioId
import io.booup.roadmate.demo.InteractionDemoScenarioId
import io.booup.roadmate.demo.DemoLaunchInput
import io.booup.roadmate.demo.DemoSimulationFeedback
import io.booup.roadmate.care.DeterministicCareEngine
import io.booup.roadmate.model.ContextClock
import io.booup.roadmate.model.RegionEvidenceSource
import io.booup.roadmate.model.RuntimeMode
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.util.UUID
import io.booup.roadmate.storage.FileTripRepository
import io.booup.roadmate.trip.TripCoordinator
import io.booup.roadmate.trip.TripIdSource
import io.booup.roadmate.model.TripConfig
import io.booup.roadmate.demo.TripDemoScenarios
import io.booup.roadmate.demo.TripDemoScenarioId
import io.booup.roadmate.demo.DemoTripController
import io.booup.roadmate.postdrive.PostDriveCoordinator
import io.booup.roadmate.demo.PostDriveDemoScenarios
import io.booup.roadmate.demo.PostDriveDemoController
import io.booup.roadmate.demo.PostDriveDemoId
import io.booup.roadmate.demo.ProgressDemoId
import io.booup.roadmate.demo.ProgressDemoScenarios
import io.booup.roadmate.demo.ProgressDemoController
import io.booup.roadmate.progress.ProgressCoordinator
import io.booup.roadmate.progress.SafeProgressEngine
import io.booup.roadmate.postdrive.RegionEvidenceProvider

object RuntimeBindings {
    val runtimePermissions: List<String> = emptyList()
    fun create(context: android.content.Context, scope: CoroutineScope, clock: ContextClock, storageRoot: File? = null): RuntimeBinding =
        create(scope, clock, storageRoot, io.booup.roadmate.storage.AesGcmEnvelope(
            io.booup.roadmate.security.AndroidStoreKeys("roadmate.demo.aggregate.v1"), RuntimeMode.DEMO),
            localCompanionPreviews(context.assets), context)
    fun create(scope: CoroutineScope, clock: ContextClock, storageRoot: File? = null,
        encryption: io.booup.roadmate.storage.StorageEncryption? = null,
        previews: List<CompanionPreview> = emptyList(), appContext: android.content.Context? = null,
        tourStorage: FileTripRepository? = null): RuntimeBinding {
        val adapter = DemoVehicleAdapter(clock).apply { select(DemoScenarioId.A_NORMAL) }
        val repository = VehicleContextStore(adapter, RuntimeMode.DEMO, clock, scope)
        val storage = tourStorage ?: storageRoot?.let { FileTripRepository(File(it, "roadmate-demo"), RuntimeMode.DEMO,
            progressEngine = SafeProgressEngine(explorationRewards = true),
            encryption = requireNotNull(encryption)) }
        val trips = storage?.let { TripCoordinator(it,
            RuntimeMode.DEMO, clock, TripIdSource { UUID.randomUUID().toString() }, TripConfig(TripDemoScenarios.START_MILLIS, TripDemoScenarios.STOP_MILLIS)) }
        val controller = trips?.let { DemoTripController(adapter, it, clock, scope) }
        val journey = trips?.let { DemoJourney(adapter, it) }
        val post = trips?.let { PostDriveCoordinator(it, requireNotNull(storage), RuntimeMode.DEMO, clock,
            { repository.context.value }, RegionEvidenceProvider { trip -> journey?.evidence(trip) ?: ProgressDemoScenarios.region(adapter.progressSelection.value,trip)
                ?: PostDriveDemoScenarios.region(adapter.postDriveSelection.value,trip) }) }
        val postController = post?.let { PostDriveDemoController(adapter, requireNotNull(trips), it, requireNotNull(storage), scope) }
        val progress=post?.let { ProgressCoordinator(requireNotNull(storage),RuntimeMode.DEMO,clock,{it.controlsAllowed()},
            initialSeasons = listOf(journeySeason(previews.firstOrNull())),
            catalog = if (previews.isEmpty() && tourStorage == null) ProgressDemoScenarios.catalog else null, seedOnlyWhenEmpty = true) }
        // Contest demo: the Chiikawa trip companions start unlocked whenever their local art is installed.
        val starters = JourneyPass.TRAVEL.companionIds().filter { id -> previews.any { it.profile.id == id } }.toSet()
        val game = when {
            tourStorage != null && progress != null -> JourneyGame(
                storage = { tourStorage.state.value }, progress = { progress.uiState.value },
                controlsAllowed = requireNotNull(post)::controlsAllowed, now = { clock.now().epochMillis },
                initial = JourneyGameState(),
                persist = { true }, passes = if (previews.isEmpty()) listOf(JourneyPass.COAST, JourneyPass.STARS) else JourneyPass.entries,
                starterCompanions = starters,
            )
            journey != null && appContext != null && progress != null -> localJourneyGame(appContext, progress, post, clock, starters)
            else -> null
        }
        val progressController=progress?.let { ProgressDemoController(adapter,requireNotNull(trips),requireNotNull(storage),storage,requireNotNull(post),it,clock,scope) }
        val input = DemoLaunchInput(adapter, repository, clock, scope, controller, postController,progressController)
        return RuntimeBinding(
            repository,
            developerPanel = { DemoScenarioSelector(adapter, input) },
            careEngine = DeterministicCareEngine(clock),
            clock = clock,
            trips = trips,
            postDrive = post,
            comfort=storage?.let { io.booup.roadmate.local.ComfortCoordinator(it,RuntimeMode.DEMO,clock,{repository.context.value}) },
            progress=progress,
            simulationFeedback = DemoSimulationFeedback(adapter),
            onLaunch = { intent -> input.fromHost(intent.getStringExtra("roadmate.demo.scenario")) },
            parkSimulation = { adapter.select(DemoScenarioId.A_NORMAL) },
            companionPreviews = previews,
            simulateVisit = if (journey != null && post != null) visit@{ requested ->
                val place = journeyPlace(requested.id) ?: return@visit false
                val route = Expedition.containing(place.id) ?: return@visit false
                val visits = trips.repository.state.value.visits.filter { it.source == RegionEvidenceSource.DEMO }
                    .map { it.regionId }.toSet()
                if (place.id != route.region.id && route.revealed(visits).none { it.id == place.id }) return@visit false
                val repeat = place.id in visits && (place.id != route.region.id || game?.state?.value?.equipped != null)
                journey.visit(place, post, repeatVisit = repeat).also { saved ->
                    if (saved && game != null) post.uiState.value.session.tripId?.let { game.remember(place, it) }
                }
            } else null,
            journeyGame = game,
        )
    }
}

@Composable
private fun DemoScenarioSelector(adapter: DemoVehicleAdapter, input: DemoLaunchInput) {
    val selected by adapter.selection.collectAsStateWithLifecycle()
    val careSelected by adapter.careSelection.collectAsStateWithLifecycle()
    val interactionSelected by adapter.interactionSelection.collectAsStateWithLifecycle()
    var group by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("케어") }
    var advanced by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(18.dp)) {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf("케어", "트립", "기억", "시즌").forEach { title ->
                GameButton(title, { group = title }, primary = group == title)
            }
        }
        RoadmateCard(Modifier.widthIn(max = 780.dp)) {
            Text(when (group) {
                "케어" -> "작은 변화를 알아차리는 케어"
                "트립" -> "출발부터 정차 후까지"
                "기억" -> "오늘의 드라이브를 한 장에"
                else -> "안전한 습관을 꾸미는 시즌"
            }, style = RoadmateType.heading)
            Text("모든 차량 값·지역·이용 상태는 시연용이에요. 실제 차량을 읽거나 제어하지 않아요.",
                style = RoadmateType.control)
            Text(if (group == "케어") "주행 시연으로 메뉴가 잠기면 ‘시뮬레이션 정차’를 눌러 다음 시연을 선택하세요."
                else "선택하면 Demo 기록·컬렉션·시즌이 초기화되고 시연용 저장 동의를 사용해요. 실제 사용자 동의가 아니에요.")
            val choices = when (group) {
                "케어" -> listOf("하향등 P1 안내" to "I1_P1_LIGHT", "공조 제안과 수락" to "I2_HVAC_ACCEPT",
                    "휴식 권장" to "C4_CONTINUOUS_DRIVE", "차량 경고 안내" to "C5_IMMEDIATE_WARNING",
                    "동반자와 대화" to "I4_CHARACTER_SWITCH")
                "트립" -> listOf("출발 · 정차 · 완료" to "T1_NORMAL_TRIP", "잠깐 정차 후 다시 출발" to "T2_TRAFFIC_STOP",
                    "정보가 부족한 드라이브" to "T12_PARTIAL_DATA")
                "기억" -> listOf("기억 카드" to "P5_1_BASIC_CARD", "선택 회고" to "P5_4_LOCAL_REFLECTION",
                    "첫 지역 엽서" to "P5_7_FIRST_REGION")
                else -> listOf("첫 드라이브와 시즌" to "S1_FIRST_TRIP", "무료 보상 받기" to "S7_FREE_CLAIM",
                    "시연용 추가 보상" to "S10_SIMULATED_PREMIUM")
            }
            choices.forEach { (title, id) ->
                GameButton(title, { input.fromUi(id) }, Modifier.fillMaxWidth())
            }
        }
        GameButton(if (advanced) "추가 시나리오 접기" else "검증용 시나리오 더 보기", { advanced = !advanced })
        if (advanced) RoadmateCard {
            Text("검증용 선택 · 기존 시나리오 ID", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text("현재 " + (careSelected?.name ?: interactionSelected?.name ?: selected?.name ?: "선택 대기"))
            DemoScenarios.all.forEach { scenario -> TextButton(onClick = { input.fromUi(scenario.id.name) }) { Text(scenario.title) } }
            CareDemoScenarioId.entries.forEach { scenario -> TextButton(onClick = { input.fromUi(scenario.name) }) { Text(scenario.name) } }
            InteractionDemoScenarioId.entries.forEach { scenario -> TextButton(onClick = { input.fromUi(scenario.name) }) { Text(scenario.name) } }
            TripDemoScenarioId.entries.forEach { scenario -> TextButton(onClick = { input.fromUi(scenario.name) }) { Text(scenario.name) } }
            PostDriveDemoId.entries.forEach { scenario -> TextButton(onClick = { input.fromUi(scenario.name) }) { Text(scenario.name) } }
            ProgressDemoId.entries.forEach { scenario -> TextButton(onClick = { input.fromUi(scenario.name) }) { Text(scenario.name) } }
        }
    }
}
