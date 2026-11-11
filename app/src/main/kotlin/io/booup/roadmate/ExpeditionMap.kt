package io.booup.roadmate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.booup.roadmate.model.RegionId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import kotlin.math.roundToInt

private val MapMint = Color(0xFFBBEBCF)
private val MapGold = Color(0xFFFFD68A)
private val MapInk = Color(0xFF233745)

private data class RegionalGeometry(val rings: List<List<Offset>>, val bounds: Rect)

private fun parseRegion(route: Expedition, districts: String, provinces: String): RegionalGeometry {
    val ids = route.province?.let { province ->
        val groups = JSONArray(provinces)
        val group = (0 until groups.length()).map(groups::getJSONArray).first { it.getString(0) == province }
        group.getJSONArray(1).let { values -> (0 until values.length()).map(values::getString).toSet() }
    } ?: setOf(route.region.id.value)
    val shapes = JSONArray(districts)
    val rings = (0 until shapes.length()).map(shapes::getJSONArray).filter { it.getString(0) in ids }.flatMap { district ->
        val values = district.getJSONArray(1)
        (0 until values.length()).map { index ->
            val ring = values.getJSONArray(index)
            (0 until ring.length() step 2).map { Offset(ring.getInt(it).toFloat(), ring.getInt(it + 1).toFloat()) }
        }
    }
    val points = rings.flatten()
    require(points.isNotEmpty())
    return RegionalGeometry(rings, points.bounds())
}

private fun List<Offset>.bounds() = Rect(minOf { it.x }, minOf { it.y }, maxOf { it.x }, maxOf { it.y })

private fun JourneyPlace.mapPoint() = Offset(((longitude - 124) * 81000).toFloat(), ((39 - latitude) * 100000).toFloat())

private fun viewport(route: Expedition, region: RegionalGeometry?): Rect {
    val tour = route.places.map { it.mapPoint() }.bounds()
    val area = region?.bounds ?: tour
    if (region != null && route.province != null) {
        return Rect(minOf(tour.left, area.left), minOf(tour.top, area.top), maxOf(tour.right, area.right), maxOf(tour.bottom, area.bottom))
    }
    val width = maxOf(tour.width * 1.6f, area.width * .62f)
    val height = maxOf(tour.height * 1.85f, area.height * .62f)
    val center = tour.center
    return Rect(center.x - width / 2, center.y - height / 2, center.x + width / 2, center.y + height / 2)
}

private fun Rect.project(point: Offset, size: Size, inset: Float): Offset {
    val scale = minOf((size.width - 2 * inset) / width, (size.height - 2 * inset) / height).coerceAtLeast(0f)
    return (point - center) * scale + Offset(size.width / 2, size.height / 2)
}

@Composable
internal fun ExpeditionMap(route: Expedition, visible: List<JourneyPlace>, visited: Set<RegionId>,
    selected: RegionId?, origin: RegionId?, destination: RegionId?, connection: List<JourneyPlace>,
    landmark: RegionId?, modifier: Modifier, select: (JourneyPlace) -> Unit) {
    val assets = LocalContext.current.assets
    val geometry by produceState<RegionalGeometry?>(null, assets, route) {
        value = withContext(Dispatchers.IO) {
            runCatching { parseRegion(route, assets.open("journey/korea-districts.json").bufferedReader().use { it.readText() },
                assets.open("journey/korea-provinces.json").bufferedReader().use { it.readText() }) }.getOrNull()
        }
    }
    val bounds = remember(route, geometry) { viewport(route, geometry) }
    val fogTexture = artwork("journey/design/map-fog.png")
    val visibleIds = remember(visible) { visible.map { it.id }.toSet() }
    val segments = remember(route, visibleIds) {
        route.places.zipWithNext().filter { (a, b) -> a.id in visibleIds && b.id in visibleIds }
    }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(22.dp))) {
        val density = LocalDensity.current
        val canvas = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val inset = with(density) { 34.dp.toPx() }
        fun position(place: JourneyPlace) = bounds.project(place.mapPoint(), canvas, inset)
        Canvas(Modifier.matchParentSize().drawWithCache {
            val area = Path().apply {
                fillType = PathFillType.EvenOdd
                geometry?.rings?.forEach { ring ->
                    ring.forEachIndexed { index, point ->
                        val p = bounds.project(point, size, inset)
                        if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                    }
                    close()
                }
            }
            val clears = Path().apply {
                visible.forEach { point ->
                    addOval(Rect(position(point), if (point.id in visited) 43.dp.toPx() else 31.dp.toPx()))
                }
            }
            val fog = Brush.verticalGradient(listOf(Color(0xF0A9ADC9), Color(0xE46D7F9D), Color(0xF0B9BDCE)))
            onDrawBehind {
                drawRoundRect(Color(0x42303F56), cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()))
                drawPath(area, Color(0xD0365662))
                drawPath(area, Color(0xA5C2E5E5), style = Stroke(1.dp.toPx()))
                clipPath(clears, clipOp = ClipOp.Difference) {
                    drawPath(area, fog)
                    if (fogTexture != null) clipPath(area) {
                        drawImage(fogTexture, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), alpha = .26f)
                    }
                }
                visible.forEach { point ->
                    val center = position(point)
                    val glow = if (point.id in visited) MapMint else MapGold
                    val radius = 32.dp.toPx()
                    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = .55f), Color.Transparent), center, radius),
                        radius, center)
                }
                segments.forEach { (a, b) ->
                    drawLine(MapMint.copy(alpha = .82f), position(a), position(b), 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
                }
                connection.zipWithNext().forEach { (a, b) ->
                    drawLine(MapGold.copy(alpha = .3f), position(a), position(b), 9.dp.toPx(), StrokeCap.Round)
                    drawLine(MapGold, position(a), position(b), 3.dp.toPx(), StrokeCap.Round)
                }
                visible.forEach { point ->
                    val center = position(point)
                    val color = if (point.id in visited) MapMint else MapGold
                    if (point.id == selected) drawCircle(Color.White, 18.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
                    drawCircle(MapInk, 13.dp.toPx(), center)
                    drawCircle(color, 12.dp.toPx(), center, style = Stroke(2.dp.toPx()))
                }
            }
        }) {}
        visible.forEach { point ->
            val pos = position(point)
            val half = with(density) { 24.dp.toPx() }
            val status = when (point.id) {
                origin -> "출발지"
                destination -> "목적지"
                landmark -> "등록한 랜드마크"
                in visited -> "방문 완료"
                else -> "다음 목표"
            }
            Box(Modifier.offset { IntOffset((pos.x - half).roundToInt(), (pos.y - half).roundToInt()) }
                .size(48.dp).clip(CircleShape).semantics { contentDescription = "${point.name} · $status" }
                .clickable(role = Role.Button) { select(point) }, contentAlignment = Alignment.Center) {
                Text(when (point.id) { origin -> "출"; destination -> "도"; landmark -> "★"; in visited -> "✓"; else -> "✦" },
                    color = if (point.id in visited) MapMint else MapGold, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
        Text("경계 © geoBoundaries · citypopulation.de · CC BY 3.0",
            Modifier.align(Alignment.BottomStart).padding(start = 10.dp, bottom = 5.dp), color = Color.White, fontSize = 10.sp)
    }
}
