package io.booup.roadmate

import androidx.compose.foundation.horizontalScroll
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

@Composable
fun SeasonContent(viewModel: RoadmateViewModel, preview: CompanionPreview? = null) {
    val ui by viewModel.progressUiState.collectAsStateWithLifecycle()
    if (!ui.contentAllowed) return
    var selected by remember { mutableStateOf<Pair<String, String>?>(null) }
    val showReward: (SeasonView, RewardView) -> Unit = { season, reward ->
        selected = season.season.seasonId to reward.reward.rewardId
    }
    Text("시즌 패스", style = MaterialTheme.typography.headlineMedium)
    Text("드라이브의 기억이 쌓이면 여행 보상이 열려요.", style = MaterialTheme.typography.bodyLarge)
    ui.error?.let { Text("시즌 작업을 완료하지 못했어요. 저장 상태와 수령 조건을 확인해 주세요.",
        color = MaterialTheme.colorScheme.error) }
    val active = ui.active
    if (active == null) RoadmateCard { Text("현재 진행 중인 시즌이 없어요.") }
    else {
        RoadmateCard(tinted = true) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoadmateBadge(if (preview != null) "가상 콜라보 · 내부 시즌" else "로드메이트 · 로컬 시즌", demo = preview != null)
                    Text(active.season.title, style = MaterialTheme.typography.titleLarge)
                    Text("레벨 ${active.progress.currentLevel} / ${active.season.levels.size}", style = MaterialTheme.typography.headlineLarge)
                }
                preview?.let { CompanionPreviewPortrait(it, Modifier.size(128.dp)) }
            }
            val next = active.nextLevelXp?.let { "다음 레벨까지 ${it} XP" } ?: "마지막 레벨에 도달했어요."
            Text("${active.progress.totalXp} XP · $next")
            LinearProgressIndicator(progress = {
                (active.progress.totalXp.toFloat() / active.season.levels.last().coerceAtLeast(1)).coerceIn(0f, 1f)
            }, modifier = Modifier.fillMaxWidth().height(10.dp))
            active.rewards.lastOrNull { it.reward.pack != null }?.let {
                Text("완주 보상 · ${it.reward.title}", style = MaterialTheme.typography.titleMedium)
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            active.season.levels.forEachIndexed { index, xp ->
                val level = index + 1
                RoadmateBadge("${if (level <= active.progress.currentLevel) "✓ " else ""}Lv.$level · $xp XP")
            }
        }
        if (viewModel.canSimulateVisit) OutlinedButton(onClick = {
            viewModel.progressAction { it.previewPremium(active.season.seasonId, !active.progress.premiumEntitled) }
        }, modifier = Modifier.heightIn(min = 56.dp)) {
            Text(if (active.progress.premiumEntitled) "프리미엄 시연 끄기" else "프리미엄 트랙 체험")
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val show: (RewardView) -> Unit = { showReward(active, it) }
            if (maxWidth >= 820.dp) Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PassTrack(active, RewardTrack.FREE, Modifier.weight(1f), show)
                PassTrack(active, RewardTrack.PREMIUM, Modifier.weight(1f), show)
            } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PassTrack(active, RewardTrack.FREE, Modifier, show)
                PassTrack(active, RewardTrack.PREMIUM, Modifier, show)
            }
        }
        Text("정차 후 미션", style = MaterialTheme.typography.titleLarge)
        active.missions.filter { it.visible }.forEach { mission ->
            RoadmateCard(tinted = mission.completed) {
                if (mission.mission.visibility == MissionVisibility.HIDDEN_UNTIL_COMPLETED) RoadmateBadge("히든 미션 발견")
                Text(mission.mission.title, style = MaterialTheme.typography.titleMedium)
                Text("${mission.count} / ${mission.mission.target}" + if (mission.completed) " · 완료" else " · 진행 중")
            }
        }
        if (active.missions.any { !it.visible }) RoadmateCard { Text("?  아직 발견하지 못한 히든 미션") }
    }
    Text("보상은 드라이브 기록을 삭제해도 유지해요. 프리미엄은 시연에서 체험할 수 있어요.",
        style = MaterialTheme.typography.bodyMedium)
    ui.archived.forEach { archived ->
        RoadmateCard {
            Text("지난 시즌 · ${archived.season.title} · 레벨 ${archived.progress.currentLevel}", style = MaterialTheme.typography.titleMedium)
            archived.rewards.forEach { item ->
                TextButton(onClick = { showReward(archived, item) }, modifier = Modifier.heightIn(min = 54.dp)) {
                    Text(item.reward.title + " · " + rewardLabel(item.state))
                }
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

@Composable
private fun PassTrack(active: SeasonView, track: RewardTrack, modifier: Modifier, onReward: (RewardView) -> Unit) {
    val premium = track == RewardTrack.PREMIUM
    RoadmateCard(modifier, tinted = !premium) {
        Text(if (premium) "프리미엄 트랙" else "무료 트랙", style = MaterialTheme.typography.titleLarge)
        if (premium) RoadmateBadge(if (active.progress.premiumEntitled) "SIMULATED · 체험 열림" else "잠김 · 보상 미리보기", demo = true)
        val rewards = active.rewards.filter { it.reward.track == track }
        if (rewards.isEmpty()) Text("이 트랙에 등록된 보상이 없어요.")
        rewards.sortedBy { it.reward.level }.forEach { item ->
            HorizontalDivider()
            Text("Lv.${item.reward.level} · ${item.reward.title}", style = MaterialTheme.typography.titleMedium)
            Text(rewardLabel(item.state), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { onReward(item) }, modifier = Modifier.heightIn(min = 54.dp)) {
                Text(if (item.state == RewardClaimState.AVAILABLE) "보상 확인하고 받기" else "보상 자세히")
            }
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
