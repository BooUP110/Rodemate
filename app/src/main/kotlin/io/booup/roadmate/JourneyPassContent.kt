package io.booup.roadmate

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.booup.roadmate.model.RegionId
import io.booup.roadmate.model.SeasonView

private enum class PassPage(val label: String) {
    LOBBY("시즌 로비"), DETAIL("패스 상세"), TRIAL("동반자 체험"),
    STAMPS("미션 보드"), DRAW("패스권 뽑기"), LIBRARY("내 패스함")
}

@Composable
internal fun JourneyPassContent(viewModel: RoadmateViewModel, game: JourneyGame, parkedStatus: String,
    modifier: Modifier = Modifier, openAlbum: () -> Unit, openMemory: () -> Unit, openHome: () -> Unit,
    tryOnHome: (CompanionPreview) -> Unit, priorityCare: @Composable () -> Unit) {
    val progress by viewModel.progressUiState.collectAsStateWithLifecycle()
    val trip by viewModel.tripUiState.collectAsStateWithLifecycle()
    val data by game.state.collectAsStateWithLifecycle()
    val season = progress.active?.takeIf { it.season.seasonId == JOURNEY_SEASON_ID }
    val archive = progress.archived.firstOrNull { it.season.seasonId == JOURNEY_SEASON_ID }
    val owned = remember(trip.storage.postcards) { game.ownedCards() }
    val stamps = remember(data, trip.storage.postDrive, owned) { game.stamps(data, owned) }
    var pass by remember { mutableStateOf(JourneyPass.TRAVEL) }
    var page by remember { mutableStateOf(PassPage.LOBBY) }
    var drawResult by remember { mutableStateOf<JourneyPass?>(null) }
    var opened by remember { mutableStateOf(false) }
    var drawing by remember { mutableStateOf(false) }
    var drawFailed by remember { mutableStateOf(false) }
    val world = when (page) {
        PassPage.LOBBY, PassPage.LIBRARY -> JourneyWorld.POST
        PassPage.DRAW -> drawResult?.takeIf { opened }?.world ?: JourneyWorld.POST
        else -> pass.world
    }
    fun show(target: PassPage, themedPass: JourneyPass = pass) { pass = themedPass; page = target }
    fun goBack() {
        if (page == PassPage.LOBBY) openHome()
        else if (page == PassPage.TRIAL) show(PassPage.DETAIL)
        else show(PassPage.LOBBY)
    }
    BackHandler { goBack() }

    Box(modifier) {
        WorldBackdrop(world, Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            .0f to world.deep.copy(alpha = .93f), .20f to world.deep.copy(alpha = .48f),
            .45f to Color.Transparent, .74f to world.deep.copy(alpha = .09f), 1f to world.deep.copy(alpha = .68f),
        )))
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = ::goBack, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (page == PassPage.LOBBY) "‹  동행" else "‹  뒤로", color = Color.White,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.weight(1f))
                Text(parkedStatus, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.background(world.deep.copy(alpha = .72f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp))
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(PassPage.LOBBY, PassPage.STAMPS, PassPage.DRAW, PassPage.LIBRARY).forEach { target ->
                    TextButton(onClick = { show(target) }, modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = if (page == target) world.paper else world.deep.copy(alpha = .60f),
                            contentColor = if (page == target) world.deep else Color.White),
                        contentPadding = PaddingValues(horizontal = 11.dp)) {
                        Text(target.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            priorityCare()
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val stageHeight = maxHeight
                val wide = maxWidth >= 730.dp
                key(page, pass) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = stageHeight)
                        .padding(top = 12.dp, bottom = 15.dp),
                        verticalArrangement = if (page in setOf(PassPage.DETAIL, PassPage.DRAW, PassPage.TRIAL))
                            Arrangement.SpaceBetween else Arrangement.spacedBy(14.dp)) {
                        val summary = season ?: archive
                        if (!progress.contentAllowed) {
                            WorldTitle("시즌 기록을 불러오는 중", Color.White)
                        } else if (summary == null) {
                            WorldPaper(world, Modifier.fillMaxWidth()) {
                                SeasonContent(viewModel, viewModel.companionPreviews.firstOrNull())
                            }
                        } else when (page) {
                            PassPage.LOBBY -> SeasonGallery(summary, data, wide) { show(PassPage.DETAIL, it) }
                            PassPage.DETAIL -> PassJourney(viewModel, game, data, pass, summary, owned, wide,
                                openAlbum, { show(PassPage.DRAW) }, { show(PassPage.TRIAL) }, { show(PassPage.LIBRARY) }, openHome)
                            PassPage.TRIAL -> CompanionTrial(pass, viewModel, wide, tryOnHome) { show(PassPage.DETAIL) }
                            PassPage.STAMPS -> {
                                val canChoose = game.canChoose(data, stamps) && season != null
                                WorldTitle("여행 여권", Color.White)
                                StampBoard(world, stamps, game.hasLine(stamps), canChoose, openAlbum, openMemory, { show(PassPage.TRIAL) })
                                if (canChoose) WorldPaper(world, Modifier.fillMaxWidth()) {
                                    WorldTitle("패스 선택권 1장", world.deep)
                                    game.candidates(data).forEach { item ->
                                        WorldAction("${item.title} 선택", world, {
                                            viewModel.journeyAction { if (choose(item)) show(PassPage.DETAIL, item) }
                                        })
                                    }
                                    if (game.candidates(data).isEmpty()) Text("모든 시즌 패스권을 모았어요.")
                                }
                            }
                            PassPage.DRAW -> {
                                val candidates = game.candidates(data)
                                val tickets = game.tickets(data, owned)
                                TravelPost(world, tickets, candidates, season != null, drawing, drawFailed, drawResult, opened, wide,
                                    draw = {
                                        drawing = true
                                        drawFailed = false
                                        viewModel.journeyAction {
                                            drawResult = draw()
                                            drawFailed = drawResult == null
                                            opened = false
                                            drawing = false
                                        }
                                    }, reveal = { opened = true }, openPass = { received ->
                                        drawResult = null
                                        show(PassPage.DETAIL, received)
                                    }, openAlbum = openAlbum, openLibrary = {
                                        drawResult = null
                                        show(PassPage.LIBRARY)
                                    })
                            }
                            PassPage.LIBRARY -> PassLibrary(viewModel, data, archive, season != null,
                                openHome, { show(PassPage.DETAIL, it) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonGallery(season: SeasonView, data: JourneyGameState, wide: Boolean, choose: (JourneyPass) -> Unit) {
    WorldEyebrow("ROAD MATE  ·  SEASON 01  ·  내부 시연", JourneyWorld.POST.gold)
    WorldTitle("세 가지 여행의 세계", Color.White)
    Text("공유 ${season.progress.totalXp} XP  ·  Lv.${season.progress.currentLevel}", color = Color.White, fontSize = 16.sp)
    val poster: @Composable (JourneyPass, Modifier) -> Unit = { pass, modifier ->
        val world = pass.world
        val shape = RoundedCornerShape(22.dp)
        Box(modifier.height(if (wide) 270.dp else 212.dp).clip(shape).border(1.dp, world.gold, shape)
            .clickable(role = Role.Button, onClickLabel = "${pass.title} 패스 보기") { choose(pass) }) {
            WorldBackdrop(world, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(.28f to Color.Transparent,
                1f to world.deep.copy(alpha = .96f))))
            Column(Modifier.align(Alignment.BottomStart).padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                WorldEyebrow(if (data.grants.any { it.pass == pass }) "PREMIUM  ·  보유" else "SEASON PASS", world.gold)
                Text(pass.title, color = Color.White, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold)
                Text("패스 보기  ›", color = world.gold, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        JourneyPass.entries.forEach { poster(it, Modifier.weight(1f)) }
    } else JourneyPass.entries.forEach { poster(it, Modifier.fillMaxWidth()) }
}

@Composable
private fun ColumnScope.PassJourney(viewModel: RoadmateViewModel, game: JourneyGame, data: JourneyGameState, pass: JourneyPass,
    season: SeasonView, owned: Set<RegionId>, wide: Boolean, openAlbum: () -> Unit, openDraw: () -> Unit,
    openTrial: () -> Unit, openLibrary: () -> Unit, openHome: () -> Unit) {
    val world = pass.world
    val premium = data.grants.any { it.pass == pass }
    var rewardTier by remember { mutableStateOf<Int?>(null) }
    var finalPack by remember { mutableStateOf(false) }
    val level = season.progress.currentLevel
    val levels = season.season.levels
    val fraction = (season.progress.totalXp.toFloat() / levels.last().coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        WorldEyebrow("${pass.theme}   ·   ${if (season.archived) "지난 시즌" else if (premium) "프리미엄 이용 중" else "무료 트랙"}", world.gold)
        WorldTitle(pass.title, Color.White, Modifier.widthIn(max = 700.dp))
    }
    Spacer(Modifier.height(if (wide) 88.dp else 150.dp))
    val collected = when (pass) {
        JourneyPass.TRAVEL -> journeyPlaces.count { it.id in owned }
        JourneyPass.STARS -> journeyPlaces.count { it.id in owned && it.id.value in data.starredCards }
        JourneyPass.COAST -> coastalPlaces.count { it.id in owned }
    }
    val total = if (pass == JourneyPass.COAST) coastalPlaces.size else journeyPlaces.size
    WorldPaper(world, Modifier.align(if (wide) Alignment.Start else Alignment.CenterHorizontally).widthIn(max = 612.dp).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            WorldEyebrow("✦  여행의 발자취", world.accent)
            Text("Lv.$level / ${levels.size}", color = world.deep, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Text("${season.progress.totalXp} XP  ·  ${season.nextLevelXp?.let { "다음 레벨까지 $it XP" } ?: "시즌 완주"}",
            color = world.deep, fontSize = 14.sp, lineHeight = 18.sp)
        WorldRail(world, level, fraction) { rewardTier = it }
        Text("${pass.subtitle}  $collected/$total", color = world.deep, fontSize = 13.sp, lineHeight = 18.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WorldAction("엽서 도감", world, openAlbum, primary = false)
            WorldAction("동반자 체험", world, openTrial, primary = false)
            if (!premium && !season.archived) WorldAction("패스권 뽑기", world, openDraw)
        }
    }

    rewardTier?.let { tier ->
        AlertDialog(onDismissRequest = { rewardTier = null }, containerColor = world.paper,
            title = { WorldTitle("Lv.${rewardLevel(tier)} 여행 보상", world.deep) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(false, true).forEach { isPremium ->
                        val claimed = rewardKey(pass, tier, isPremium) in data.claimed
                        val pack = isPremium && tier == 3
                        val label = if (isPremium) {
                            if (pack) "완주 동반자 팩" else pass.rewards[tier]
                        } else listOf(pass.rewards.first(), "${pass.theme} 배지", "완주 기념 소인")[tier - 1]
                        val eligible = !season.archived && level >= rewardLevel(tier) && (!isPremium || premium) && !claimed
                        if (isPremium) HorizontalDivider(color = world.accent.copy(alpha = .24f))
                        WorldEyebrow(if (isPremium) "프리미엄 트랙" else "무료 트랙", world.accent)
                        Text(label, color = world.deep, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        Text(when {
                            claimed -> "수령 완료"
                            eligible -> "받을 수 있어요"
                            season.archived -> "시즌 종료"
                            isPremium && !premium -> "프리미엄 패스권 필요"
                            else -> "레벨 ${rewardLevel(tier)}에서 해금"
                        }, color = world.deep, fontSize = 14.sp)
                        if (eligible || (pack && claimed)) WorldAction(if (pack) "동반자 팩 열기" else "보상 받기", world, {
                            if (pack) { rewardTier = null; finalPack = true }
                            else viewModel.journeyAction { game.claim(pass, tier, isPremium) }
                        })
                    }
                }
            }, confirmButton = { WorldAction("닫기", world, { rewardTier = null }, primary = false) })
    }
    if (finalPack) {
        val claimed = rewardKey(pass, 3, true) in data.claimed
        val choices = viewModel.companionPreviews.filter { it.profile.id in pass.companionIds() && (!claimed || it.profile.id in data.companions) }
        AlertDialog(onDismissRequest = { finalPack = false }, containerColor = world.paper,
            title = { WorldTitle(if (claimed) "동반자 팩 획득" else "완주 동반자 고르기", world.deep) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    choices.forEach { mascot ->
                        WorldAction(if (claimed) "${mascot.profile.displayName} 로드메이트로 지정" else "${mascot.profile.displayName} 받기", world, {
                            if (claimed) viewModel.equipJourneyCompanion(mascot.profile.id, openHome)
                            else viewModel.journeyAction { game.claim(pass, 3, true, mascot.profile.id) }
                        })
                    }
                    if (choices.isEmpty()) Text("동반자 미리보기 에셋을 확인해 주세요.")
                }
            }, confirmButton = { WorldAction(if (claimed) "내 패스함" else "닫기", world, { finalPack = false; if (claimed) openLibrary() }, primary = false) })
    }
}

@Composable
private fun ColumnScope.TravelPost(world: JourneyWorld, tickets: Int, candidates: List<JourneyPass>, active: Boolean,
    drawing: Boolean, failed: Boolean, result: JourneyPass?, opened: Boolean, wide: Boolean,
    draw: () -> Unit, reveal: () -> Unit, openPass: (JourneyPass) -> Unit, openAlbum: () -> Unit, openLibrary: () -> Unit) {
    var odds by remember { mutableStateOf(false) }
    val revealed = opened && result != null
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        WorldEyebrow("TRAVEL POST  ·  패스권 뽑기", world.gold)
        WorldTitle(if (revealed) "여행 패스권 도착!" else "여행 우체국", Color.White)
        Text("무료 뽑기권 ${tickets}장  ·  미보유 패스 ${candidates.size}종", color = Color.White, fontSize = 15.sp)
    }
    val scale by animateFloatAsState(if (revealed) .78f else if (result != null) 1.06f else .91f,
        spring(dampingRatio = .58f), label = "travel envelope")
    Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp)
        .width(if (wide) 190.dp else 210.dp).height(if (wide) 115.dp else 132.dp)
        .graphicsLayer { scaleX = scale; scaleY = scale; rotationZ = if (revealed) 0f else -5f }
        .clip(RoundedCornerShape(16.dp))
        .then(if (result != null && !revealed) Modifier.clickable(role = Role.Button, onClickLabel = "여행 봉투 열기", onClick = reveal) else Modifier)) {
        IllustratedEnvelope(world, sealed = !revealed, modifier = Modifier.matchParentSize())
    }
    WorldPaper(world, Modifier.align(Alignment.CenterHorizontally).widthIn(max = 544.dp).fillMaxWidth()) {
        AnimatedVisibility(revealed) {
            Text("${result?.title.orEmpty()} 프리미엄 패스권", color = world.deep, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        if (revealed) WorldAction("획득한 패스 열기", world, { openPass(result) }, Modifier.fillMaxWidth())
        else if (result != null) {
            WorldAction("여행 봉투 열기", world, reveal, Modifier.fillMaxWidth())
            WorldAction("바로 결과 보기", world, reveal, primary = false)
        } else {
            val available = active && tickets > 0 && candidates.isNotEmpty() && !drawing
            val message = when {
                failed -> "패스권을 저장하지 못했어요. 다시 시도해 주세요."
                !active -> "이번 시즌의 뽑기가 종료됐어요."
                candidates.isEmpty() -> "모든 시즌 패스권을 모았어요."
                tickets == 0 -> "강릉·제주 엽서를 모으면 두 번째 뽑기권이 열려요."
                else -> "미보유 패스에서 한 장을 받아요."
            }
            Text(message, color = world.deep, fontSize = 14.sp)
            WorldAction(if (drawing) "봉투를 준비하는 중" else "패스권 1회 뽑기", world, draw, Modifier.fillMaxWidth(), enabled = available)
            if (tickets == 0 && candidates.isNotEmpty()) WorldAction("해안 엽서 보러 가기", world, openAlbum, primary = false)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WorldAction("확률 보기", world, { odds = true }, primary = false)
            WorldAction("내 패스함 · 뽑기 기록", world, openLibrary, primary = false)
        }
    }
    if (odds) AlertDialog(onDismissRequest = { odds = false }, containerColor = world.paper,
        title = { WorldTitle("패스권 획득 확률", world.deep) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("보유 중인 패스권은 후보에서 빠져요.", color = world.deep)
                candidates.forEach { candidate ->
                    Text("${candidate.title}  ·  ${if (candidates.size == 3) "1/3 (약 33.3%)" else "${100 / candidates.size}%"}", color = world.deep)
                }
                if (candidates.isEmpty()) Text("남은 패스권이 없어요.", color = world.deep)
            }
        }, confirmButton = { WorldAction("닫기", world, { odds = false }, primary = false) })
}

@Composable
private fun StampBoard(world: JourneyWorld, stamps: Set<JourneyStamp>, line: Boolean, choice: Boolean,
    openAlbum: () -> Unit, openMemory: () -> Unit, openTrial: () -> Unit) {
    WorldPaper(world, Modifier.widthIn(max = 700.dp).fillMaxWidth()) {
        WorldEyebrow("MISSION STAMPS  ·  ${stamps.size}/9", world.accent)
        Text(if (choice) "한 줄 완성! 미보유 패스를 골라 받을 수 있어요." else if (line) "한 줄을 완성했어요." else "가로·세로·대각선 중 한 줄을 채워 보세요.", color = world.deep)
        JourneyStamp.entries.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { stamp ->
                    val done = stamp in stamps
                    Column(Modifier.weight(1f).heightIn(min = 76.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (done) world.accent.copy(alpha = .17f) else world.paper).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (done) "✓" else if (stamp == JourneyStamp.SECRET) "?" else "○", color = world.accent)
                        Text(if (stamp == JourneyStamp.SECRET && !done) "히든 미션" else stamp.title,
                            color = world.deep, fontSize = 13.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
        WorldAction("엽서 펼치고 별 붙이기", world, openAlbum)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WorldAction("드라이브 회고", world, openMemory, primary = false)
            WorldAction("차량 케어 체험", world, openTrial, primary = false)
        }
    }
}

@Composable
private fun ColumnScope.CompanionTrial(pass: JourneyPass, viewModel: RoadmateViewModel, wide: Boolean,
    tryOnHome: (CompanionPreview) -> Unit, openPass: () -> Unit) {
    val world = pass.world
    val choices = viewModel.companionPreviews.filter { it.profile.id in pass.companionIds() }
    var preview by remember(pass) { mutableStateOf(choices.firstOrNull()) }
    var showCare by remember(preview) { mutableStateOf(false) }
    Column {
        WorldEyebrow(pass.title, world.gold)
        WorldTitle("동반자 체험", Color.White)
    }
    preview?.let { CompanionPreviewPortrait(it, Modifier.align(Alignment.CenterHorizontally).size(if (wide) 132.dp else 166.dp)) }
    WorldPaper(world, Modifier.align(Alignment.CenterHorizontally).widthIn(max = 544.dp).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            choices.forEach { item ->
                WorldAction(item.profile.displayName, world, { preview = item }, primary = item == preview)
            }
        }
        Text("시연 상황 · 실내가 더워졌어요.", color = world.deep, fontSize = 15.sp)
        if (showCare) Text(when (preview?.profile?.id?.value) {
            "preview:usagi" -> "우라! 실내가 더워요. 23°C로 낮춰볼까요?"
            "preview:hachiware" -> "조금 더워졌네요! 23°C는 어때요?"
            "preview:starlight" -> "차 안을 살짝 식혀볼까요? 23°C를 제안해요."
            "preview:waveotter" -> "시원한 바람 어때요? 23°C로 맞춰볼까요?"
            else -> "조금 더워요. 23°C로 낮추면 편안할 것 같아요."
        }, color = world.deep, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WorldAction("공조 제안 체험", world, { showCare = true; viewModel.journeyAction { careChecked() } })
            WorldAction("패스 상세 보기", world, openPass, primary = false)
        }
        preview?.let { selected -> WorldAction("동행 홈에서 체험", world, { tryOnHome(selected) }) }
    }
}

@Composable
private fun PassLibrary(viewModel: RoadmateViewModel, data: JourneyGameState, archive: SeasonView?, active: Boolean,
    openHome: () -> Unit, openPass: (JourneyPass) -> Unit) {
    val world = JourneyWorld.POST
    val art = artwork("journey/postcards.png")
    WorldTitle("내 패스함", Color.White)
    WorldPaper(world, Modifier.fillMaxWidth()) {
        WorldEyebrow("PASS COLLECTION  ·  ${data.grants.size}종", world.accent)
        if (data.grants.isEmpty()) Text("여행 우체국 또는 미션 보드에서 패스권을 받을 수 있어요.")
        data.grants.forEach { grant ->
            Text("${grant.pass.title} · ${if (active) "진행 중" else "종료"} · ${if (grant.drawn) "뽑기" else "선택권"}", color = world.deep)
            WorldAction("패스 열기", grant.pass.world, { openPass(grant.pass) }, primary = false)
        }
        archive?.let { Text("지난 시즌 · Lv.${it.progress.currentLevel} · 받은 보상 ${data.claimed.size}개") }
    }
    WorldPaper(world, Modifier.fillMaxWidth()) {
        WorldEyebrow("COMPANION MEMORIES", world.accent)
        Text("동반자와의 추억", color = world.deep, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (data.companions.isEmpty()) Text("시즌을 완주하고 동반자 팩을 열어 보세요.")
        data.companions.forEach { id ->
            val mascot = viewModel.companionPreviews.firstOrNull { it.profile.id == id }
            if (mascot != null) {
                Text(mascot.profile.displayName, color = world.deep, fontWeight = FontWeight.Bold)
                WorldAction(if (data.equipped == id) "동행 홈에서 보기" else "로드메이트로 지정", world,
                    { viewModel.equipJourneyCompanion(id, openHome) }, primary = false)
                data.memories.firstOrNull { it.companion == id }?.let { memory ->
                    val place = journeyPlaces.firstOrNull { it.name == memory.region }
                    PostcardArtwork(place, art, mascot, Modifier.fillMaxWidth().height(128.dp), animate = true)
                    Text("${memory.region}에서의 첫 동행", color = world.deep)
                    Text(journeyMemoryLine(id.value, memory.region), color = world.deep)
                } ?: Text("동반자로 지정한 뒤 한 번 여행하면 첫 동행 엽서가 열려요.")
            }
        }
    }
}

private fun journeyMemoryLine(companion: String, place: String) = when (companion) {
    "preview:usagi" -> "우라! $place, 한 장 더 간직했어요!"
    "preview:starlight" -> "${place}에서 반짝인 순간을 별 하나로 남겼어요."
    "preview:waveotter" -> "${place}에 함께 갔던 날을 조개처럼 소중히 간직할래요."
    else -> "${place}에 함께 갔던 날을 엽서에 남겼어요."
}
