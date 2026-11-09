package io.booup.roadmate

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
fun SeasonContent(viewModel: RoadmateViewModel) {
    val ui by viewModel.progressUiState.collectAsStateWithLifecycle()
    if (!ui.contentAllowed) return
    var selected by remember { mutableStateOf<Pair<String, String>?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("작은 기억이 모이는 시즌", style = MaterialTheme.typography.headlineMedium)
        Text("드라이브를 정리하고, 나만의 동행을 꾸며요.", style = MaterialTheme.typography.bodyLarge)
        ui.error?.let { Text("시즌 작업을 완료하지 못했어요. 저장 상태와 수령 조건을 다시 확인해 주세요.",
            color = MaterialTheme.colorScheme.error) }
        val active = ui.active
        if (active == null) RoadmateCard { Text("현재 진행 중인 시즌이 없어요.") }
        else {
            RoadmateCard(tinted = true) {
                RoadmateBadge("무료 트랙 · 로컬 예시 시즌")
                Text(active.season.title, style = MaterialTheme.typography.titleLarge)
                Text("레벨 " + active.progress.currentLevel, style = MaterialTheme.typography.headlineLarge)
                Text(active.progress.totalXp.toString() + " XP · " +
                    (active.nextLevelXp?.let { "다음 레벨까지 " + it + " XP" } ?: "마지막 레벨에 도달했어요."))
                LinearProgressIndicator(progress = {
                    (active.progress.totalXp.toFloat() / active.season.levels.last().coerceAtLeast(1)).coerceIn(0f, 1f)
                }, modifier = Modifier.fillMaxWidth().height(12.dp))
                active.rewards.firstOrNull { it.reward.track == RewardTrack.FREE && it.state != RewardClaimState.CLAIMED }?.let {
                    Text("다음 보상  " + it.reward.title, style = MaterialTheme.typography.titleMedium)
                }
            }
            Text("무료 보상", style = MaterialTheme.typography.titleLarge)
            active.rewards.filter { it.reward.track == RewardTrack.FREE }.forEach { item ->
                RoadmateCard {
                    Text(item.reward.title, style = MaterialTheme.typography.titleMedium)
                    Text("레벨 " + item.reward.level + " · " + rewardLabel(item.state), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { selected = active.season.seasonId to item.reward.rewardId },
                        modifier = Modifier.heightIn(min = 56.dp)) { Text(if (item.state == RewardClaimState.AVAILABLE) "보상 확인하고 받기" else "보상 자세히") }
                }
            }
            if (active.progress.premiumEntitled) {
                RoadmateBadge("SIMULATED · 시연용 추가 보상", demo = true)
                active.rewards.filter { it.reward.track == RewardTrack.PREMIUM }.forEach { item ->
                    TextButton(onClick = { selected = active.season.seasonId to item.reward.rewardId },
                        modifier = Modifier.heightIn(min = 56.dp)) { Text(item.reward.title + " · " + rewardLabel(item.state)) }
                }
            }
            Text("정차 후 작은 미션", style = MaterialTheme.typography.titleLarge)
            active.missions.filter { it.visible }.forEach { mission ->
                RoadmateCard {
                    Text(mission.mission.title, style = MaterialTheme.typography.titleMedium)
                    Text(mission.count.toString() + " / " + mission.mission.target + if (mission.completed) " · 완료" else " · 진행 중")
                }
            }
        }
        RoadmateCard {
            Text("나의 발견", style = MaterialTheme.typography.titleLarge)
            Text(ui.exploration.discoveredCount.toString() + "개 지역", style = MaterialTheme.typography.headlineMedium)
            ui.exploration.percentage?.let {
                Text("시연 지역 목록 중 " + it.toInt() + "%", style = MaterialTheme.typography.bodyMedium)
            } ?: Text("확인된 지역의 발견 개수만 보여드려요.", style = MaterialTheme.typography.bodyMedium)
        }
        Text("회고를 건너뛰어도 정리 보상은 같아요. 거리·운전 시간·야간 운전·경고에는 추가 보상이 없어요.",
            style = MaterialTheme.typography.bodyMedium)
        Text("현재는 구매 기능을 제공하지 않아요. 시즌 일정과 보상은 로컬 예시이며, 받은 보상은 자동 적용하지 않아요.",
            style = MaterialTheme.typography.bodyMedium)
        Text("시즌 진행도와 받은 보상은 드라이브 기록을 삭제해도 유지해요.", style = MaterialTheme.typography.bodyMedium)
        ui.archived.forEach { archived ->
            Text("지난 시즌 · " + archived.season.title + " · 레벨 " + archived.progress.currentLevel,
                style = MaterialTheme.typography.titleMedium)
            archived.rewards.forEach { item ->
                TextButton(onClick = { selected = archived.season.seasonId to item.reward.rewardId },
                    modifier = Modifier.heightIn(min = 56.dp)) { Text(item.reward.title + " · " + rewardLabel(item.state)) }
            }
        }
    }
    selected?.let { (seasonId, rewardId) ->
        val item = (listOfNotNull(ui.active) + ui.archived).singleOrNull { it.season.seasonId == seasonId }
            ?.rewards?.singleOrNull { it.reward.rewardId == rewardId }
        if (item != null) AlertDialog(onDismissRequest = { selected = null },
            title = { Text(item.reward.title) },
            text = { Text("레벨 " + item.reward.level + " · " + rewardLabel(item.state) +
                "\n꾸미기 표현 보상이에요. 안전 기능과 무관하며 수령해도 자동 적용하지 않아요.") },
            confirmButton = { TextButton(enabled = item.state == RewardClaimState.AVAILABLE, onClick = {
                selected = null; viewModel.progressAction { it.claim(seasonId, rewardId) }
            }) { Text("받기") } },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("닫기") } })
    }
}
