package io.booup.roadmate

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.booup.roadmate.model.*

private enum class AlbumFilter(val label: String) { ALL("전체"), OWNED("보유"), LOCKED("미발견"), SEASON("계절") }

@Composable
fun CollectionContent(viewModel: RoadmateViewModel, preview: CompanionPreview?, openMap: () -> Unit) {
    val state by viewModel.postDriveUiState.collectAsStateWithLifecycle()
    val care by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.contentAllowed) return
    val owned = state.collection.associateBy { it.key.regionId }
    val art = artwork("journey/postcards.png")
    var filter by remember { mutableStateOf(AlbumFilter.ALL) }
    var season by remember { mutableStateOf<CalendarSeason?>(null) }
    var detail by remember { mutableStateOf<PostcardKey?>(null) }
    val slots = (journeyPlaces.map { it to owned[it.id] } + state.collection.filter { card ->
        journeyPlaces.none { it.id == card.key.regionId }
    }.map { null to it }).filter { (_, card) ->
        when (filter) {
            AlbumFilter.ALL -> true
            AlbumFilter.OWNED -> card != null
            AlbumFilter.LOCKED -> card == null
            AlbumFilter.SEASON -> card != null && (season == null || card.calendar.season == season)
        }
    }
    RoadmateCard(tinted = true) {
        Text("엽서 컬렉션", style = MaterialTheme.typography.headlineMedium)
        Text("우리의 드라이브 앨범 · 보유 ${state.collection.size}장", style = MaterialTheme.typography.titleMedium)
        val covered = journeyPlaces.count { it.id in owned }
        LinearProgressIndicator(progress = { covered / journeyPlaces.size.toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp))
        Text("여행 테마 엽서 $covered / ${journeyPlaces.size}장")
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AlbumFilter.entries.forEach { item -> FilterChip(filter == item, { filter = item }, { Text(item.label) },
            modifier = Modifier.heightIn(min = 54.dp)) }
    }
    if (filter == AlbumFilter.SEASON) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        (listOf(null) + CalendarSeason.entries).forEach { item ->
            FilterChip(season == item, { season = item }, { Text(item?.let(UiCopy::season) ?: "모든 계절") },
                modifier = Modifier.heightIn(min = 54.dp))
        }
    }
    if (slots.isEmpty()) RoadmateCard { Text("이 보기에는 아직 엽서가 없어요.") }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = when { maxWidth >= 1120.dp -> 4; maxWidth >= 640.dp -> 2; else -> 1 }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            slots.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { (place, card) ->
                        RoadmateCard(Modifier.weight(1f)) {
                            if (card != null) {
                                PostcardArtwork(place, art, preview, Modifier.fillMaxWidth().height(132.dp), companion = care.companion)
                                Text(place?.title ?: card.regionLabel, style = MaterialTheme.typography.titleMedium)
                                Text("${UiCopy.season(card.calendar.season)}의 기록 · 보유", style = MaterialTheme.typography.bodyMedium)
                                OutlinedButton(onClick = { detail = card.key }, modifier = Modifier.heightIn(min = 54.dp)) { Text("엽서 펼치기") }
                            } else {
                                Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(16.dp))
                                    .background(Brush.verticalGradient(listOf(Color(0xFFE8EAF1), Color(0xFFD7DDE7)))),
                                    contentAlignment = Alignment.Center) { Text("?", style = MaterialTheme.typography.headlineLarge) }
                                Text(requireNotNull(place).name, style = MaterialTheme.typography.titleMedium)
                                Text("미발견 · 첫 방문 기록으로 열려요.", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
    OutlinedButton(onClick = openMap, modifier = Modifier.heightIn(min = 56.dp)) { Text("탐험 지도 열기") }
    owned.values.firstOrNull { it.key == detail }?.let { card ->
        val place = journeyPlaces.firstOrNull { it.id == card.key.regionId }
        var back by remember(card.key) { mutableStateOf(true) }
        val turn by animateFloatAsState(if (back) 180f else 0f, tween(400), label = "postcard turn")
        LaunchedEffect(card.key) { back = false }
        AlertDialog(onDismissRequest = { detail = null }, title = { Text(place?.title ?: card.regionLabel) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.fillMaxWidth().height(156.dp).graphicsLayer {
                    rotationY = if (turn < 90f) turn else turn - 180f
                    cameraDistance = density * 18f
                }) {
                    if (turn < 90f) PostcardArtwork(place, art, preview, Modifier.matchParentSize(), animate = true, companion = care.companion)
                    else Box(Modifier.matchParentSize().clip(RoundedCornerShape(16.dp)).background(Color(0xFFF6E5C5)),
                        contentAlignment = Alignment.Center) { Text("ROAD MATE · 여행 소인", style = MaterialTheme.typography.titleMedium) }
                }
                Text(place?.note ?: "여행을 마치고 수집한 엽서")
                Text("${UiCopy.season(card.calendar.season)} · ${UiCopy.date(card.acquiredAtEpochMillis)} UTC")
                if (card.source == RegionEvidenceSource.DEMO) RoadmateBadge("SIMULATED · 시연 엽서", demo = true)
            }
        }, confirmButton = { TextButton(onClick = { detail = null }) { Text("앨범으로") } },
            dismissButton = { TextButton(onClick = { back = !back }) { Text("엽서 뒤집기") } })
    }
}

@Composable
internal fun PostcardArtwork(place: JourneyPlace?, atlas: ImageBitmap?, preview: CompanionPreview?, modifier: Modifier,
    animate: Boolean = false, companion: CompanionProfile? = null) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFFDCEEE4))) {
        if (place != null && atlas != null) AtlasFrame(atlas, place.art, Modifier.matchParentSize(), fill = true)
        if (preview != null) CompanionPreviewPortrait(preview, Modifier.align(Alignment.BottomEnd).size(102.dp), animate)
        else if (companion != null) CompanionPortrait(companion.displayName, companion.tone == CompanionTone.WARM,
            Modifier.align(Alignment.BottomEnd).size(102.dp))
    }
}
