package io.booup.roadmate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.booup.roadmate.model.RegionEvidenceSource
import io.booup.roadmate.model.RegionId
import io.booup.roadmate.model.RuntimeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

private data class District(val id: RegionId, val rings: List<FloatArray>)
private data class MapGeometry(val districts: List<District>, val bounds: Rect, val provinces: Map<String, List<RegionId>>) {
    fun project(point: Offset, canvas: Size, inset: Float): Offset {
        val scale = minOf((canvas.width - inset * 2) / bounds.width, (canvas.height - inset * 2) / bounds.height)
        return (point - bounds.center) * scale + Offset(canvas.width / 2, canvas.height / 2)
    }
}

private fun readDistricts(json: String, provinceJson: String): MapGeometry {
    val source = JSONArray(json)
    var west = Float.POSITIVE_INFINITY; var north = Float.POSITIVE_INFINITY
    var east = Float.NEGATIVE_INFINITY; var south = Float.NEGATIVE_INFINITY
    val districts = List(source.length()) { index ->
        val entry = source.getJSONArray(index)
        val rings = entry.getJSONArray(1)
        District(RegionId(entry.getString(0)), List(rings.length()) { ringIndex ->
            val values = rings.getJSONArray(ringIndex)
            FloatArray(values.length()) { axis ->
                values.getInt(axis).toFloat().also { value ->
                    if (axis % 2 == 0) { west = minOf(west, value); east = maxOf(east, value) }
                    else { north = minOf(north, value); south = maxOf(south, value) }
                }
            }
        })
    }
    val provinces = JSONArray(provinceJson)
    val grouped = (0 until provinces.length()).associate { index ->
        val province = provinces.getJSONArray(index)
        val ids = province.getJSONArray(1)
        province.getString(0) to List(ids.length()) { RegionId(ids.getString(it)) }
    }
    return MapGeometry(districts, Rect(west, north, east, south), grouped)
}

