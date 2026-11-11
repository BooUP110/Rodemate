package io.booup.roadmate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class JourneyWorld(val art: String, val deep: Color, val accent: Color, val gold: Color, val paper: Color) {
    TRAVEL("travel", Color(0xFF49273F), Color(0xFF9A3E63), Color(0xFFFFC799), Color(0xFFFFE3E8)),
    STARS("stars", Color(0xFF25204C), Color(0xFF624799), Color(0xFFF8D188), Color(0xFFEBE3FF)),
    COAST("coast", Color(0xFF133E49), Color(0xFF176E70), Color(0xFFFFD396), Color(0xFFDCFFF4)),
    POST("post", Color(0xFF412336), Color(0xFF873E54), Color(0xFFF6C57B), Color(0xFFFFE7D6));
}

internal val JourneyPass.world get() = when (this) {
    JourneyPass.TRAVEL -> JourneyWorld.TRAVEL
    JourneyPass.STARS -> JourneyWorld.STARS
    JourneyPass.COAST -> JourneyWorld.COAST
}

@Composable
internal fun WorldBackdrop(world: JourneyWorld, modifier: Modifier = Modifier) {
    val image = artwork("pass-worlds/${world.art}.jpg")
    Box(modifier.background(Brush.verticalGradient(listOf(world.accent, world.deep)))) {
        image?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop) }
    }
}

@Composable
internal fun WorldEyebrow(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text, modifier, color = color,
        style = RoadmateType.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = .25.sp))
}

@Composable
internal fun WorldTitle(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(text, modifier, color = color, style = RoadmateType.heading)
}

@Composable
internal fun WorldAction(label: String, world: JourneyWorld, onClick: () -> Unit,
    modifier: Modifier = Modifier, primary: Boolean = true, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (primary) world.accent else world.paper,
            contentColor = if (primary) Color.White else world.deep),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)) {
        Text(label, style = RoadmateType.control.copy(fontSize = 16.sp, lineHeight = 22.sp))
    }
}

@Composable
internal fun WorldPaper(world: JourneyWorld, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 15.dp, bottomEnd = 15.dp)
    Column(modifier.clip(shape).background(Brush.verticalGradient(listOf(world.paper.copy(alpha = .95f), Color(0xFFFFF8E9))))
        .border(1.5.dp, world.accent.copy(alpha = .48f), shape).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
internal fun WorldRail(world: JourneyWorld, reached: Int, progress: Float, rewardAction: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().height(76.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val start = size.width / 6
            val end = size.width * 5 / 6
            val y = 22.dp.toPx()
            drawLine(world.accent.copy(alpha = .25f), Offset(start, y), Offset(end, y), 4.dp.toPx(), StrokeCap.Round)
            drawLine(world.accent, Offset(start, y), Offset(start + (end - start) * progress, y), 4.dp.toPx(), StrokeCap.Round)
            for (index in 1..7) {
                if (index != 4) drawCircle(world.accent.copy(alpha = .44f), 2.dp.toPx(), Offset(start + (end - start) * index / 8, y + 8.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceAround) {
            (1..3).forEach { tier ->
                val level = rewardLevel(tier)
                val reachedLevel = reached >= level
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = "레벨 $level 보상 보기") { rewardAction(tier) },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(44.dp).clip(CircleShape)
                        .background(if (reachedLevel) world.accent else world.paper)
                        .border(2.dp, if (reachedLevel) world.gold else world.accent.copy(alpha = .46f), CircleShape),
                        contentAlignment = Alignment.Center) {
                        Text(if (tier == 3) "✦" else if (reachedLevel) "✓" else "·",
                            color = if (reachedLevel) Color.White else world.accent, fontSize = 22.sp)
                    }
                    Text("Lv.$level", color = world.deep, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(if (tier == 3) "동반자 팩" else "보상 보기", color = world.deep, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
internal fun IllustratedEnvelope(world: JourneyWorld, sealed: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val width = size.width
        val height = size.height
        val outline = Path().apply {
            moveTo(width * .04f, height * .17f)
            lineTo(width * .96f, height * .17f)
            lineTo(width * .96f, height * .94f)
            lineTo(width * .04f, height * .94f)
            close()
        }
        val flap = Path().apply {
            moveTo(width * .04f, height * .17f)
            lineTo(width * .5f, if (sealed) height * .62f else 0f)
            lineTo(width * .96f, height * .17f)
            close()
        }
        drawPath(outline, Brush.verticalGradient(listOf(Color(0xFFFFF4D8), world.gold)))
        drawPath(outline, world.deep, style = Stroke(2.dp.toPx()))
        drawLine(world.accent.copy(alpha = .56f), Offset(width * .04f, height * .94f), Offset(width * .48f, height * .52f), 1.dp.toPx())
        drawLine(world.accent.copy(alpha = .56f), Offset(width * .96f, height * .94f), Offset(width * .52f, height * .52f), 1.dp.toPx())
        drawPath(flap, world.paper)
        drawPath(flap, world.accent, style = Stroke(1.5.dp.toPx()))
        if (sealed) {
            val seal = Offset(width * .5f, height * .61f)
            val radius = height * .112f
            drawCircle(world.accent, radius, seal)
            drawCircle(world.gold, radius * .74f, seal, style = Stroke(1.dp.toPx()))
            val mark = Path().apply {
                moveTo(seal.x, seal.y - radius * .46f)
                lineTo(seal.x + radius * .3f, seal.y)
                lineTo(seal.x, seal.y + radius * .46f)
                lineTo(seal.x - radius * .3f, seal.y)
                close()
            }
            drawPath(mark, world.gold)
        }
    }
}
