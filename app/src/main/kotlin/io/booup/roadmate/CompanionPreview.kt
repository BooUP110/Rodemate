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

internal enum class CompanionMotion(val asset: String, val holdLastPose: Boolean = false) {
    WALK_RIGHT("01-walk-right"), WALK_LEFT("02-walk-left"), TURN_LEFT("03-turn-left"),
    START_RIGHT("04-start-right"), STOP_RIGHT("05-stop-right"), WAVE("06-wave"), NOD("07-nod"),
    TALK("08-talk"), LOOK_AROUND("09-look-around"), LIFT_LANTERN("10-lift-lantern", holdLastPose = true),
    THINK("11-think"), POINT("12-point"), PICK_UP_POSTCARD("13-pick-up-postcard", holdLastPose = true),
    OPEN_LETTER("14-open-letter", holdLastPose = true), LISTEN_TO_SHELL("15-listen-to-shell"),
    GIVE_POSTCARD("16-give-postcard"), SAILOR_COAT("17-sailor-coat"), REACT("18-react"),
    READ_LETTER("19-read-letter");
}

private val roamingMotions = listOf(CompanionMotion.WALK_LEFT, CompanionMotion.WALK_RIGHT,
    CompanionMotion.TURN_LEFT, CompanionMotion.STOP_RIGHT, CompanionMotion.WAVE, CompanionMotion.NOD, CompanionMotion.START_RIGHT)

data class CompanionPreview(val profile: CompanionProfile, val portrait: String, val seasonTitle: String,
    val idleAtlas: String? = null, val original: CompanionProfile? = null)

@Composable
internal fun artwork(path: String?): ImageBitmap? {
    val assets = LocalContext.current.assets
    val image by produceState<ImageBitmap?>(null, assets, path) {
        value = null
        if (path != null) value = withContext(Dispatchers.IO) {
            val options = BitmapFactory.Options().apply {
                if (path.startsWith("companion-previews/") && path.endsWith(".png") && !path.endsWith("-idle.png")) inSampleSize = 2
            }
            runCatching { assets.open(path).use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() } }.getOrNull()
        }
    }
    return image
}

@Composable
fun CompanionPreviewPortrait(preview: CompanionPreview, modifier: Modifier = Modifier, animate: Boolean = true,
    roam: Boolean = false, onOpenRoster: (() -> Unit)? = null) =
    PreviewCompanionPortrait(preview, modifier, animate, roam, onOpenRoster = onOpenRoster)

@Composable
internal fun PreviewCompanionPortrait(preview: CompanionPreview, modifier: Modifier, animate: Boolean = true,
    roam: Boolean = false, motion: CompanionMotion? = null, onOpenRoster: (() -> Unit)? = null) = key(preview.profile.id) {
    if (preview.original != null) OriginalCompanionPortrait(preview.original, modifier, animate, roam, motion, onOpenRoster)
    else {
        val folder = "companion-previews/${preview.profile.id.value.removePrefix("preview:")}"
        val paths = companionMotionPaths(folder, animate, roam, motion)
        CompanionArtwork(preview.portrait, preview.idleAtlas, preview.profile.displayName, modifier, animate, roam,
            motion, paths[motion], paths.takeIf { roam }.orEmpty(), onOpenRoster) {
            val fallback = if (preview.profile.tone == CompanionTone.WARM) CompanionProfiles.somsom else CompanionProfiles.dodam
            OriginalCompanionPortrait(fallback, modifier, animate, roam, onOpenRoster = onOpenRoster)
        }
    }
}

internal fun originalCompanionAtlas(profile: CompanionProfile): String = "original-companions/${originalCompanionFolder(profile)}/00-idle.png"

private fun originalCompanionFolder(profile: CompanionProfile): String =
    if (profile.id == CompanionProfiles.somsom.id) "starlight-somsom" else "wave-otter"

@Composable
internal fun OriginalCompanionPortrait(profile: CompanionProfile, modifier: Modifier = Modifier, animate: Boolean = true,
    roam: Boolean = false, motion: CompanionMotion? = null, onOpenRoster: (() -> Unit)? = null) = key(profile.id) {
    val folder = originalCompanionFolder(profile)
    val path = originalCompanionAtlas(profile)
    val paths = companionMotionPaths("original-companions/$folder", animate, roam, motion)
    CompanionArtwork(path, path, profile.displayName, modifier, animate, roam,
        motion, paths[motion], paths.takeIf { roam }.orEmpty(), onOpenRoster)
}

