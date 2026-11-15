package io.booup.roadmate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.booup.roadmate.model.CompanionId
import io.booup.roadmate.model.CompanionProfile
import io.booup.roadmate.model.CompanionTone

internal data class UpcomingCharacter(val asset: String, val name: String)
internal data class UpcomingReward(val title: String, val art: PassRewardArt)

/**
 * Collab passes under discussion: locked teasers with a look-only preview. Nothing here can be claimed or equipped.
 * Art comes from the git-ignored internal collab assets: `pass-worlds/<key>.jpg` (wide), `<key>-poster.jpg` (tall)
 * and `companion-previews/<asset>-idle.png` (2×2 idle sheet). Missing art falls back to gradients and silhouettes.
 */
internal enum class UpcomingCollab(val title: String, val tagline: String, val characters: List<UpcomingCharacter>,
    val rewards: List<UpcomingReward>, val deep: Color, val glow: Color) {
    SHINCHAN("서울 투어 짱구 패스", "짱구와 친구들과 서울 곳곳을 여행하고 한정 보상을 모아요.",
        listOf(UpcomingCharacter("shinchan", "짱구"), UpcomingCharacter("cheolsu", "철수"), UpcomingCharacter("yuri", "유리"),
            UpcomingCharacter("maenggu", "맹구"), UpcomingCharacter("hooni", "훈이")),
        listOf(UpcomingReward("남산 여행 소인", PassRewardArt.STAMP), UpcomingReward("한강 노을 액자", PassRewardArt.ACCESSORY),
            UpcomingReward("움직이는 서울 엽서", PassRewardArt.POSTCARD), UpcomingReward("짱구 동반자 팩", PassRewardArt.PACK)),
        Color(0xFF1F3A6E), Color(0xFFFFC94D)),
    // Deliberately nameless: hints that more collabs follow without naming a partner we have no art or rights for.
    MYSTERY("다음 콜라보 패스", "새로운 친구와 떠날 다음 시즌을 준비하고 있어요.",
        listOf(UpcomingCharacter("mystery-1", "???"), UpcomingCharacter("mystery-2", "???")),
        listOf(UpcomingReward("??? 여행 소인", PassRewardArt.STAMP), UpcomingReward("??? 액자", PassRewardArt.ACCESSORY),
            UpcomingReward("움직이는 ??? 엽서", PassRewardArt.POSTCARD), UpcomingReward("??? 동반자 팩", PassRewardArt.PACK)),
        Color(0xFF1D2B45), Color(0xFFFFDD55));

    val key get() = name.lowercase()
}

internal val upcomingCompanions: List<Pair<CompanionProfile, UpcomingCollab>> = UpcomingCollab.entries.flatMap { collab ->
    collab.characters.map { CompanionProfile(CompanionId("upcoming:${it.asset}"), it.name, it.name.take(2), CompanionTone.WARM) to collab }
}

internal fun upcomingCollabOf(id: CompanionId): UpcomingCollab? = upcomingCompanions.firstOrNull { it.first.id == id }?.second

/** The character's own idle sheet when the internal art is installed; a silhouette while loading or without art. */
@Composable
internal fun UpcomingPortrait(profile: CompanionProfile, collab: UpcomingCollab, modifier: Modifier, animate: Boolean = false) {
    val atlas = "companion-previews/${profile.id.value.removePrefix("upcoming:")}-idle.png"
    Box(modifier) {
        CompanionArtwork(atlas, atlas, profile.displayName, Modifier.matchParentSize(), animate, roam = false) {
            LockedSilhouette(collab.glow, Modifier.matchParentSize())
        }
    }
}

@Composable
internal fun LockedSilhouette(glow: Color, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        Canvas(Modifier.matchParentSize()) {
            val s = size.minDimension
            val left = (size.width - s) / 2
            val top = (size.height - s) / 2
            val ink = Color(0xFF0B141D)
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = .38f), Color.Transparent),
                center = center, radius = s * .5f), radius = s * .5f, center = center)
            drawOval(ink, Offset(left + s * .25f, top + s * .52f), Size(s * .50f, s * .40f))
            drawCircle(ink, s * .21f, Offset(left + s * .5f, top + s * .38f))
            drawCircle(ink, s * .075f, Offset(left + s * .34f, top + s * .20f))
            drawCircle(ink, s * .075f, Offset(left + s * .66f, top + s * .20f))
        }
        Text("?", Modifier.offset(y = side * -.12f), color = glow, style = RoadmateType.cardTitle)
    }
}

@Composable
private fun LockMark(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(12.dp, 15.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(w * .16f)
        drawArc(color, 180f, 180f, false, Offset(w * .2f, 0f), Size(w * .6f, h * .6f), style = stroke)
        drawLine(color, Offset(w * .2f, h * .3f), Offset(w * .2f, h * .48f), stroke.width)
        drawLine(color, Offset(w * .8f, h * .3f), Offset(w * .8f, h * .48f), stroke.width)
        drawRoundRect(color, Offset(0f, h * .44f), Size(w, h * .56f), CornerRadius(w * .18f))
    }
}

@Composable
private fun LockBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.background(GameInk.copy(alpha = .80f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LockMark(color)
        Text(text, color = color, style = RoadmateType.caption)
    }
}

