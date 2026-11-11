package io.booup.roadmate

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.booup.roadmate.model.*
import io.booup.roadmate.trip.TripEvidenceMapper

@Composable
fun ExplorationContent(viewModel: RoadmateViewModel, context: VehicleContext, initialPlace: JourneyPlace?,
    openCollection: (JourneyPlace?) -> Unit, openMemory: () -> Unit, openSeason: () -> Unit,
    onSimulate: (JourneyPlace) -> Unit) {
    val trip by viewModel.tripUiState.collectAsStateWithLifecycle()
    val journey by viewModel.journey.collectAsStateWithLifecycle()
    val progress by viewModel.progressUiState.collectAsStateWithLifecycle()
    val game = viewModel.journeyGame
    val gameState = game?.state?.collectAsStateWithLifecycle()?.value
    val demo = context.mode == RuntimeMode.DEMO
    val source = if (demo) RegionEvidenceSource.DEMO else RegionEvidenceSource.VERIFIED_CONTEXT
    val visits = trip.storage.visits.filter { it.source == source }
    val visited = visits.map { it.regionId }.toSet()
    val atlas = artwork("journey/postcards.png")
    var route by remember(initialPlace) { mutableStateOf(Expedition.containing(initialPlace?.id) ?: Expedition.SEOUL) }
    val next = route.next(visited)
    val visible = route.revealed(visited)
    val count = route.places.count { it.id in visited }
    var selectedId by remember(route, initialPlace) { mutableStateOf(initialPlace?.id) }
    var originId by remember(route) { mutableStateOf<RegionId?>(null) }
    var destinationId by remember(route) { mutableStateOf<RegionId?>(null) }
    val selected = visible.firstOrNull { it.id == selectedId } ?: next ?: visible.last()
    val origin = visible.firstOrNull { it.id == originId }
    val destination = visible.firstOrNull { it.id == destinationId }
    val connection = if (origin != null && destination != null) route.connection(origin.id, destination.id, visited) else emptyList()
    val recent = visits.filter { visit -> visible.any { it.id == visit.regionId } }.maxByOrNull { it.firstVisitedAtEpochMillis }
    val clock = remember { AndroidContextClock() }
    val location = TripEvidenceMapper().map(context, clock.now()).location
    val current = verifiedExpeditionPoint(location).takeIf { !demo && it in visible }
    val landmark = gameState?.landmarks?.firstOrNull { Expedition.containing(it.pointId) == route && it.pointId in visited }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("안개 너머, 우리의 한국", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        progress.active?.let { RoadmateBadge("시즌 ${it.progress.totalXp} XP") }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 720.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Expedition.entries.chunked(columns).forEach { regions ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    regions.forEach { region ->
                        val completed = region.places.count { it.id in visited }
                        RegionCard(region, atlas, completed, route == region, Modifier.weight(1f)) { route = region }
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Color(0xFF283B58))) {
        ExpeditionLandscape(route, atlas, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
            Color(0xE522304B), Color(0x48283E54), Color(0xB323304B)))))
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${route.label} 탐험", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(route.theme, Modifier.weight(1f), color = Color(0xFFFFDC9A), style = MaterialTheme.typography.labelMedium)
                Text("$count / ${route.places.size} 방문", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val map: @Composable (Modifier) -> Unit = { modifier ->
                    ExpeditionMap(route, visible, visited, selected.id, origin?.id, destination?.id, connection,
                        landmark?.pointId, modifier.height(270.dp)) { selectedId = it.id }
                }
                val detail: @Composable (Modifier) -> Unit = { modifier ->
                    RoadmateCard(modifier, compact = true) {
                        val reached = selected.id in visited
                        RoadmateBadge(if (reached) "✓ 방문 완료" else "✦ 다음 목표")
                        Text(selected.name, style = MaterialTheme.typography.titleLarge)
                        if (!reached) Text("첫 방문 보너스 +$EXPEDITION_FIRST_VISIT_XP XP",
                            style = MaterialTheme.typography.bodySmall)
                        if (demo && viewModel.canSimulateVisit) {
                            Button(onClick = { onSimulate(selected) }, enabled = !journey.running,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
                                Text(if (reached) "다시 방문 시연" else "방문 시연")
                            }
                        } else if (!reached) Text("확인된 방문 기록이 들어오면 다음 별이 열려요.", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { originId = selected.id }, modifier = Modifier.heightIn(min = 52.dp)) { Text("출발지로") }
                            OutlinedButton(onClick = { destinationId = selected.id }, modifier = Modifier.heightIn(min = 52.dp)) { Text("목적지로") }
                        }
                    }
                }
                if (maxWidth >= 750.dp) Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    map(Modifier.weight(1.6f)); detail(Modifier.weight(1f))
                } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    map(Modifier.fillMaxWidth()); detail(Modifier.fillMaxWidth())
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                visible.forEach { place ->
                    FilterChip(selected.id == place.id, { selectedId = place.id }, { Text(place.name) }, modifier = Modifier.heightIn(min = 48.dp),
                        colors = FilterChipDefaults.filterChipColors(containerColor = Color(0xEFFFFAF3), labelColor = Color(0xFF273848)))
                }
            }
        }
    }
    RoadmateCard {
        Text("별자리 경로", style = MaterialTheme.typography.titleMedium)
        Text(if (connection.isNotEmpty()) connection.joinToString(" → ") { it.name }
            else "${origin?.name ?: "출발지 선택"} → ${destination?.name ?: "목적지 선택"}")
        Text(when {
            connection.isNotEmpty() -> "지도 위 금색 선으로 탐험 순서를 확인하세요."
            origin?.id != null && origin.id == destination?.id -> "서로 다른 두 지점을 선택해 주세요."
            origin != null && destination != null -> "사이에 안개가 남아 있어요. 다음 목표를 방문하면 경로가 이어져요."
            else -> "공개된 원을 눌러 출발지와 목적지를 정하세요."
        }, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (current != null) OutlinedButton({ originId = current.id }, modifier = Modifier.heightIn(min = 52.dp)) { Text("현위치를 출발지로") }
            else if (recent != null) OutlinedButton({ originId = recent.regionId }, modifier = Modifier.heightIn(min = 52.dp)) { Text("최근 방문을 출발지로") }
            if (origin != null || destination != null) TextButton({ originId = null; destinationId = null },
                modifier = Modifier.heightIn(min = 52.dp)) { Text("경로 지우기") }
        }
    }
    val found = journey.place?.takeIf { Expedition.containing(it.id) == route }
    if (found != null && !journey.running) {
        if (journey.saved) {
            val entries = journey.tripId?.let { id -> trip.storage.progression.ledger.filter { it.sourceTripId == id } }.orEmpty()
            val driveXp = entries.filter { it.sourceType == ProgressEventType.TRIP_COMPLETED }.sumOf { it.awardedXp }
            val visitXp = entries.filter { it.sourceType == ProgressEventType.FIRST_REGION_VISIT }.sumOf { it.awardedXp }
            val postcardXp = entries.filter { it.sourceType == ProgressEventType.POSTCARD_ACQUIRED }.sumOf { it.awardedXp }
            RoadmateCard(tinted = true) {
                Text("${found.name} 방문 기록", style = MaterialTheme.typography.titleMedium)
                Text("주행 +$driveXp XP · 첫 방문 +$visitXp XP" + if (postcardXp > 0) " · 엽서 +$postcardXp XP" else "")
                if (entries.any { it.reason in setOf(XpReason.DAILY_CAP, XpReason.TRIP_CAP) }) {
                    Text("일부 XP에 시즌 상한이 적용됐어요.", style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ openCollection(found) }, modifier = Modifier.heightIn(min = 52.dp)) { Text("엽서 펼치기") }
                    TextButton(openMemory, modifier = Modifier.heightIn(min = 52.dp)) { Text("드라이브 회고") }
                    TextButton(openSeason, modifier = Modifier.heightIn(min = 52.dp)) { Text("시즌 보상") }
                }
            }
        } else RoadmateCard { Text("방문 기록을 저장하지 못했어요. 정차 상태를 확인해 주세요.") }
    }
    ExpeditionLandmarks(route, count, visits, landmark, demo && game != null, viewModel)
}

