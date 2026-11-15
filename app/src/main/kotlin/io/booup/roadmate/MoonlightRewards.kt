package io.booup.roadmate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.booup.roadmate.model.CompanionProfile
import io.booup.roadmate.model.CompanionProfiles

private val WardrobeInk = Color(0xFF292238)
private val WardrobeLavender = Color(0xFFEEE2FF)

@Composable
internal fun MoonlightWardrobe(state: JourneyGameState, viewModel: RoadmateViewModel, companion: CompanionProfile,
    preview: CompanionPreview?, close: () -> Unit) {
    val equipped = MoonlightOutfit.entries.firstOrNull { it.name == state.moonlightOutfit }
    var selected by remember { mutableStateOf(equipped ?: MoonlightOutfit.SCARF) }
    var tryOn by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    val premium = MOONLIGHT_DEMO_PREMIUM in state.claimed
    val owned = selected.claimKey in state.claimed
    val reached = state.moonlightCompleted >= selected.chapter
    val claimable = reached && (!selected.premium || premium)
    val wearing = selected == equipped

    fun save(showEquippedAfter: Boolean = false, action: suspend JourneyGame.() -> Boolean) {
        if (saving) return
        saving = true
        saveFailed = false
        viewModel.journeyAction {
            try {
                saveFailed = !action()
                if (!saveFailed && showEquippedAfter) tryOn = false
            }
            finally { saving = false }
        }
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(WardrobeInk)) {
            RoadmateLandscape("story-gangneung-gyeongpodae.png", 1, Modifier.matchParentSize(), artwork("journey/postcards.png"))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                WardrobeInk.copy(alpha = .68f), WardrobeInk.copy(alpha = .30f), WardrobeInk.copy(alpha = .93f)))))
            val wide = maxWidth >= 680.dp
            val portraitHeight = if (wide) (maxHeight - 114.dp).coerceAtLeast(248.dp) else 260.dp
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) 28.dp else 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    GameButton("‹ 돌아가기", close)
                    Text("동행 옷장", Modifier.weight(1f), color = GamePaper, style = RoadmateType.cardTitle)
                    if (wide) GamePill(companion.displayName)
                }
                val fitting: @Composable (Modifier) -> Unit = { modifier ->
                    Surface(modifier, shape = RoundedCornerShape(26.dp), color = WardrobeInk.copy(alpha = .46f),
                        border = BorderStroke(1.dp, WardrobeLavender.copy(alpha = .55f))) {
                        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (tryOn && !wearing) "착용 미리보기" else "현재 착용 모습", color = WardrobeLavender, style = RoadmateType.metric)
                            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                Box(Modifier.fillMaxHeight().aspectRatio(1f).background(
                                    Brush.radialGradient(listOf(WardrobeLavender.copy(alpha = .26f), Color.Transparent))))
                                DressedCompanion(companion, preview, if (tryOn) selected else equipped,
                                    Modifier.fillMaxHeight().aspectRatio(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(10.dp), color = WardrobeInk.copy(alpha = .90f),
                                    border = BorderStroke(1.dp, WardrobeLavender.copy(alpha = .42f))) {
                                    if (equipped == null) Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                                        Text("—", color = WardrobeLavender)
                                    } else OutfitIcon(equipped, Modifier.size(46.dp).padding(6.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("착용 중", color = WardrobeLavender, style = RoadmateType.caption)
                                    Text(equipped?.label ?: "기본 모습", color = GamePaper, style = RoadmateType.metric)
                                }
                            }
                        }
                    }
                }
                val inventory: @Composable (Modifier) -> Unit = { modifier ->
                    Surface(modifier, shape = RoundedCornerShape(24.dp), color = WardrobeInk.copy(alpha = .94f),
                        border = BorderStroke(1.dp, WardrobeLavender.copy(alpha = .38f))) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("의상 보관함", color = GamePaper, style = RoadmateType.cardTitle)
                            val columns = if (wide) 4 else 2
                            MoonlightOutfit.entries.chunked(columns).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { outfit ->
                                        val status = when {
                                            outfit == equipped -> "착용 중"
                                            outfit.claimKey in state.claimed -> "보유"
                                            state.moonlightCompleted >= outfit.chapter && (!outfit.premium || premium) -> "받을 수 있음"
                                            else -> "잠김"
                                        }
                                        OutfitSlot(outfit, outfit == selected, status, Modifier.weight(1f)) {
                                            selected = outfit
                                            tryOn = true
                                            saveFailed = false
                                        }
                                    }
                                }
                            }
                            Text(selected.label, color = GamePaper, style = RoadmateType.control)
                            Text("${if (selected.premium) "프리미엄 패스" else "무료 패스"} · 강릉 ${selected.chapter}장 완료",
                                color = WardrobeLavender, style = RoadmateType.caption)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                GameButton(when {
                                    wearing -> "벗기"
                                    owned -> "입기"
                                    claimable -> "보상 받기"
                                    !reached -> "${selected.chapter}장 완료 후 받기"
                                    else -> "프리미엄 전용"
                                }, {
                                    val item = selected
                                    save(showEquippedAfter = owned) {
                                        if (owned) equipMoonlightOutfit(item.takeUnless { wearing })
                                        else claimMoonlightOutfit(item).cue(RoadmateSound.REWARD)
                                    }
                                }, primary = true, enabled = !saving && (owned || claimable))
                                if (selected.premium && !premium) {
                                    GameButton("프리미엄 미리보기 · 결제 없음", { save { grantDemoMoonlightPremium().cue(RoadmateSound.UPGRADE) } }, enabled = !saving)
                                }
                            }
                            if (saveFailed) Text("변경을 저장하지 못했어요.", color = GameGold, style = RoadmateType.caption)
                        }
                    }
                }
                if (wide) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    fitting(Modifier.weight(.86f).height(portraitHeight))
                    inventory(Modifier.weight(1.14f))
                } else {
                    fitting(Modifier.fillMaxWidth().height(portraitHeight))
                    inventory(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun OutfitSlot(outfit: MoonlightOutfit, selected: Boolean, status: String, modifier: Modifier, choose: () -> Unit) {
    Surface(modifier.height(91.dp).semantics { this.selected = selected }.clickable(role = Role.Button, onClick = choose),
        shape = RoundedCornerShape(12.dp), color = if (selected) Color(0xFF594361) else Color(0xFF39304B),
        border = BorderStroke(1.dp, if (selected) GameGold else WardrobeLavender.copy(alpha = .28f))) {
        Column(Modifier.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            OutfitIcon(outfit, Modifier.weight(1f).aspectRatio(1f))
            Text(when (outfit) {
                MoonlightOutfit.SCARF -> "스카프"
                MoonlightOutfit.HAT -> "모자"
                MoonlightOutfit.JACKET -> "바람막이"
                MoonlightOutfit.CAPE -> "망토"
            }, color = GamePaper, style = RoadmateType.metric, maxLines = 1)
            Text(status, color = if (status == "잠김") WardrobeLavender else GameGold,
                style = RoadmateType.caption, maxLines = 1)
        }
    }
}

private val MoonlightOutfit.tint get() = when (this) {
    MoonlightOutfit.SCARF -> Color(0xFF82BF9D)
    MoonlightOutfit.HAT -> Color(0xFFF7C785)
    MoonlightOutfit.JACKET -> Color(0xFF86B8DF)
    MoonlightOutfit.CAPE -> Color(0xFFBC99D5)
}

@Composable
private fun OutfitIcon(outfit: MoonlightOutfit, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val icon = Path().apply {
            val points = when (outfit) {
                MoonlightOutfit.SCARF -> listOf(.13f to .34f, .82f to .32f, .87f to .53f, .60f to .56f,
                    .70f to .87f, .48f to .81f, .42f to .56f, .15f to .56f)
                MoonlightOutfit.HAT -> listOf(.12f to .72f, .10f to .58f, .29f to .52f, .35f to .27f,
                    .65f to .27f, .71f to .52f, .90f to .58f, .88f to .72f)
                MoonlightOutfit.JACKET -> listOf(.30f to .19f, .48f to .27f, .70f to .19f, .90f to .54f,
                    .75f to .63f, .72f to .85f, .28f to .85f, .25f to .63f, .10f to .54f)
                MoonlightOutfit.CAPE -> listOf(.34f to .23f, .66f to .23f, .90f to .75f, .68f to .85f,
                    .50f to .73f, .32f to .85f, .10f to .75f)
            }
            points.forEachIndexed { index, (x, y) -> if (index == 0) moveTo(w * x, h * y) else lineTo(w * x, h * y) }
            close()
        }
        drawPath(icon, outfit.tint)
        drawPath(icon, WardrobeInk, style = Stroke(2.dp.toPx()))
        if (outfit == MoonlightOutfit.JACKET) drawLine(GamePaper, Offset(w * .5f, h * .30f), Offset(w * .5f, h * .82f), 1.dp.toPx())
        else drawCircle(GamePaper, w * .045f, Offset(w * .5f, h * .46f))
    }
}

@Composable
internal fun DressedCompanion(companion: CompanionProfile, preview: CompanionPreview?, outfit: MoonlightOutfit?,
    modifier: Modifier, motion: CompanionMotion? = null) {
    Box(modifier, contentAlignment = Alignment.Center) {
        val original = preview?.original ?: companion
        val coat = original.id == CompanionProfiles.dodam.id && outfit == MoonlightOutfit.JACKET
        if (preview != null && preview.original == null) PreviewCompanionPortrait(preview, Modifier.fillMaxSize(), motion = motion)
        else OriginalCompanionPortrait(original, Modifier.fillMaxSize(), motion = if (coat) CompanionMotion.SAILOR_COAT else motion)
        if (outfit != null && !coat) OutfitOverlay(outfit, Modifier.matchParentSize())
    }
}

@Composable
internal fun OutfitOverlay(outfit: MoonlightOutfit, modifier: Modifier) {
    val (top, width, height) = when (outfit) {
        MoonlightOutfit.SCARF -> Triple(.59f, .43f, .12f)
        MoonlightOutfit.HAT -> Triple(.12f, .47f, .22f)
        MoonlightOutfit.JACKET -> Triple(.64f, .47f, .24f)
        MoonlightOutfit.CAPE -> Triple(.61f, .56f, .24f)
    }
    BoxWithConstraints(modifier) {
        OutfitIcon(outfit, Modifier.offset(x = maxWidth * ((1f - width) / 2), y = maxHeight * top)
            .size(maxWidth * width, maxHeight * height))
    }
}
