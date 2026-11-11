package io.booup.roadmate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.booup.roadmate.model.*

/** Kept for existing callers; the service shell provides distinct navigation destinations. */
@Composable
fun ProductContent(viewModel: RoadmateViewModel) = PostDriveContent(viewModel)

private fun rewardLabel(state: RewardClaimState) = when (state) {
    RewardClaimState.LOCKED -> "아직 잠겨 있어요"
    RewardClaimState.AVAILABLE -> "받을 수 있어요"
    RewardClaimState.CLAIMED -> "받았어요"
    RewardClaimState.UNCLAIMED_EXPIRED -> "시즌이 끝났어요"
}

private enum class SeasonTab(val label: String) { REWARDS("보상"), MISSIONS("미션"), ARCHIVE("지난 시즌") }

@Composable
fun SeasonContent(viewModel: RoadmateViewModel, preview: CompanionPreview? = null, openStory: (() -> Unit)? = null,
    insets: PaddingValues = PaddingValues(0.dp), openHome: () -> Unit = {}, priorityCare: @Composable () -> Unit = {}) {
    val ui by viewModel.progressUiState.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(SeasonTab.REWARDS) }
    var selected by remember { mutableStateOf<Pair<String, String>?>(null) }
    val active = ui.active
    val showReward: (SeasonView, RewardView) -> Unit = { season, item ->
        selected = season.season.seasonId to item.reward.rewardId
    }
    val owned = (listOfNotNull(active) + ui.archived).sumOf { season ->
        season.rewards.count { it.state == RewardClaimState.CLAIMED }
    }
    val atlas = artwork("journey/postcards.png")

    Box(Modifier.fillMaxSize()) {
        RoadmateLandscape("map-gangneung.png", 1, Modifier.matchParentSize(), atlas)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to GameInk, .5f to GameInk.copy(alpha = .65f), 1f to GameInk)))
        Column(Modifier.fillMaxSize().padding(insets).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameButton("‹ 동행", openHome)
                Text("시즌 패스", Modifier.weight(1f), color = GamePaper,
                    style = RoadmateType.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                GamePill("보유 보상 $owned")
                active?.let { GamePill("${it.progress.totalXp} XP") }
            }
            priorityCare()
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SeasonTab.entries.forEach { section -> GameButton(section.label, { tab = section }, primary = section == tab) }
                if (ui.contentAllowed && active != null && viewModel.canSimulateVisit) {
                    GameButton(if (active.progress.premiumEntitled) "프리미엄 시연 끄기" else "프리미엄 트랙 체험", {
                        viewModel.progressAction { it.previewPremium(active.season.seasonId, !active.progress.premiumEntitled) }
                    })
                }
            }
            ui.error?.let { Text("시즌 작업을 완료하지 못했어요. 저장 상태와 수령 조건을 확인해 주세요.",
                color = Color(0xFFFFBBB4), fontSize = 14.sp) }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val wide = maxWidth >= 680.dp
                val height = maxHeight
                key(tab) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!ui.contentAllowed) Text("시즌 기록을 불러오는 중", color = GamePaper)
                        else when (tab) {
                            SeasonTab.REWARDS -> if (active == null) {
                                Text("현재 진행 중인 시즌이 없어요.", color = GamePaper)
                            } else {
                                val featured = listOfNotNull(
                                    active.rewards.firstOrNull { it.reward.track == RewardTrack.FREE },
                                    active.rewards.lastOrNull { it.reward.track == RewardTrack.PREMIUM },
                                )
                                val storyTitle = if (openStory != null) "사라진 달빛 엽서" else active.season.title
                                if (wide) Row(Modifier.fillMaxWidth().heightIn(min = 176.dp).height(height.coerceAtLeast(176.dp)),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SeasonStoryTile(storyTitle, active.season.title, openStory, Modifier.weight(2f).fillMaxHeight())
                                    featured.forEach { item ->
                                        SeasonRewardTile(item, viewModel, Modifier.weight(1f).fillMaxHeight()) { showReward(active, item) }
                                    }
                                } else {
                                    SeasonStoryTile(storyTitle, active.season.title, openStory, Modifier.fillMaxWidth().height(192.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        featured.forEach { item ->
                                            SeasonRewardTile(item, viewModel, Modifier.weight(1f).height(165.dp)) { showReward(active, item) }
                                        }
                                    }
                                }
                            }
                            SeasonTab.MISSIONS -> {
                                Text("정차 후 미션", color = GamePaper, style = RoadmateType.cardTitle)
                                val missions = active?.missions.orEmpty()
                                if (missions.isEmpty()) Text("진행 중인 미션이 없어요.", color = GamePaper)
                                missions.filter { it.visible }.forEach { mission ->
                                    val hidden = mission.mission.visibility == MissionVisibility.HIDDEN_UNTIL_COMPLETED
                                    GamePill("${if (hidden) "히든 · " else ""}${mission.mission.title} · " +
                                        "${mission.count} / ${mission.mission.target}${if (mission.completed) " · 완료" else ""}")
                                }
                                if (missions.any { !it.visible }) GamePill("?  아직 발견하지 못한 히든 미션")
                            }
                            SeasonTab.ARCHIVE -> {
                                if (ui.archived.isEmpty()) Text("지난 시즌 기록이 없어요.", color = GamePaper)
                                ui.archived.forEach { archived ->
                                    Text("${archived.season.title} · Lv.${archived.progress.currentLevel}", color = GamePaper,
                                        style = RoadmateType.cardTitle)
                                    archived.rewards.forEach { item ->
                                        GameButton("${item.reward.title} · ${rewardLabel(item.state)}", { showReward(archived, item) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (ui.contentAllowed && tab == SeasonTab.REWARDS && active != null) {
                val levels = active.season.levels.indices.map { index ->
                    val number = index + 1
                    val rewards = active.rewards.filter { it.reward.level == number }
                    fun slots(track: RewardTrack) = rewards.filter { it.reward.track == track }.map { item ->
                        PassRailReward(item.reward.title, rewardSymbol(item), item.state) { showReward(active, item) }
                    }
                    PassRailLevel(number, slots(RewardTrack.FREE), slots(RewardTrack.PREMIUM))
                }
                GamePassRail(active, levels)
            }
        }
    }
    selected?.let { (seasonId, rewardId) ->
        val season = (listOfNotNull(active) + ui.archived).singleOrNull { it.season.seasonId == seasonId }
        val item = season?.rewards?.singleOrNull { it.reward.rewardId == rewardId }
        if (item != null) RewardDialog(item, preview, viewModel, dismiss = { selected = null }) {
            viewModel.progressAction { it.claim(seasonId, rewardId) }
        }
    }
}

private fun rewardSymbol(item: RewardView) = when {
    item.reward.pack != null -> "✦"
    item.reward.track == RewardTrack.PREMIUM -> "◇"
    else -> "✉"
}

@Composable
private fun SeasonStoryTile(title: String, seasonTitle: String, openStory: (() -> Unit)?, modifier: Modifier) {
    val frame = RoundedCornerShape(12.dp)
    Box(modifier.clip(frame).background(GameInk).border(1.dp, GameGold.copy(alpha = .65f), frame)) {
        RoadmateLandscape("story-gangneung-gangmun.png", 1, Modifier.matchParentSize(), artwork("journey/postcards.png"))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(.15f to Color.Transparent, 1f to GameInk)))
        Column(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (openStory != null) Text(seasonTitle, color = GameGold, style = RoadmateType.caption)
            Text(title, color = GamePaper, style = RoadmateType.heading)
            if (openStory != null) GameButton("강릉 이야기 시작", openStory, primary = true)
        }
    }
}

@Composable
private fun SeasonRewardTile(item: RewardView, viewModel: RoadmateViewModel, modifier: Modifier, open: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val premium = item.reward.track == RewardTrack.PREMIUM
    val pack = viewModel.companionPreviews.firstOrNull { it.profile.id == item.reward.pack?.companionProfileId }
    Box(modifier.clip(shape).background(GameInk).border(1.dp, GameGold.copy(alpha = .45f), shape)
        .clickable(role = Role.Button, onClickLabel = "보상 보기", onClick = open)) {
        if (pack != null) CompanionPreviewPortrait(pack, Modifier.matchParentSize())
        else RoadmateLandscape(if (premium) "story-gangneung-anmok.png" else "story-gangneung-gyeongpodae.png",
            1, Modifier.matchParentSize(), artwork("journey/postcards.png"))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(.05f to Color.Transparent, 1f to GameInk)))
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${if (premium) "프리미엄" else "무료"} · Lv.${item.reward.level}", color = GameGold, style = RoadmateType.caption)
            Text(item.reward.title, color = GamePaper, style = RoadmateType.cardTitle.copy(fontSize = 20.sp, lineHeight = 25.sp),
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(rewardLabel(item.state), color = GameMist, style = RoadmateType.caption, maxLines = 1)
        }
    }
}