@Composable
private fun companionMotionPaths(folder: String, animate: Boolean, roam: Boolean,
    motion: CompanionMotion?): Map<CompanionMotion, String> {
    if (!animate || (!roam && motion == null)) return emptyMap()
    val assets = LocalContext.current.assets
    val files = remember(assets, folder) { assets.list(folder)?.toSet().orEmpty() }
    return remember(folder, files, roam, motion) {
        (if (roam) roamingMotions else listOfNotNull(motion)).mapNotNull { action ->
            val fallback = when (action) {
                CompanionMotion.OPEN_LETTER -> CompanionMotion.READ_LETTER
                CompanionMotion.REACT -> CompanionMotion.LOOK_AROUND
                CompanionMotion.THINK -> CompanionMotion.NOD
                else -> null
            }
            val file = listOfNotNull(action, fallback).firstNotNullOfOrNull { candidate ->
                listOf("${candidate.asset}-16.webp", "${candidate.asset}.webp").firstOrNull { it in files }
            }
            file?.let { action to "$folder/$it" }
        }.toMap()
    }
}

private data class MotionFrames(val image: ImageBitmap, val grid: Int) {
    val count get() = grid * grid
}

@Composable
private fun motionFrames(path: String?): MotionFrames? {
    val image = artwork(path) ?: return null
    val grid = if (path?.endsWith("-16.webp") == true) 4 else 2
    if (image.width < grid || image.height < grid || image.width % grid != 0 || image.height % grid != 0) return null
    return remember(image, grid) { MotionFrames(image, grid) }
}

