package io.booup.roadmate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.booup.roadmate.model.CompanionId
import io.booup.roadmate.model.CompanionProfile
import io.booup.roadmate.model.CompanionProfiles

@Composable
internal fun CompanionRoster(current: CompanionProfile, state: JourneyGameState?, viewModel: RoadmateViewModel,
    equip: (CompanionId) -> Unit, close: () -> Unit) {
    val previews = viewModel.companionPreviews.associateBy { it.profile.id }
    val profiles = (CompanionProfiles.all + previews.values.map { it.profile }).associateBy { it.id }
    val owned = CompanionProfiles.all.map { it.id }.toSet() + (state?.companions ?: previews.keys)
    val slots = state?.companionSlots.orEmpty()
    var slot by remember { mutableIntStateOf(slots.indexOf(current.id).coerceAtLeast(0)) }
    var highlighted by remember { mutableStateOf(current.id) }
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val focus = profiles[highlighted] ?: current
    val preview = previews[focus.id]
    val currentSlot = slots.getOrNull(slot)
    val visibleProfiles = profiles.values.filter { profile ->
        previews[profile.id]?.original == null || profile.id in owned
    }

    fun saveSlot(id: CompanionId?) {
        val index = slot
        saving = true
        failed = false
        viewModel.journeyAction {
            try { failed = !setCompanionSlot(index, id) }
            finally { saving = false }
        }
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(GameInk)) {
            RoadmateLandscape("home-parked.png", 3, Modifier.matchParentSize(), artwork("journey/postcards.png"))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                GameInk.copy(alpha = .52f), GameInk.copy(alpha = .16f), GameInk.copy(alpha = .91f)))))
            val wide = maxWidth >= 680.dp
            val stageHeight = if (wide) (maxHeight - 114.dp).coerceAtLeast(256.dp) else 246.dp
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 28.dp else 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    GameButton("‹ 돌아가기", close)
                    Text("동행 선택", Modifier.weight(1f), color = GamePaper, style = RoadmateType.cardTitle)
                    GamePill("보유 ${owned.count { it in profiles }}명")
                }
                val stage: @Composable (Modifier) -> Unit = { modifier ->
                    Surface(modifier, shape = RoundedCornerShape(28.dp), color = GameInk.copy(alpha = .26f),
                        border = BorderStroke(1.dp, GameGold.copy(alpha = .45f))) {
                        Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Canvas(Modifier.matchParentSize()) {
                                    val glow = center.copy(y = size.height * .54f)
                                    drawCircle(Brush.radialGradient(listOf(GameGold.copy(alpha = .24f), Color.Transparent),
                                        center = glow, radius = size.minDimension * .54f), radius = size.minDimension * .54f, center = glow)
                                    drawOval(GameInk.copy(alpha = .36f), Offset(center.x - size.height * .29f, size.height * .86f),
                                        Size(size.height * .58f, size.height * .09f))
                                }
                                CompanionCardPortrait(focus, preview, Modifier.fillMaxHeight().aspectRatio(1f), animate = true)
                            }
                            Text(focus.displayName, color = GamePaper, style = RoadmateType.heading,
                                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(when {
                                focus.id == current.id -> "현재 동행 중"
                                focus.id in owned -> preview?.seasonTitle ?: "기본 동행"
                                else -> "${preview?.seasonTitle.orEmpty()} 패스 보상"
                            }, color = GameGold, style = RoadmateType.metric, textAlign = TextAlign.Center)
                            if (!editing) GameButton(
                                when (focus.id) { current.id -> "현재 동행 중"; in owned -> "이 동행과 출발"; else -> "아직 잠겨 있어요" },
                                { equip(focus.id); close() }, Modifier.widthIn(min = 172.dp), primary = focus.id in owned && focus.id != current.id,
                                enabled = focus.id in owned && focus.id != current.id)
                        }
                    }
                }
                val choices: @Composable (Modifier) -> Unit = { modifier ->
                    Surface(modifier, shape = RoundedCornerShape(24.dp), color = GameInk.copy(alpha = .88f),
                        border = BorderStroke(1.dp, GameGold.copy(alpha = .40f))) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            if (slots.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("동행 슬롯", Modifier.weight(1f), color = GamePaper, style = RoadmateType.cardTitle)
                                    GameButton(if (editing) "편집 닫기" else "슬롯 편집", { editing = !editing; failed = false }, enabled = !saving)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    slots.forEachIndexed { index, id ->
                                        val profile = profiles[id]
                                        CharacterTile(profile, previews[id], profile?.displayName ?: "빈 슬롯",
                                            "슬롯 ${index + 1}", if (editing) slot == index else highlighted == id,
                                            Modifier.weight(1f), locked = false) {
                                            if (profile == null && !editing) editing = true
                                            slot = index
                                            if (profile != null) highlighted = profile.id
                                            failed = false
                                        }
                                    }
                                }
                                if (editing) {
                                    Text("슬롯 ${slot + 1}에 넣을 동행을 아래에서 고르세요.", color = GameMist, style = RoadmateType.caption)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        GameButton("슬롯에 넣기", { saveSlot(focus.id) }, enabled = !saving && focus.id in owned && focus.id != currentSlot)
                                        GameButton("슬롯 비우기", { saveSlot(null) }, enabled = !saving && currentSlot != null)
                                    }
                                    if (failed) Text("슬롯을 저장하지 못했어요.", color = GameGold, style = RoadmateType.caption)
                                }
                            }
                            Text("캐릭터 컬렉션", color = GamePaper, style = RoadmateType.control)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                items(visibleProfiles, key = { it.id.value }) { profile ->
                                    CharacterTile(profile, previews[profile.id], profile.displayName,
                                        when (profile.id) { current.id -> "동행 중"; in owned -> "보유"; else -> "잠김" },
                                        profile.id == highlighted, Modifier.width(106.dp), profile.id !in owned) {
                                        highlighted = profile.id
                                        failed = false
                                    }
                                }
                            }
                        }
                    }
                }
                if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    stage(Modifier.weight(1f).height(stageHeight))
                    choices(Modifier.weight(1.35f))
                } else {
                    stage(Modifier.fillMaxWidth().height(stageHeight))
                    choices(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CharacterTile(profile: CompanionProfile?, preview: CompanionPreview?, name: String, status: String,
    selected: Boolean, modifier: Modifier, locked: Boolean, onClick: () -> Unit) {
    Surface(modifier.height(92.dp).semantics { this.selected = selected }.clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(14.dp), color = if (selected) Color(0xFF3B4750) else Color(0xFF213344),
        border = BorderStroke(1.dp, GameGold.copy(alpha = if (selected) 1f else .28f))) {
        Column(Modifier.padding(horizontal = 3.dp, vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (profile != null) CompanionCardPortrait(profile, preview, Modifier.fillMaxHeight().aspectRatio(1f))
                else Text("＋", color = GameGold, style = RoadmateType.cardTitle)
            }
            Text(name, color = GamePaper, style = RoadmateType.metric, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, color = if (locked) GameMist else GameGold, style = RoadmateType.caption.copy(fontSize = 12.sp))
        }
    }
}

@Composable
internal fun CompanionCardPortrait(profile: CompanionProfile, preview: CompanionPreview?, modifier: Modifier, animate: Boolean = false) {
    if (preview == null) OriginalCompanionPortrait(profile, modifier, animate = animate)
    else CompanionPreviewPortrait(preview, modifier, animate = animate)
}
