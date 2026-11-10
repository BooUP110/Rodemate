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
private data class MapGeometry(val districts: List<District>, val bounds: Rect) {
    fun project(point: Offset, canvas: Size, inset: Float): Offset {
        val scale = minOf((canvas.width - inset * 2) / bounds.width, (canvas.height - inset * 2) / bounds.height)
        return (point - bounds.center) * scale + Offset(canvas.width / 2, canvas.height / 2)
    }
}

private fun readDistricts(json: String): MapGeometry {
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
    return MapGeometry(districts, Rect(west, north, east, south))
}

@Composable
fun ExplorationContent(viewModel: RoadmateViewModel, mode: RuntimeMode, openCollection: () -> Unit,
    onSimulate: (JourneyPlace) -> Unit) {
    val trip by viewModel.tripUiState.collectAsStateWithLifecycle()
    val journey by viewModel.journey.collectAsStateWithLifecycle()
    val expected = if (mode == RuntimeMode.DEMO) RegionEvidenceSource.DEMO else RegionEvidenceSource.VERIFIED_CONTEXT
    val visits = trip.storage.visits.filter { it.source == expected }.map { it.regionId }.toSet()
    val assets = LocalContext.current.assets
    var failed by remember { mutableStateOf(false) }
    val map by produceState<MapGeometry?>(null, assets) {
        val result = withContext(Dispatchers.IO) {
            runCatching { readDistricts(assets.open("journey/korea-districts.json").bufferedReader().use { it.readText() }) }
        }
        value = result.getOrNull()
        failed = result.isFailure
    }
    var selected by remember(journey.place) { mutableStateOf(journey.place ?: journeyPlaces.first()) }
    val discovered = map?.districts?.count { it.id in visits } ?: 0
    Text("안개 너머, 우리의 한국", style = MaterialTheme.typography.headlineMedium)
    Text("도착한 곳의 안개가 걷히고, 첫 방문 엽서가 한 장씩 모여요.", style = MaterialTheme.typography.bodyMedium)
    if (map != null) Text("탐험한 구역 $discovered / ${map!!.districts.size}", style = MaterialTheme.typography.titleMedium)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val chart: @Composable (Modifier) -> Unit = { modifier ->
            RoadmateCard(modifier) {
                val geometry = map
                if (geometry == null) Text(if (failed) "지도 파일을 읽지 못했어요." else "한국 지도를 펼치는 중이에요.")
                else KoreaFogMap(geometry, visits, selected) { selected = it }
                Text("● 발견한 구역     ○ 안개 구역", style = MaterialTheme.typography.labelMedium)
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
                if (visited) Button(onClick = openCollection, modifier = Modifier.heightIn(min = 56.dp)) { Text("엽서 컬렉션 열기") }
                else if (viewModel.canSimulateVisit) {
                    Button(onClick = { onSimulate(selected) }, enabled = !journey.running, modifier = Modifier.heightIn(min = 56.dp)) {
                        Text("${selected.name} 방문 시연")
                    }
                    Text("합성 트립과 시연 기록을 이 앱에 저장해요.", style = MaterialTheme.typography.bodyMedium)
                } else Text("확인된 차량 위치 기록을 기다리고 있어요.")
                if (journey.place == selected && !journey.running) {
                    Text(if (journey.saved) "첫 방문 엽서를 컬렉션에 넣었어요." else "방문 기록을 만들지 못했어요. 정차 상태에서 다시 시연해 주세요.")
                }
            }
        }
        if (maxWidth >= 950.dp) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            chart(Modifier.weight(1.4f)); destinations(Modifier.weight(1f))
        } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { chart(Modifier); destinations(Modifier) }
    }
}

@Composable
private fun KoreaFogMap(geometry: MapGeometry, visits: Set<RegionId>, selected: JourneyPlace, onSelect: (JourneyPlace) -> Unit) {
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
                    if (place == selected) drawCircle(Color.White, 12.dp.toPx(), pin)
                    drawCircle(if (place.id in visits) ink else Color(0xFF7B8699), 8.dp.toPx(), pin)
                    drawCircle(if (place.id in visits) Color(0xFFF8D891) else Color(0xFFECEFF3), 3.dp.toPx(), pin)
                }
            }
        }) {}
}

private fun JourneyPlace.mapPoint() = Offset(((longitude - 124) * 81000).toFloat(), ((39 - latitude) * 100000).toFloat())
