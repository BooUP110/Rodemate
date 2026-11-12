package io.booup.roadmate

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.booup.roadmate.model.CompanionProfile
import io.booup.roadmate.model.CompanionProfiles
import io.booup.roadmate.model.CompanionTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private const val NEUTRAL_POSE = 0
private const val WAVE_POSE = 1
private const val SMILE_POSE = 2
private const val BLINK_POSE = 3

internal enum class OtterMotion(val asset: String) {
    WALK_LEFT("walk-left"), WALK_RIGHT("walk-right"), TURN_LEFT("turn-left"), STOP_RIGHT("stop-right"),
    WAVE("wave"), NOD("nod"), TALK("talk"), THINK("think"), LOOK_AROUND("look-around"), GIVE_POSTCARD("give-postcard");

    val path get() = "original-companions/wave-otter/$asset.webp"
}

private val otterRoamingMotions = listOf(OtterMotion.WALK_LEFT, OtterMotion.WALK_RIGHT,
    OtterMotion.TURN_LEFT, OtterMotion.STOP_RIGHT, OtterMotion.WAVE, OtterMotion.NOD)

data class CompanionPreview(val profile: CompanionProfile, val portrait: String, val seasonTitle: String,
    val idleAtlas: String? = null)

@Composable
internal fun artwork(path: String?): ImageBitmap? {
    val assets = LocalContext.current.assets
    val image by produceState<ImageBitmap?>(null, assets, path) {
        value = null
        if (path != null) value = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply {
                if (path.startsWith("companion-previews/") && !path.endsWith("-idle.png")) inSampleSize = 2
            }
            runCatching { assets.open(path).use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() } }.getOrNull()
        }
    }
    return image
}

@Composable
fun CompanionPreviewPortrait(preview: CompanionPreview, modifier: Modifier = Modifier, animate: Boolean = true,
    roam: Boolean = false) = key(preview.profile.id) {
    CompanionArtwork(preview.portrait, preview.idleAtlas, "${preview.profile.displayName} · 콜라보 초상화", modifier, animate, roam) {
        val original = if (preview.profile.tone == CompanionTone.WARM) CompanionProfiles.somsom else CompanionProfiles.dodam
        OriginalCompanionPortrait(original, modifier, animate, roam)
    }
}

@Composable
internal fun OriginalCompanionPortrait(profile: CompanionProfile, modifier: Modifier = Modifier, animate: Boolean = true,
    roam: Boolean = false, otterMotion: OtterMotion? = null) = key(profile.id) {
    val atlas = if (profile.id == CompanionProfiles.somsom.id) "starlight-somsom-idle.png" else "wave-otter-idle.png"
    val path = "original-companions/$atlas"
    val isOtter = profile.id == CompanionProfiles.dodam.id
    CompanionArtwork(path, path, profile.displayName, modifier, animate, roam,
        otterRoam = isOtter, otterMotion = otterMotion.takeIf { isOtter })
}

@Composable
private fun CompanionArtwork(portrait: String, idleAtlas: String?, description: String, modifier: Modifier,
    animate: Boolean, roam: Boolean, otterRoam: Boolean = false, otterMotion: OtterMotion? = null,
    fallback: @Composable () -> Unit = {}) {
    val atlas = artwork(if (animate || portrait == idleAtlas) idleAtlas else null)
    val still = artwork(portrait.takeIf { portrait != idleAtlas && atlas == null })
    if (atlas != null && animate && roam) {
        RoamingCompanionPortrait(atlas, description, modifier, otterRoam)
    } else if (atlas != null) {
        val action = key(otterMotion) { artwork(otterMotion?.path.takeIf { animate }) }
        var frame by remember(atlas, action) { mutableIntStateOf(NEUTRAL_POSE) }
        var acting by remember(action) { mutableStateOf(action != null) }
        LaunchedEffect(atlas, animate, action) {
            frame = NEUTRAL_POSE
            if (!animate) return@LaunchedEffect
            if (action != null) {
                for (pose in 0..3) {
                    frame = pose
                    delay(390)
                }
                acting = false
                frame = NEUTRAL_POSE
            }
            while (true) {
                delay(4_800)
                frame = BLINK_POSE
                delay(240)
                frame = NEUTRAL_POSE
            }
        }
        AtlasFrame(if (acting) action ?: atlas else atlas, frame, modifier.semantics { contentDescription = description })
    } else if (still != null) Image(still, contentDescription = description, modifier = modifier)
    else fallback()
}

