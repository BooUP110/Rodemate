package io.booup.roadmate

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale

@Composable
internal fun BoxScope.MoonlightBackground(file: String, camera: State<Float>, atlas: ImageBitmap?) {
    val background = artwork("journey/design/$file")
    val cover = artwork("journey/design/map-gangneung.png".takeUnless { file == "map-gangneung.png" })
    val frame = Modifier.matchParentSize().graphicsLayer {
        scaleX = 1.035f
        scaleY = scaleX
        translationX = camera.value * size.width * .009f
    }
    val scene = background ?: cover
    if (scene != null) Image(scene, null, frame, contentScale = ContentScale.Crop)
    else if (atlas != null) AtlasFrame(atlas, Expedition.GANGNEUNG.postcardIndex, frame, fill = true)
}

@Composable
internal fun BoxScope.MoonlightDepth(file: String, plane: String, camera: State<Float>) {
    val image = artwork("journey/design/${file.removeSuffix(".png")}-$plane.png") ?: return
    val foreground = plane == "front"
    Image(image, null, Modifier.matchParentSize().graphicsLayer {
        scaleX = 1.035f
        scaleY = scaleX
        translationX = camera.value * size.width * if (foreground) .024f else .015f
    }, contentScale = ContentScale.Crop)
}
