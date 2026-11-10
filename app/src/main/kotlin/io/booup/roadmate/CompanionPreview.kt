package io.booup.roadmate

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.booup.roadmate.model.CompanionProfile
import io.booup.roadmate.model.CompanionTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

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
fun CompanionPreviewPortrait(preview: CompanionPreview, modifier: Modifier = Modifier, animate: Boolean = true) = key(preview.profile.id) {
    val atlas = artwork(if (animate) preview.idleAtlas else null)
    val still = artwork(if (atlas == null) preview.portrait else null)
    val description = "${preview.profile.displayName} · 콜라보 초상화"
    if (atlas != null) {
        var frame by remember(atlas) { mutableIntStateOf(0) }
        LaunchedEffect(atlas) {
            while (true) {
                delay(1_350)
                for (next in listOf(1, 2, 3, 0)) { frame = next; delay(if (next == 2) 180 else 145) }
            }
        }
        AtlasFrame(atlas, frame, modifier.semantics { contentDescription = description })
    } else if (still != null) Image(still, contentDescription = description, modifier = modifier)
    else CompanionPortrait(preview.profile.displayName, preview.profile.tone == CompanionTone.WARM, modifier)
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