@Composable
private fun ExpeditionLandscape(route: Expedition, atlas: ImageBitmap?, modifier: Modifier) {
    RoadmateLandscape("map-${route.asset}.png", route.postcardIndex, modifier, atlas)
}

@Composable
private fun RegionCard(route: Expedition, atlas: ImageBitmap?, count: Int, selected: Boolean, modifier: Modifier, select: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Surface(modifier.height(76.dp), shape = shape,
        border = androidx.compose.foundation.BorderStroke(if (selected) 3.dp else 1.dp,
            if (selected) Color(0xFFECAD60) else Color(0xFFEEE0D3))) {
        Box(Modifier.fillMaxSize().clickable(role = Role.Button, onClick = select)) {
            ExpeditionLandscape(route, atlas, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xED233246)))))
            Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(route.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp)
                Text("$count / ${route.places.size} 방문", color = Color(0xFFFFEAC9), fontSize = 14.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun ExpeditionLandmarks(route: Expedition, count: Int, visits: List<RegionVisitRecord>,
    landmark: ExpeditionLandmark?, demo: Boolean, viewModel: RoadmateViewModel) {
    val rank = if (demo) route.demoRank(visits) else null
    var creating by remember(route) { mutableStateOf(false) }
    var name by remember(route) { mutableStateOf("") }
    var point by remember(route) { mutableStateOf(route.places.first()) }
    var visitor by remember(route) { mutableIntStateOf(1) }
    var saving by remember(route) { mutableStateOf(false) }
    var failed by remember(route) { mutableStateOf(false) }
    val art = artwork(if (landmark != null) "journey/design/landmark-placeholder.png" else "journey/design/landmark-creator-ticket.png")
    RoadmateCard(tinted = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if (art != null) Image(art, null, Modifier.size(68.dp), contentScale = ContentScale.Fit)
            else Text("✦", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.headlineMedium)
            Column(Modifier.weight(1f)) {
                Text("여행자의 랜드마크", style = MaterialTheme.typography.titleLarge)
                Text(if (demo) "${route.label} · 순위와 반응 시연" else "${route.label} · 지역 완주 보상",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        when {
            landmark != null -> {
                Text(landmark.name, style = MaterialTheme.typography.titleLarge)
                Text(journeyPlace(landmark.pointId)?.name.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                Text("좋아요 ${landmark.likes.size} · 방문자 ${landmark.visitors.size}명 · ${landmark.points} RP")
                Text("시연 방문자 1명당 5 RP가 쌓여요.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..3).forEach { number ->
                        FilterChip(visitor == number, { visitor = number }, { Text("시연 방문자 $number") }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({
                        val person = visitor
                        viewModel.journeyAction { demoLandmarkFeedback(landmark.pointId, person, like = false) }
                    },
                        enabled = visitor !in landmark.visitors, modifier = Modifier.heightIn(min = 52.dp)) { Text("방문 시연") }
                    OutlinedButton({
                        val person = visitor
                        viewModel.journeyAction { demoLandmarkFeedback(landmark.pointId, person, like = true) }
                    },
                        enabled = visitor in landmark.visitors && visitor !in landmark.likes,
                        modifier = Modifier.heightIn(min = 52.dp)) { Text("좋아요 시연") }
                }
            }
            count < route.places.size -> Text("${route.places.size}개 지점을 모두 방문한 뒤 지역 완주 기록이 열려요.")
            rank != null -> {
                Text("지역 완주 · 시연 순위 ${rank}위", style = MaterialTheme.typography.titleMedium)
                Text(if (rank <= 3) "완주 상위 3명에게 생성권 한 장이 열려요." else "이 시연에서는 완주 상위 3명에게 생성권을 줘요.")
                if (rank <= 3) Button({ creating = true; failed = false }, modifier = Modifier.heightIn(min = 54.dp)) {
                    Text("랜드마크 이름 짓기")
                }
            }
            else -> Text("지역을 완주했어요. 생성권은 공유 순위가 연결되면 확인할 수 있어요.")
        }
    }
    if (creating) AlertDialog(onDismissRequest = { if (!saving) creating = false },
        title = { Text("랜드마크 이름 짓기") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("완주한 장소 중 한 곳을 고르세요.")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    route.places.forEach { place ->
                        FilterChip(point == place, { point = place }, { Text(place.name) }, modifier = Modifier.heightIn(min = 48.dp))
                    }
                }
                OutlinedTextField(name, { if (it.length <= 24) { name = it; failed = false } }, label = { Text("이름 · 2~24자") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (failed) Text("이름이나 저장 상태를 확인해 주세요.", color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = {
            Button({
                val pointId = point.id
                val title = name
                saving = true
                viewModel.journeyAction {
                    try {
                        if (createLandmark(pointId, title)) creating = false else failed = true
                    } finally { saving = false }
                }
            }, enabled = !saving && name.trim().length in 2..24) { Text("등록하기") }
        }, dismissButton = { TextButton({ creating = false }, enabled = !saving) { Text("취소") } })
}
