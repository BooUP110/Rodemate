package io.booup.roadmate

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.booup.roadmate.model.RewardClaimState
import io.booup.roadmate.model.SeasonView

internal data class PassRailReward(
    val xp: Long, val premium: Boolean, val title: String, val art: PassRewardArt, val state: RewardClaimState,
    val opensWithUpgrade: Boolean, val open: () -> Unit,
)

internal enum class PassRewardArt(val cell: Int, val symbol: String) {
    POSTCARD(0, "✉"), STAMP(1, "◆"), ACCESSORY(2, "✦"), PACK(3, "◇"),
}

private const val REWARD_ITEMS_ATLAS = "journey/reward-items.png"

@Composable
internal fun RewardItemIllustration(art: PassRewardArt, modifier: Modifier) {
    artwork(REWARD_ITEMS_ATLAS)?.let { AtlasFrame(it, art.cell, modifier) }
}

@Composable
internal fun GamePassRail(season: SeasonView, rewards: List<PassRailReward>, modifier: Modifier = Modifier,
    world: JourneyWorld? = null, upgraded: Boolean = season.progress.premiumEntitled) {
    val xp = season.progress.totalXp
    val cap = maxOf(season.season.levels.lastOrNull() ?: 0L, rewards.lastOrNull()?.xp ?: 0L, 1L)
    val base = world?.let { lerp(GameInk, it.deep, .70f) } ?: GameInk
    val gold = world?.gold ?: GameGold
    val panel = if (upgraded) lerp(base, gold, .11f) else base
    val accent = world?.accent ?: GameMist
    val shape = RoundedCornerShape(18.dp)
    val itemAtlas = artwork(REWARD_ITEMS_ATLAS)
    Column(modifier.fillMaxWidth().clip(shape).background(panel.copy(alpha = .96f))
        .border(1.dp, gold.copy(alpha = if (upgraded) .68f else .34f), shape)
        .padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("패스 트랙", color = GamePaper, style = RoadmateType.control)
            LinearProgressIndicator(progress = { (xp.toFloat() / cap).coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(50)), color = gold,
                trackColor = GamePaper.copy(alpha = .22f))
            Text("$xp / $cap XP", color = gold, style = RoadmateType.metric, maxLines = 1)
        }
        if (rewards.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = (maxWidth / 6.2f).coerceAtLeast(150.dp)
            key(season.season.seasonId, world) {
                val start = (rewards.indexOfLast { it.xp <= xp } - 2).coerceAtLeast(0)
                LazyRow(state = rememberLazyListState(initialFirstVisibleItemIndex = start),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(rewards) { reward ->
                        PassRewardCard(reward, itemAtlas, panel, gold, accent, Modifier.width(cardWidth))
                    }
                }
            }
        }
    }
}

@Composable
private fun PassRewardCard(reward: PassRailReward, itemAtlas: ImageBitmap?, panel: Color, gold: Color, accent: Color,
    modifier: Modifier) {
    val available = reward.state == RewardClaimState.AVAILABLE
    val claimed = reward.state == RewardClaimState.CLAIMED
    val locked = reward.state == RewardClaimState.LOCKED
    val shape = RoundedCornerShape(11.dp)
    val status = when (reward.state) {
        RewardClaimState.AVAILABLE -> "수령 가능"
        RewardClaimState.CLAIMED -> "수령 완료"
        RewardClaimState.LOCKED -> if (reward.opensWithUpgrade) "업그레이드 시 수령" else "잠김"
        RewardClaimState.UNCLAIMED_EXPIRED -> "시즌 종료"
    }
    Column(modifier.height(112.dp).clip(shape)
        .background(when {
            available -> lerp(panel, accent, .47f)
            claimed -> lerp(panel, accent, .22f)
            else -> lerp(panel, Color.Black, .31f)
        })
        .border(1.dp, when {
            available -> gold
            reward.opensWithUpgrade || !reward.premium -> gold.copy(alpha = .47f)
            else -> GameMist.copy(alpha = .24f)
        }, shape)
        .clickable(role = Role.Button, onClickLabel = "보상 보기", onClick = reward.open)
        .semantics { stateDescription = if (reward.opensWithUpgrade) "잠김 · $status 가능" else status }
        .padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("${reward.xp} XP", color = gold, style = RoadmateType.metric, maxLines = 1)
            val label = if (reward.premium) "유료" else "무료"
            Text(label, Modifier.background(
                if (reward.premium) GamePaper.copy(alpha = .12f) else gold, RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
                color = if (reward.premium) GamePaper else GameInk, style = RoadmateType.caption, maxLines = 1)
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(reward.title, Modifier.weight(1f), color = GamePaper.copy(alpha = if (locked) .81f else 1f),
                style = RoadmateType.metric.copy(fontSize = 14.sp, lineHeight = 17.sp), maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            Box(Modifier.size(54.dp).background(gold.copy(alpha = if (locked) .055f else .13f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center) {
                if (itemAtlas != null) AtlasFrame(itemAtlas, reward.art.cell, Modifier.matchParentSize()
                    .padding(3.dp).alpha(if (locked) .56f else 1f))
                else Text(reward.art.symbol, color = GameMist, style = RoadmateType.metric)
            }
        }
        Text(if (reward.opensWithUpgrade) "잠김 · $status" else status,
            color = if (available || reward.opensWithUpgrade) gold else GameMist,
            style = RoadmateType.caption.copy(fontSize = 12.sp, lineHeight = 15.sp), maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun PassUpgradeLighting(upgraded: Boolean, gold: Color, modifier: Modifier) {
    val light by animateFloatAsState(if (upgraded) 1f else 0f, tween(520), label = "pass backdrop light")
    if (light > 0f) Box(modifier.background(Brush.verticalGradient(
        0f to gold.copy(alpha = .29f * light),
        .33f to Color.Transparent,
        .70f to gold.copy(alpha = .14f * light),
        1f to gold.copy(alpha = .22f * light),
    )))
}