@Composable
private fun RoamingCompanionPortrait(atlas: ImageBitmap, description: String, modifier: Modifier, otter: Boolean) {
    val loaded = if (otter) otterRoamingMotions.map { motion ->
        key(motion) { artwork(motion.path) }
    } else emptyList()
    val motions = remember(loaded) {
        if (loaded.size == otterRoamingMotions.size && loaded.all { it != null }) {
            otterRoamingMotions.zip(loaded.filterNotNull()).toMap()
        } else emptyMap()
    }
    val position = remember { Animatable(0f) }
    var bounce by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableIntStateOf(NEUTRAL_POSE) }
    var activeAtlas by remember(atlas) { mutableStateOf(atlas) }
    var greetings by remember { mutableIntStateOf(0) }
    LaunchedEffect(atlas, greetings, motions) {
        suspend fun play(motion: OtterMotion, firstPose: Int = 0, frameMillis: Long = 340) {
            val sequence = motions[motion] ?: return
            activeAtlas = sequence
            for (pose in firstPose..3) {
                frame = pose
                delay(frameMillis)
            }
        }

        suspend fun wave() {
            if (motions.isEmpty()) {
                frame = WAVE_POSE
                delay(720)
            } else play(OtterMotion.WAVE)
            activeAtlas = atlas
            frame = SMILE_POSE
            delay(850)
            frame = NEUTRAL_POSE
        }

        bounce = 0f
        activeAtlas = atlas
        frame = NEUTRAL_POSE
        if (greetings > 0) wave()
        delay(1_800)
        while (true) {
            for (target in listOf(.82f, -.82f, 0f)) {
                val start = position.value
                val distance = abs(target - start)
                val walk = motions[if (target > start) OtterMotion.WALK_RIGHT else OtterMotion.WALK_LEFT]
                if (distance > .03f && walk != null) {
                    val duration = (3_000 * distance).roundToInt()
                    activeAtlas = walk
                    frame = NEUTRAL_POSE
                    position.animateTo(target, tween(duration, easing = LinearEasing)) {
                        val progress = (value - start) / (target - start)
                        frame = (progress * duration / 310).toInt() % 4
                    }
                } else if (distance > .03f) {
                    val steps = if (distance > 1f) 5 else 3
                    position.animateTo(target, tween((3_000 * distance).roundToInt(), easing = LinearEasing)) {
                        val phase = (value - start) / (target - start) * steps * PI
                        bounce = abs(sin(phase)).toFloat()
                    }
                }
                bounce = 0f
                when {
                    target > 0 && walk != null -> {
                        play(OtterMotion.STOP_RIGHT, firstPose = 2)
                        delay(550)
                        play(OtterMotion.TURN_LEFT)
                    }
                    target < 0 && walk != null -> {
                        play(OtterMotion.NOD)
                        activeAtlas = atlas
                        frame = NEUTRAL_POSE
                        delay(700)
                    }
                    else -> {
                        if (target == 0f) wave()
                        delay(1_650)
                    }
                }
            }
        }
    }
    BoxWithConstraints(modifier
        .semantics { contentDescription = description }
        .clickable(interactionSource = remember { MutableInteractionSource() },
            indication = if (otter) null else LocalIndication.current,
            role = Role.Button, onClickLabel = "인사하기") { greetings++ }, contentAlignment = Alignment.Center) {
        val sprite = minOf(maxWidth, maxHeight * .84f)
        val stride = ((maxWidth - sprite) / 2).coerceAtMost(72.dp)
        Canvas(Modifier.matchParentSize()) {
            val width = sprite.toPx() * (.48f - bounce * .02f)
            val height = sprite.toPx() * .06f
            drawOval(Color(0x263F6551), Offset(center.x + position.value * stride.toPx() - width / 2,
                center.y + sprite.toPx() * .37f), Size(width, height))
        }
        AtlasFrame(activeAtlas, frame, Modifier.size(sprite).graphicsLayer {
            translationX = position.value * stride.toPx()
            translationY = -bounce * 2.5.dp.toPx()
        })
    }
}

@Composable
internal fun AtlasFrame(image: ImageBitmap, index: Int, modifier: Modifier = Modifier, fill: Boolean = false) {
    Canvas(modifier) {
        val cell = IntSize(image.width / 2, image.height / 2)
        val scaleX = size.width / cell.width
        val scaleY = size.height / cell.height
        val scale = if (fill) maxOf(scaleX, scaleY) else minOf(scaleX, scaleY)
        val width = (cell.width * scale).roundToInt()
        val height = (cell.height * scale).roundToInt()
        clipRect {
            drawImage(image, srcOffset = IntOffset((index % 2) * cell.width, (index / 2) * cell.height),
                srcSize = cell, dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
                dstSize = IntSize(width, height), filterQuality = FilterQuality.Medium)
        }
    }
}