@Composable
fun ExplorationContent(viewModel: RoadmateViewModel, mode: RuntimeMode, initialPlace: JourneyPlace?,
    openCollection: (JourneyPlace?) -> Unit, openMemory: () -> Unit, openSeason: () -> Unit, onSimulate: (JourneyPlace) -> Unit) {
    val trip by viewModel.tripUiState.collectAsStateWithLifecycle()
    val journey by viewModel.journey.collectAsStateWithLifecycle()
    val progress by viewModel.progressUiState.collectAsStateWithLifecycle()
    val game = viewModel.journeyGame
    val gameState = game?.state?.collectAsStateWithLifecycle()?.value
    val expected = if (mode == RuntimeMode.DEMO) RegionEvidenceSource.DEMO else RegionEvidenceSource.VERIFIED_CONTEXT
    val visits = trip.storage.visits.filter { it.source == expected }.map { it.regionId }.toSet()
    val assets = LocalContext.current.assets
    var failed by remember { mutableStateOf(false) }
    val map by produceState<MapGeometry?>(null, assets) {
        val result = withContext(Dispatchers.IO) {
            runCatching { readDistricts(
                assets.open("journey/korea-districts.json").bufferedReader().use { it.readText() },
                assets.open("journey/korea-provinces.json").bufferedReader().use { it.readText() },
            ) }
        }
        value = result.getOrNull()
        failed = result.isFailure
    }
    var selected by remember(initialPlace) { mutableStateOf(initialPlace ?: journeyPlaces.first()) }
    val discovered = map?.districts?.count { it.id in visits } ?: 0
    Text("안개 너머, 우리의 한국", style = MaterialTheme.typography.headlineMedium)
    Text("도착한 곳의 안개가 걷히고, 첫 방문 엽서가 한 장씩 모여요.", style = MaterialTheme.typography.bodyMedium)
    if (map != null) Text("탐험한 구역 $discovered / ${map!!.districts.size}", style = MaterialTheme.typography.titleMedium)
    if (gameState != null && journey.saved && journey.place != null) {
        val found = requireNotNull(journey.place)
        val xp = trip.storage.progression.ledger.filter { it.sourceTripId == journey.tripId && it.seasonId == JOURNEY_SEASON_ID }.sumOf { it.awardedXp }
        val isFirst = trip.storage.visits.any { it.regionId == found.id && it.sourceTripId == journey.tripId }
        RoadmateCard(tinted = true) {
            RoadmateBadge("정차 후 발견 리포트", demo = true)
            Text(if (isFirst) "${found.name}의 안개가 걷혔어요" else "${found.name} 드라이브를 기록했어요", style = MaterialTheme.typography.titleLarge)
            Text(if (isFirst) "첫 방문 엽서 · ${found.title}" else "엽서 컬렉션에 이미 있는 지역이에요.")
            Text("이번 드라이브 +$xp XP · 시즌 누적 ${progress.active?.progress?.totalXp ?: 0} XP")
            if (gameState.memories.any { it.tripId == journey.tripId }) Text("동반자의 첫 여행 엽서가 내 패스함에 열렸어요.")
            Button(onClick = { openCollection(found) }, modifier = Modifier.heightIn(min = 52.dp)) { Text("여행 엽서 펼치기") }
            OutlinedButton(onClick = openMemory, modifier = Modifier.heightIn(min = 52.dp)) { Text("드라이브 회고 열기") }
            OutlinedButton(onClick = openSeason, modifier = Modifier.heightIn(min = 52.dp)) { Text("시즌 미션과 보상 보기") }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val chart: @Composable (Modifier) -> Unit = { modifier ->
            RoadmateCard(modifier) {
                val geometry = map
                if (geometry == null) Text(if (failed) "지도 파일을 읽지 못했어요." else "한국 지도를 펼치는 중이에요.")
                else KoreaFogMap(geometry, visits, selected, game != null) { selected = it }
                Text("● 발견한 구역     ○ 안개 구역", style = MaterialTheme.typography.labelMedium)
                if (game != null) Text("노란 선 · 방문 기록에 연결한 합성 시연 경로", style = MaterialTheme.typography.labelMedium)
                Text("경계 데이터 geoBoundaries / citypopulation.de · 2020 · CC BY 3.0", style = MaterialTheme.typography.labelMedium)
            }
        }
        val destinations: @Composable (Modifier) -> Unit = { modifier ->
            RoadmateCard(modifier, tinted = true) {
                Text("지역 기록", style = MaterialTheme.typography.titleLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    journeyPlaces.take(2).forEach { place ->
                        FilterChip(selected == place, { selected = place }, { Text(place.name) },
                            modifier = Modifier.weight(1f).heightIn(min = 54.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    journeyPlaces.drop(2).forEach { place ->
                        FilterChip(selected == place, { selected = place }, { Text(place.name) },
                            modifier = Modifier.weight(1f).heightIn(min = 54.dp))
                    }
                }
                val visited = selected.id in visits
                RoadmateBadge(if (visited) "발견 완료" else "안개 속 지역")
                Text(if (visited) selected.title else selected.name, style = MaterialTheme.typography.titleMedium)
                Text(if (visited) selected.note else "확인된 첫 방문이 기록되면 엽서가 열려요.")
                if (visited) Button(onClick = { openCollection(selected) }, modifier = Modifier.heightIn(min = 56.dp)) { Text("엽서 컬렉션 열기") }
                if (viewModel.canSimulateVisit && (!visited || gameState?.equipped != null)) {
                    Button(onClick = { onSimulate(selected) }, enabled = !journey.running, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text("${selected.name} 방문 시연")
                    }
                    Text("합성 트립과 시연 기록을 이 앱에 저장해요.", style = MaterialTheme.typography.bodyMedium)
                } else if (!visited) Text("확인된 차량 위치 기록을 기다리고 있어요.")
                if (journey.place == selected && !journey.running) {
                    Text(if (journey.saved) "첫 방문 엽서를 컬렉션에 넣었어요." else "방문 기록을 만들지 못했어요. 정차 상태에서 다시 시연해 주세요.")
                }
            }
        }
        if (maxWidth >= 950.dp) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            chart(Modifier.weight(1.4f)); destinations(Modifier.weight(1f))
        } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { chart(Modifier); destinations(Modifier) }
    }
    map?.let { geometry -> RoadmateCard {
        var expanded by remember { mutableStateOf(false) }
        Text("광역별 탐험 진행도", style = MaterialTheme.typography.titleLarge)
        val regions = if (expanded) geometry.provinces else geometry.provinces.filterValues { ids ->
            ids.any { it in visits } || selected.id in ids
        }
        regions.forEach { (name, ids) -> Text("$name · ${ids.count { it in visits }} / ${ids.size} 구역") }
        OutlinedButton(onClick = { expanded = !expanded }, modifier = Modifier.heightIn(min = 52.dp)) {
            Text(if (expanded) "방문한 광역만 보기" else "17개 광역 전체 보기")
        }
        Text("수집률은 앱에 포함된 2020년 시·군·구 경계 기준이에요.", style = MaterialTheme.typography.labelMedium)
    } }
}

@Composable
private fun KoreaFogMap(geometry: MapGeometry, visits: Set<RegionId>, selected: JourneyPlace, demoRoutes: Boolean,
    onSelect: (JourneyPlace) -> Unit) {
    val select by rememberUpdatedState(onSelect)
    val sea = Color(0xFFD9EAF0)
    val discovered = Color(0xFF69B58D)
    val ink = Color(0xFF235D4B)
    Canvas(Modifier.fillMaxWidth().height(392.dp).clip(RoundedCornerShape(22.dp)).background(sea)
        .semantics { contentDescription = "한국 탐험 지도 · 방문 구역만 표시" }
        .pointerInput(geometry) {
            detectTapGestures { tap ->
                val canvas = Size(size.width.toFloat(), size.height.toFloat())
                journeyPlaces.minByOrNull { (geometry.project(it.mapPoint(), canvas, 24.dp.toPx()) - tap).getDistance() }
                    ?.takeIf { (geometry.project(it.mapPoint(), canvas, 24.dp.toPx()) - tap).getDistance() <= 30.dp.toPx() }
                    ?.let { select(it) }
            }
        }.drawWithCache {
            val inset = 24.dp.toPx()
            val shapes = geometry.districts.map { district ->
                val shape = Path().apply {
                    fillType = PathFillType.EvenOdd
                    for (ring in district.rings) {
                        for (i in ring.indices step 2) {
                            val p = geometry.project(Offset(ring[i], ring[i + 1]), size, inset)
                            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                        }
                        close()
                    }
                }
                district.id to shape
            }
            val fog = Brush.verticalGradient(listOf(Color(0xFFE8EBF1), Color(0xFFBDC8D6), Color(0xFFE8EEF1)))
            onDrawBehind {
                shapes.forEach { (id, shape) ->
                    if (id in visits) drawPath(shape, discovered) else drawPath(shape, fog)
                    drawPath(shape, Color(0xFF8599A7), style = Stroke(0.55.dp.toPx()))
                }
                journeyPlaces.forEach { place ->
                    val pin = geometry.project(place.mapPoint(), size, inset)
                    if (demoRoutes && place.id in visits) shapes.firstOrNull { it.first == place.id }?.let { (_, area) ->
                        clipPath(area) {
                            val route = Path().apply {
                                moveTo(pin.x - 19.dp.toPx(), pin.y + 13.dp.toPx())
                                quadraticBezierTo(pin.x - 7.dp.toPx(), pin.y - 9.dp.toPx(), pin.x, pin.y)
                            }
                            drawPath(route, Color(0xFFFFEB9B), style = Stroke(3.dp.toPx()))
                        }
                    }
                    if (place == selected) drawCircle(Color.White, 12.dp.toPx(), pin)
                    drawCircle(if (place.id in visits) ink else Color(0xFF7B8699), 8.dp.toPx(), pin)
                    drawCircle(if (place.id in visits) Color(0xFFF8D891) else Color(0xFFECEFF3), 3.dp.toPx(), pin)
                }
            }
        }) {}
}

private fun JourneyPlace.mapPoint() = Offset(((longitude - 124) * 81000).toFloat(), ((39 - latitude) * 100000).toFloat())
