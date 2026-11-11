package io.booup.roadmate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.booup.roadmate.model.RewardClaimState
import io.booup.roadmate.model.SeasonView

internal data class PassRailReward(
    val title: String, val symbol: String, val state: RewardClaimState, val open: () -> Unit,
)

internal data class PassRailLevel(val number: Int, val free: List<PassRailReward>, val premium: List<PassRailReward>)

@Composable
internal fun GamePassRail(season: SeasonView, levels: List<PassRailLevel>, modifier: Modifier = Modifier) {
    val xp = season.progress.totalXp
    val cap = season.season.levels.lastOrNull()?.coerceAtLeast(1) ?: 1
    val reached = season.progress.currentLevel
    Column(modifier.fillMaxWidth().background(GameInk.copy(alpha = .97f))
        .border(1.dp, GameGold.copy(alpha = .35f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("패스 트랙", color = GamePaper, style = RoadmateType.control)
            LinearProgressIndicator(progress = { (xp.toFloat() / cap).coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f).height(4.dp), color = GameGold, trackColor = Color(0xFF364450))
            Text("Lv.$reached · $xp XP", color = GameGold, style = RoadmateType.metric, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Column(Modifier.width(80.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Spacer(Modifier.height(20.dp))
                TrackLabel("무료")
                TrackLabel("프리미엄")
            }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                levels.forEach { level ->
                    Column(Modifier.width(146.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.height(20.dp), contentAlignment = Alignment.Center) {
                            Text("Lv.${level.number}", color = if (level.number <= reached) GameGold else GameMist,
                                style = RoadmateType.metric.copy(fontSize = 13.sp, lineHeight = 16.sp), maxLines = 1)
                        }
                        RewardSlot(level.free)
                        RewardSlot(level.premium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackLabel(text: String) {
    Box(Modifier.height(46.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, color = GameMist, style = RoadmateType.caption, maxLines = 1)
    }
}

@Composable
private fun RewardSlot(rewards: List<PassRailReward>) {
    val shape = RoundedCornerShape(5.dp)
    if (rewards.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(46.dp).border(1.dp, GameMist.copy(alpha = .13f), shape),
            contentAlignment = Alignment.Center) { Text("·", color = GameMist.copy(alpha = .42f)) }
        return
    }
    Column(Modifier.fillMaxWidth().height(46.dp).verticalScroll(rememberScrollState())) {
        rewards.forEach { reward ->
            val lit = reward.state == RewardClaimState.AVAILABLE || reward.state == RewardClaimState.CLAIMED
            Row(Modifier.fillMaxWidth().height(46.dp).clip(shape)
                .background(if (lit) Color(0xFF24423E) else Color(0xFF1D2B3B))
                .border(1.dp, if (lit) GameGold.copy(alpha = .7f) else GameMist.copy(alpha = .26f), shape)
                .clickable(role = Role.Button, onClickLabel = "보상 보기", onClick = reward.open)
                .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(if (reward.state == RewardClaimState.CLAIMED) "✓" else reward.symbol,
                    color = if (lit) GameGold else GameMist, fontSize = 21.sp)
                Text(reward.title, color = GamePaper, style = RoadmateType.caption.copy(lineHeight = 16.sp),
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