@Composable
internal fun LockedCollabPoster(collab: UpcomingCollab, modifier: Modifier, open: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val art = artwork("pass-worlds/${collab.key}.jpg")
    Box(modifier.clip(shape).background(Brush.verticalGradient(listOf(collab.deep, GameInk)))
        .border(1.dp, collab.glow.copy(alpha = .55f), shape)
        .clickable(role = Role.Button, onClickLabel = "${collab.title} 미리보기") { RoadmateSounds.play(RoadmateSound.LOCKED); open() }
        .semantics { stateDescription = "잠김 · 콜라보 예시, 실제 제휴 아님" }) {
        if (art != null) Image(art, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else Row(Modifier.align(Alignment.TopEnd).padding(10.dp)) {
            collab.characters.take(3).forEach { LockedSilhouette(collab.glow, Modifier.size(76.dp)) }
        }
        // Bright partner art needs a dark reading side for the title.
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to collab.deep.copy(alpha = .94f),
            .38f to collab.deep.copy(alpha = .70f), .62f to Color.Transparent)))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(.45f to Color.Transparent, 1f to collab.deep.copy(alpha = .85f))))
        LockBadge("잠김", collab.glow, Modifier.align(Alignment.TopStart).padding(12.dp))
        Column(Modifier.align(Alignment.BottomStart).padding(15.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            WorldEyebrow("콜라보 예시  ·  실제 제휴 아님", collab.glow)
            Text(collab.title, color = Color.White, style = RoadmateType.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("미리보기  ›", color = collab.glow, style = RoadmateType.control)
        }
    }
}

/** Look-only preview of a locked collab pass: key art, the moving cast and the rewards it would hold. */
@Composable
internal fun UpcomingPassPreview(collab: UpcomingCollab, close: () -> Unit) {
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(collab.deep)) {
            val wide = maxWidth >= 730.dp
            val wideArt = artwork("pass-worlds/${collab.key}.jpg")
            val tallArt = if (wide) null else artwork("pass-worlds/${collab.key}-poster.jpg")
            wideArt?.let { Image(it, null, Modifier.matchParentSize().alpha(.30f), contentScale = ContentScale.Crop) }
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(collab.deep.copy(alpha = .55f), GameInk.copy(alpha = .92f)))))
            val keyArt: @Composable (Modifier) -> Unit = { modifier ->
                Box(modifier, contentAlignment = Alignment.Center) {
                    val art = tallArt ?: wideArt
                    if (art != null) KeyArt(art, collab) else LockedSilhouette(collab.glow, Modifier.size(180.dp))
                }
            }
            val details: @Composable (Modifier) -> Unit = { modifier -> PreviewDetails(collab, wide, close, modifier) }
            if (wide) Row(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                keyArt(Modifier.weight(1.2f).fillMaxHeight())
                details(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
            } else Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                details(Modifier.fillMaxWidth())
                keyArt(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun KeyArt(art: ImageBitmap, collab: UpcomingCollab) {
    val shape = RoundedCornerShape(22.dp)
    Box(Modifier.aspectRatio(art.width.toFloat() / art.height).clip(shape).border(1.dp, collab.glow.copy(alpha = .6f), shape)) {
        Image(art, collab.title, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        LockBadge("콜라보 예시 · 실제 제휴 아님", collab.glow, Modifier.align(Alignment.TopEnd).padding(12.dp))
    }
}

@Composable
private fun PreviewDetails(collab: UpcomingCollab, wide: Boolean, close: () -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            GameButton("‹ 돌아가기", close)
            Spacer(Modifier.weight(1f))
            LockBadge("미리보기 · 잠김", collab.glow)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WorldEyebrow("NEXT COLLAB  ·  시즌 패스 미리보기", collab.glow)
            Text(collab.title, color = Color.White, style = RoadmateType.heading)
            Text(collab.tagline, color = GamePaper, style = RoadmateType.control)
        }
        Text("함께할 동행", color = GamePaper, style = RoadmateType.metric)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            upcomingCompanions.filter { it.second == collab }.forEach { (profile, _) ->
                Column(Modifier.background(GameInk.copy(alpha = .55f), RoundedCornerShape(14.dp)).padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    UpcomingPortrait(profile, collab, Modifier.size(if (wide) 82.dp else 72.dp), animate = true)
                    Text(profile.displayName, color = GamePaper, style = RoadmateType.metric)
                }
            }
        }
        Text("패스 보상", color = GamePaper, style = RoadmateType.metric)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            collab.rewards.forEach { reward ->
                Column(Modifier.width(124.dp).clip(RoundedCornerShape(12.dp)).background(GameInk.copy(alpha = .72f))
                    .border(1.dp, collab.glow.copy(alpha = .30f), RoundedCornerShape(12.dp)).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    RewardItemIllustration(reward.art, Modifier.size(52.dp).alpha(.65f))
                    Text(reward.title, color = GamePaper, style = RoadmateType.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        LockMark(GameMist)
                        Text("잠김", color = GameMist, style = RoadmateType.caption)
                    }
                }
            }
        }
        GameButton("콜라보가 확정되면 열려요", {}, Modifier.fillMaxWidth(), enabled = false)
        Text("콜라보 구성의 미리보기이며 실제 제휴가 아닙니다. 정식 제공에는 라이선스 계약이 필요합니다.",
            color = GameMist, style = RoadmateType.caption)
    }
}