@Composable
internal fun CompanionArtwork(portrait: String, idleAtlas: String?, description: String, modifier: Modifier,
    animate: Boolean, roam: Boolean, motion: CompanionMotion? = null, motionPath: String? = null,
    roamPaths: Map<CompanionMotion, String> = emptyMap(),
    onOpenRoster: (() -> Unit)? = null,
    fallback: @Composable () -> Unit = {}) {
    val atlas = artwork(if (animate || portrait == idleAtlas) idleAtlas else null)
    val still = artwork(portrait.takeIf { portrait != idleAtlas && atlas == null })
    val action = key(motionPath) { motionFrames(motionPath.takeIf { animate }) }
    if (atlas != null && animate && roam) {
        RoamingCompanionPortrait(atlas, description, modifier, roamPaths, onOpenRoster)
    } else if (atlas != null || action != null) {
        val idle = atlas ?: action?.image ?: return
        val resting = action?.takeIf { motion == CompanionMotion.SAILOR_COAT }
        var frame by remember(atlas, action) { mutableIntStateOf(NEUTRAL_POSE) }
        var acting by remember(action) { mutableStateOf(action != null) }
        LaunchedEffect(atlas, animate, action, motion) {
            frame = NEUTRAL_POSE
            if (!animate) return@LaunchedEffect
            if (action != null) {
                for (pose in 0 until action.count) {
                    frame = pose
                    delay(1_560L / action.count)
                }
                if (motion?.holdLastPose == true) return@LaunchedEffect
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
        val current = if (acting) action ?: resting else resting
        AtlasFrame(current?.image ?: idle, if (current == null && atlas == null) 0 else frame,
            modifier.semantics { contentDescription = description }, grid = current?.grid ?: if (atlas == null) action?.grid ?: 2 else 2)
    } else if (still != null) Image(still, contentDescription = description,
        modifier = if (onOpenRoster == null) modifier else modifier.clickable(role = Role.Button,
            onClickLabel = "동행 선택 열기", onClick = onOpenRoster))
    else fallback()
}

@Composable
private fun RoamingCompanionPortrait(atlas: ImageBitmap, description: String, modifier: Modifier,
    motionPaths: Map<CompanionMotion, String>, onOpenRoster: (() -> Unit)?) {
    val loaded = motionPaths.map { (motion, path) -> key(motion) { motion to motionFrames(path) } }
    val motions = remember(motionPaths, loaded) {
        loaded.mapNotNull { (motion, frames) -> frames?.let { motion to it } }.toMap()
    }
    val idle = remember(atlas) { MotionFrames(atlas, 2) }
    val position = remember { Animatable(0f) }
    var bounce by remember { mutableFloatStateOf(0f) }
    var frame by remember { mutableIntStateOf(NEUTRAL_POSE) }
    var activeAtlas by remember(idle) { mutableStateOf(idle) }
    var greetings by remember { mutableIntStateOf(0) }
    LaunchedEffect(atlas, greetings, motions) {
        suspend fun play(motion: CompanionMotion, frameMillis: Long = 340) {
            val sequence = motions[motion] ?: return
            activeAtlas = sequence
            for (pose in 0 until sequence.count) {
                frame = pose
                delay(frameMillis * 4 / sequence.count)
            }
        }

        suspend fun wave() {
            if (CompanionMotion.WAVE !in motions) {
                activeAtlas = idle
                frame = WAVE_POSE
                delay(720)
            } else play(CompanionMotion.WAVE)
            activeAtlas = idle
            frame = SMILE_POSE
            delay(850)
            frame = NEUTRAL_POSE
        }

        bounce = 0f
        activeAtlas = idle
        frame = NEUTRAL_POSE
        if (greetings > 0) wave()
        delay(1_800)
        while (true) {
            for (target in listOf(.82f, -.82f, 0f)) {
                val start = position.value
                val distance = abs(target - start)
                val right = target > start
                val walk = motions[if (right) CompanionMotion.WALK_RIGHT else CompanionMotion.WALK_LEFT]
                if (distance > .03f && walk != null) {
                    val duration = (3_000 * distance).roundToInt()
                    val startWalk = motions[CompanionMotion.START_RIGHT].takeIf { right }
                    activeAtlas = startWalk ?: walk
                    frame = NEUTRAL_POSE
                    position.animateTo(target, tween(duration, easing = LinearEasing)) {
                        val progress = (value - start) / (target - start)
                        val elapsed = progress * duration
                        val frames = if (elapsed < 1_240) startWalk ?: walk else walk
                        activeAtlas = frames
                        frame = (elapsed * frames.count / 1_240).toInt() % frames.count
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
                        play(CompanionMotion.STOP_RIGHT, frameMillis = 230)
                        delay(550)
                        play(CompanionMotion.TURN_LEFT)
                    }
                    target < 0 && walk != null -> {
                        play(CompanionMotion.NOD)
                        activeAtlas = idle
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
            indication = if (motionPaths.isNotEmpty()) null else LocalIndication.current,
            role = Role.Button, onClickLabel = if (onOpenRoster == null) "인사하기" else "동행 선택 열기") {
            if (onOpenRoster == null) greetings++ else onOpenRoster()
        }, contentAlignment = Alignment.Center) {
        val sprite = minOf(maxWidth, maxHeight * .84f)
        val stride = ((maxWidth - sprite) / 2).coerceAtMost(72.dp)
        Canvas(Modifier.matchParentSize()) {
            val width = sprite.toPx() * (.48f - bounce * .02f)
            val height = sprite.toPx() * .06f
            drawOval(Color(0x263F6551), Offset(center.x + position.value * stride.toPx() - width / 2,
                center.y + sprite.toPx() * .37f), Size(width, height))
        }
        AtlasFrame(activeAtlas.image, frame, Modifier.size(sprite).graphicsLayer {
            translationX = position.value * stride.toPx()
            translationY = -bounce * 2.5.dp.toPx()
        }, grid = activeAtlas.grid)
    }
}

@Composable
internal fun AtlasFrame(image: ImageBitmap, index: Int, modifier: Modifier = Modifier, fill: Boolean = false, grid: Int = 2) {
    Canvas(modifier) {
        val cell = IntSize(image.width / grid, image.height / grid)
        val scaleX = size.width / cell.width
        val scaleY = size.height / cell.height
        val scale = if (fill) maxOf(scaleX, scaleY) else minOf(scaleX, scaleY)
        val width = (cell.width * scale).roundToInt()
        val height = (cell.height * scale).roundToInt()
        clipRect {
            drawImage(image, srcOffset = IntOffset((index % grid) * cell.width, (index / grid) * cell.height),
                srcSize = cell, dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
                dstSize = IntSize(width, height), filterQuality = FilterQuality.Medium)
        }
    }
}