@Composable
private fun RewardDialog(item: RewardView, preview: CompanionPreview?, viewModel: RoadmateViewModel,
    dismiss: () -> Unit, claim: () -> Unit) {
    val pack = item.reward.pack
    val packArt = viewModel.companionPreviews.firstOrNull { it.profile.id == pack?.companionProfileId }
    AlertDialog(onDismissRequest = dismiss, title = { Text(item.reward.title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            packArt?.let { CompanionPreviewPortrait(it, Modifier.fillMaxWidth().height(142.dp)) }
            if (item.reward.rewardId == "journey-animated" && preview != null) {
                PostcardArtwork(journeyPlaces[1], artwork("journey/postcards.png"), preview,
                    Modifier.fillMaxWidth().height(142.dp), animate = true)
            }
            Text("레벨 ${item.reward.level} · ${rewardLabel(item.state)}")
            Text(if (pack != null) "팩을 받으면 로드메이트로 직접 지정할 수 있어요." else "시즌에서 받은 꾸미기 보상으로 기록돼요.")
        }
    }, confirmButton = {
        if (item.state == RewardClaimState.CLAIMED && pack != null) TextButton(onClick = {
            dismiss()
            viewModel.selectCompanion(pack.companionProfileId)
        }) { Text("로드메이트로 지정") }
        else if (item.state == RewardClaimState.AVAILABLE) TextButton(onClick = {
            dismiss()
            claim()
        }) { Text("받기") }
    }, dismissButton = { TextButton(onClick = dismiss) { Text("닫기") } })
}
