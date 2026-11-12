package io.booup.roadmate

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.booup.roadmate.model.CompanionProfile
import io.booup.roadmate.model.CompanionProfiles

internal enum class MoonlightBeat {
    COVER, GREETING, CLUE, TRAVEL, ARRIVAL, MISSION, PUZZLE, RESTORED,
}

private enum class PostcardFragment {
    CLOUD, REFLECTION, STAR,
}

private val MoonlightGold = Color(0xFFFFDA91)
private val MoonlightInk = Color(0xFF202E47)
private val MoonlightCream = Color(0xFFFFF2D9)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoonlightEpisode(chapter: MoonlightChapter, beat: MoonlightBeat, companion: CompanionProfile,
    viewModel: RoadmateViewModel, state: JourneyGameState,
    saving: Boolean, failed: Boolean, earnedXp: Long, modifier: Modifier, insets: PaddingValues, close: () -> Unit,
    advance: (MoonlightBeat) -> Unit, beginFromStart: () -> Unit, solve: (Int) -> Unit,
    nextChapter: () -> Unit, openAlbum: () -> Unit,
    priorityCare: @Composable () -> Unit) {
    val atlas = artwork("journey/postcards.png")
    var wrongAnswer by rememberSaveable(chapter) { mutableStateOf(false) }
    var wardrobe by rememberSaveable { mutableStateOf(false) }
    var roster by rememberSaveable { mutableStateOf(false) }
    val previews = viewModel.companionPreviews
    val player = previews.firstOrNull { it.profile.id == companion.id && it.profile.id in state.companions }
    val cast = JourneyPass.TRAVEL.companionIds().mapNotNull { id -> previews.firstOrNull { it.profile.id == id } }
    val guests = cast.filter { it.profile.id != companion.id }
    val guest = guests.takeIf { it.isNotEmpty() }?.let { it[chapter.ordinal % it.size] }
    val outfit = MoonlightOutfit.entries.firstOrNull { it.name == state.moonlightOutfit }
    val speaker = companion.displayName.substringBefore(" (")
    val isCover = beat == MoonlightBeat.COVER
    val lastChapter = chapter == MoonlightChapter.ANMOK
    val finished = state.moonlightCompleted == MoonlightChapter.entries.size
    if (wardrobe) MoonlightWardrobe(state, viewModel) { wardrobe = false }
    if (roster) MoonlightRoster(companion, state, viewModel) { roster = false }
    BoxWithConstraints(modifier.clipToBounds().background(MoonlightInk)) {
        val wide = maxWidth >= 680.dp
        val margin = if (wide) 32.dp else 16.dp
        val sceneHeight = maxHeight
        val art = if (isCover || beat == MoonlightBeat.TRAVEL) "map-gangneung.png" else chapter.art
        val camera = remember(art) { Animatable(0f) }
        LaunchedEffect(art) { camera.animateTo(1f, tween(3600, easing = FastOutSlowInEasing)) }
        val cameraPosition = camera.asState()
        MoonlightBackground(art, cameraPosition, atlas)
        MoonlightDepth(art, "mid", cameraPosition)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
            Color(0xBD17233A), Color(0x172E4568), Color(0x44334566), Color(0xEA17233A)))))

        if (chapter == MoonlightChapter.GYEONGPODAE && (beat == MoonlightBeat.ARRIVAL || beat == MoonlightBeat.RESTORED)) {
            Canvas(Modifier.align(BiasAlignment(.65f, -.54f)).size(if (wide) 92.dp else 55.dp)) {
                drawCircle(Brush.radialGradient(listOf(Color(0x70FFDA91), Color.Transparent), radius = size.minDimension / 2f))
                drawCircle(MoonlightCream, radius = size.minDimension * .19f)
            }
        }

        if (isCover && cast.isNotEmpty()) {
            val actorSize = minOf(maxWidth * if (wide) .14f else .25f, maxHeight * .28f)
            Row(Modifier.align(BiasAlignment(if (wide) .99f else 0f, if (wide) .69f else -.40f)),
                verticalAlignment = Alignment.CenterVertically) {
                cast.forEachIndexed { index, preview -> key(preview.profile.id) {
                    MoonlightActor(preview.profile, preview, null, Modifier.size(actorSize)
                        .offset(y = if (wide) (15 - index * 15).dp else 0.dp))
                } }
            }
        } else if (beat in setOf(MoonlightBeat.COVER, MoonlightBeat.GREETING, MoonlightBeat.CLUE, MoonlightBeat.ARRIVAL)) {
            val pose = Modifier.align(BiasAlignment(if (wide) .59f else .28f, if (wide) -.06f else -.37f))
                .size(if (wide) minOf(maxWidth * .30f, maxHeight * .48f) else maxHeight * .27f)
            val resident = guest.takeIf { beat == MoonlightBeat.CLUE }
            val residentScene = beat == MoonlightBeat.CLUE
            val actor = if (residentScene) resident?.profile ?: CompanionProfiles.all.first { it.id != companion.id } else companion
            key(chapter, actor.id) { MoonlightActor(actor, if (residentScene) resident else player,
                outfit.takeUnless { residentScene }, pose) }
            if (residentScene && wide) {
                val listener = Modifier.align(BiasAlignment(.04f, .07f)).size(minOf(maxWidth * .22f, maxHeight * .36f))
                key(chapter, "listener") { MoonlightActor(companion, player, outfit, listener) }
            }
        }
        MoonlightDepth(art, "front", cameraPosition)
        if (beat == MoonlightBeat.RESTORED) {
            val postcardWidth = if (wide) minOf(maxWidth * .33f, maxHeight * .68f) else maxWidth * .67f
            RestoredPostcard(chapter, Modifier.align(BiasAlignment(if (wide) .67f else 0f, if (wide) .03f else -.37f))
                .width(postcardWidth), atlas)
        }

        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(insets)
            .padding(horizontal = margin, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GameButton("‹ 나가기", close, enabled = !saving)
                Text(if (isCover) "강릉" else chapter.place.name, Modifier.weight(1f), color = Color.White,
                    style = RoadmateType.cardTitle)
                if (wide && !isCover) GamePill(chapter.title)
            }
            priorityCare()
        }

        val panelWidth = if (wide && beat != MoonlightBeat.PUZZLE) maxWidth * .57f else maxWidth
        Column(Modifier.align(Alignment.BottomStart).width(panelWidth).heightIn(max = sceneHeight * .73f).padding(insets)
            .padding(horizontal = margin, vertical = if (wide) 26.dp else 12.dp).verticalScroll(rememberScrollState())
            .background(MoonlightInk.copy(alpha = .92f), RoundedCornerShape(26.dp)).padding(if (wide) 22.dp else 15.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)) {
            when (beat) {
                MoonlightBeat.COVER -> {
                    Text(if (cast.isEmpty()) "강릉 이야기" else "치이카와 강릉 패스 · 내부 시연",
                        color = MoonlightGold, style = MaterialTheme.typography.labelLarge)
                    Text("사라진 달빛 엽서", color = Color.White,
                        style = RoadmateType.heading.copy(fontSize = if (wide) 49.sp else 35.sp,
                            lineHeight = if (wide) 58.sp else 42.sp))
                    Text(if (state.moonlightCompleted == 0) "그림이 사라진 엽서 한 장. 오죽헌에서 첫 단서를 찾아요."
                        else "모은 엽서 조각 ${state.moonlightCompleted} / ${MoonlightChapter.entries.size}",
                        color = MoonlightCream, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MoonlightButton(when { finished -> "완주 다시 보기"; state.moonlightCompleted > 0 -> "이어 하기"; else -> "이야기 시작" }) {
                            advance(if (finished) MoonlightBeat.RESTORED else MoonlightBeat.GREETING)
                        }
                        if (state.moonlightCompleted > 0) FilledTonalButton(beginFromStart) { Text("처음부터") }
                        FilledTonalButton({ roster = true }) { Text("동행 선택") }
                        FilledTonalButton({ wardrobe = true }) { Text("의상 보상") }
                    }
                }
                MoonlightBeat.GREETING -> {
                    MoonlightDialogue(speaker, chapter.introduction)
                    MoonlightButton("목적지로 출발") { advance(MoonlightBeat.TRAVEL) }
                }
                MoonlightBeat.CLUE -> {
                    MoonlightDialogue(guest?.profile?.displayName ?: chapter.resident, chapter.clue)
                    MoonlightButton("현장 미션 보기") { advance(MoonlightBeat.MISSION) }
                }
                MoonlightBeat.TRAVEL -> {
                    MoonlightDialogue("이번 목적지", chapter.place.name)
                    MoonlightButton("도착 시연") { advance(MoonlightBeat.ARRIVAL) }
                }
                MoonlightBeat.ARRIVAL -> {
                    MoonlightDialogue(speaker, chapter.arrival)
                    MoonlightButton("현장 이야기 듣기") { advance(MoonlightBeat.CLUE) }
                }
                MoonlightBeat.MISSION -> {
                    MoonlightDialogue("현장 미션", chapter.mission)
                    MoonlightButton(if (saving) "위치 인증 시연 중…" else "위치 인증 시연", enabled = !saving) {
                        wrongAnswer = false; advance(MoonlightBeat.PUZZLE)
                    }
                }
                MoonlightBeat.PUZZLE -> {
                    MoonlightDialogue(
                        if (wrongAnswer) guest?.profile?.displayName ?: chapter.resident else "엽서의 빈칸",
                        if (wrongAnswer) chapter.clue else chapter.puzzle,
                    )
                    val choose: (Int) -> Unit = { index ->
                        wrongAnswer = index != chapter.answer
                        if (!wrongAnswer) solve(index)
                    }
                    if (wide && !lastChapter) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        chapter.options.forEachIndexed { index, label ->
                            FragmentButton(label, PostcardFragment.entries[index].takeIf { chapter == MoonlightChapter.GYEONGPODAE },
                                !saving, Modifier.weight(1f)) { choose(index) }
                        }
                    } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        chapter.options.forEachIndexed { index, label ->
                            FragmentButton(label, null, !saving, Modifier.fillMaxWidth()) { choose(index) }
                        }
                    }
                    if (saving) Text("엽서 조각을 저장하고 있어요…", color = MoonlightGold, style = MaterialTheme.typography.labelMedium)
                }
                MoonlightBeat.RESTORED -> {
                    MoonlightDialogue(if (lastChapter) guest?.profile?.displayName ?: chapter.resident else speaker, chapter.discovery)
                    val reward = if (lastChapter) "달빛 엽서 완성" else "엽서 조각 ${chapter.ordinal + 1} / ${MoonlightChapter.entries.size}"
                    Text(reward + if (earnedXp > 0) " · 시즌 +$earnedXp XP" else "", color = MoonlightGold,
                        style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (lastChapter) MoonlightButton("엽서 컬렉션", action = openAlbum)
                        else MoonlightButton("다음 이야기", action = nextChapter)
                        FilledTonalButton({ wardrobe = true }, Modifier.heightIn(min = 58.dp)) { Text("의상 보상") }
                    }
                }
            }
            if (failed) Text("방문 기록을 저장하지 못했어요. 버튼을 눌러 다시 시도해 주세요.", color = MoonlightGold,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MoonlightDialogue(speaker: String, line: String) {
    Text(speaker, color = MoonlightGold, style = RoadmateType.control)
    Text(line, color = GamePaper, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun MoonlightButton(label: String, enabled: Boolean = true, action: () -> Unit) {
    GameButton(label, action, Modifier.heightIn(min = 54.dp), primary = true, enabled = enabled)
}

@Composable
private fun MoonlightActor(companion: CompanionProfile, preview: CompanionPreview?, outfit: MoonlightOutfit?, modifier: Modifier) {
    val arrival = remember { Animatable(0f) }
    LaunchedEffect(Unit) { arrival.animateTo(1f, tween(1450, easing = FastOutSlowInEasing)) }
    val motion = modifier.graphicsLayer {
        alpha = .75f + arrival.value * .25f
        translationX = (arrival.value - 1f) * 16.dp.toPx()
    }
    Box(motion, contentAlignment = Alignment.Center) {
        if (preview != null) CompanionPreviewPortrait(preview, Modifier.fillMaxSize())
        else OriginalCompanionPortrait(companion, Modifier.fillMaxSize())
        if (outfit != null) Canvas(Modifier.matchParentSize()) {
            val (color, top) = when (outfit) {
                MoonlightOutfit.SCARF -> Color(0xFF387A60) to .60f
                MoonlightOutfit.HAT -> Color(0xFFFFB672) to .23f
                MoonlightOutfit.JACKET -> Color(0xFF558BB8) to .68f
                MoonlightOutfit.CAPE -> Color(0xFF9974A2) to .65f
            }
            val width = size.width * if (outfit == MoonlightOutfit.CAPE) .58f else .45f
            val height = size.height * if (outfit in setOf(MoonlightOutfit.JACKET, MoonlightOutfit.CAPE)) .13f else .055f
            drawRoundRect(color, Offset(center.x - width / 2f, size.height * top), Size(width, height), CornerRadius(height * .5f))
            drawCircle(MoonlightGold, height * .19f, Offset(center.x + width * .23f, size.height * top + height * .5f))
        }
    }
}

@Composable
private fun RestoredPostcard(chapter: MoonlightChapter, modifier: Modifier, atlas: ImageBitmap?) {
    val reveal = remember { Animatable(0f) }
    val illustration = artwork("journey/design/story-gangneung-postcard-art.png".takeIf { chapter == MoonlightChapter.ANMOK })
    LaunchedEffect(Unit) { reveal.animateTo(1f, tween(680, easing = FastOutSlowInEasing)) }
    Column(modifier.graphicsLayer {
        alpha = reveal.value
        scaleX = .82f + reveal.value * .18f
        scaleY = scaleX
        rotationZ = 8f - reveal.value * 11f
    }.background(MoonlightCream, RoundedCornerShape(16.dp)).padding(10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1.55f).clip(RoundedCornerShape(8.dp))) {
            if (illustration != null) Image(illustration, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            else {
                RoadmateLandscape(chapter.art, Expedition.GANGNEUNG.postcardIndex, Modifier.matchParentSize(), atlas)
                Canvas(Modifier.matchParentSize()) {
                    drawRect(Brush.verticalGradient(listOf(Color(0xAC172A54), Color(0x493D5F97))))
                    val center = Offset(size.width * .65f, size.height * .27f)
                    drawCircle(MoonlightGold, size.height * .065f, center)
                    repeat(4) { row ->
                        val width = size.height * (.055f + row * .009f)
                        val y = size.height * (.65f + row * .04f)
                        drawLine(MoonlightGold.copy(alpha = .82f - row * .12f), Offset(center.x - width, y),
                            Offset(center.x + width, y), size.height * .016f)
                    }
                }
            }
        }
        Text("강릉 · ${chapter.place.name}", Modifier.padding(start = 4.dp, top = 4.dp), color = MoonlightInk,
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun FragmentButton(label: String, fragment: PostcardFragment?, enabled: Boolean, modifier: Modifier, choose: () -> Unit) {
    Button(choose, modifier.heightIn(min = 70.dp), enabled = enabled, shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MoonlightCream, contentColor = MoonlightInk)) {
        if (fragment != null) Canvas(Modifier.size(44.dp)) {
            val unit = size.minDimension
            drawCircle(MoonlightInk, unit * .49f)
            drawCircle(MoonlightGold, unit * .13f, Offset(unit * .55f, unit * .31f))
            when (fragment) {
                PostcardFragment.CLOUD -> {
                    drawCircle(MoonlightCream, unit * .11f, Offset(unit * .4f, unit * .62f))
                    drawCircle(MoonlightCream, unit * .11f, Offset(unit * .57f, unit * .62f))
                }
                PostcardFragment.REFLECTION -> {
                    drawCircle(MoonlightGold, unit * .11f, Offset(unit * .55f, unit * .72f))
                    drawLine(Color(0xFFADCEE3), Offset(unit * .19f, unit * .52f), Offset(unit * .81f, unit * .52f), unit * .045f)
                }
                PostcardFragment.STAR -> {
                    val center = Offset(unit * .44f, unit * .68f)
                    drawLine(MoonlightCream, center.copy(x = unit * .31f), center.copy(x = unit * .57f), unit * .045f)
                    drawLine(MoonlightCream, center.copy(y = unit * .55f), center.copy(y = unit * .81f), unit * .045f)
                }
            }
        }
        if (fragment != null) Spacer(Modifier.width(9.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
