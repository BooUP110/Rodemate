package io.booup.roadmate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF283C31)
internal val RoadmateForest = Color(0xFF263D33)
internal val RoadmateGold = Color(0xFFF8D78A)
internal object RoadmateType {
    val heading = TextStyle(
        fontFamily = FontFamily(Font(R.font.carpet_heading)),
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    )
    val cardTitle = heading.copy(fontSize = 24.sp, lineHeight = 30.sp)
    val body = TextStyle(fontFamily = FontFamily(Font(R.font.gowun_dodum_regular)))
    val control = body.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold)
    val caption = body.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val metric = control.copy(fontSize = 15.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum")
}

private val Palette = lightColorScheme(
    primary = Color(0xFF4C673F), onPrimary = Color.White,
    primaryContainer = Color(0xFFE7EED5), onPrimaryContainer = Ink,
    secondary = Color(0xFF845039), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF9E8D7), onSecondaryContainer = Color(0xFF553524),
    tertiary = Color(0xFF6E526D), onTertiary = Color.White, tertiaryContainer = Color(0xFFF0E5F1),
    background = Color(0xFFF2F0E3), onBackground = Ink,
    surface = Color(0xFFFFFAEF), onSurface = Ink,
    surfaceVariant = Color(0xFFEFE8E5), onSurfaceVariant = Color(0xFF495563),
    outline = Color(0xFF82746E), outlineVariant = Color(0xFFE6D7C9), error = Color(0xFF9F2626), onError = Color.White,
    errorContainer = Color(0xFFFFE2DF), onErrorContainer = Color(0xFF631A19),
)

@Composable
fun RoadmateTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Palette, typography = Typography(
        headlineLarge = RoadmateType.heading.copy(fontSize = 42.sp, lineHeight = 52.sp),
        headlineMedium = RoadmateType.heading.copy(fontSize = 36.sp, lineHeight = 46.sp),
        headlineSmall = RoadmateType.cardTitle,
        titleLarge = RoadmateType.heading.copy(lineHeight = 42.sp),
        titleMedium = RoadmateType.cardTitle.copy(fontSize = 26.sp, lineHeight = 36.sp),
        titleSmall = RoadmateType.control,
        bodyLarge = RoadmateType.body.copy(fontSize = 26.sp, lineHeight = 36.sp),
        bodyMedium = RoadmateType.body.copy(fontSize = 22.sp, lineHeight = 32.sp),
        bodySmall = RoadmateType.body.copy(fontSize = 20.sp, lineHeight = 28.sp),
        labelLarge = RoadmateType.body.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
        labelMedium = RoadmateType.body.copy(fontSize = 20.sp, lineHeight = 28.sp),
        labelSmall = RoadmateType.body.copy(fontSize = 20.sp, lineHeight = 28.sp),
    ), shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)), content = content)
}

@Composable
fun RoadmateCard(modifier: Modifier = Modifier, tinted: Boolean = false, compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = if (tinted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(if (compact) 16.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp), content = content)
    }
}

@Composable
internal fun RoadmateLandscape(file: String, fallbackIndex: Int, modifier: Modifier, atlas: ImageBitmap?) {
    val image = artwork("journey/design/$file")
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF384D67), Color(0xFFAD8395))))) {
        if (image != null) Image(image, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else if (atlas != null) AtlasFrame(atlas, fallbackIndex, Modifier.matchParentSize(), fill = true)
    }
}

@Composable
internal fun RoadmateStoryHeader(title: String, body: String, file: String, fallbackIndex: Int) {
    val atlas = artwork("journey/postcards.png")
    Box(Modifier.fillMaxWidth().heightIn(min = 166.dp).clip(RoundedCornerShape(26.dp))) {
        RoadmateLandscape(file, fallbackIndex, Modifier.matchParentSize(), atlas)
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xEF243443), Color(0xA9233443), Color(0x49374452)))))
        Column(Modifier.align(Alignment.CenterStart).padding(22.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text(body, color = Color(0xFFFFEED9), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun RoadmateBadge(text: String, demo: Boolean = false) {
    Text(text, Modifier.background(
        if (demo) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 8.dp),
        color = if (demo) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.labelMedium)
}

@Composable
internal fun RoadmateChapter(title: String, file: String, fallback: Int, status: String, insets: PaddingValues,
    navigation: @Composable () -> Unit, priorityCare: @Composable () -> Unit, content: @Composable () -> Unit) {
    val atlas = artwork("journey/postcards.png")
    BoxWithConstraints(Modifier.fillMaxSize().background(GameInk)) {
        val margin = if (maxWidth >= 680.dp) 22.dp else 16.dp
        RoadmateLandscape(file, fallback, Modifier.matchParentSize(), atlas)
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to GameInk.copy(alpha = .84f), .24f to GameInk.copy(alpha = .38f),
            .62f to GameInk.copy(alpha = .28f), 1f to GameInk.copy(alpha = .93f))))
        Column(Modifier.fillMaxSize().padding(insets).imePadding().padding(horizontal = margin, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, Modifier.weight(1f), color = GamePaper, style = RoadmateType.heading,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                GamePill(status, Modifier.widthIn(max = 154.dp))
            }
            priorityCare()
            val scroll = rememberScrollState()
            val fade = if (scroll.canScrollForward) Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val top = (size.height - 12.dp.toPx()).coerceAtLeast(0f)
                    if (top < size.height) drawRect(
                        Brush.verticalGradient(listOf(Color.Black, Color.Transparent), startY = top, endY = size.height),
                        topLeft = Offset(0f, top), size = Size(size.width, size.height - top), blendMode = BlendMode.DstIn,
                    )
                } else Modifier
            Box(Modifier.weight(1f).fillMaxWidth().then(fade)) {
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
                        primary = GameInk, onPrimary = GamePaper, primaryContainer = GameGold, onPrimaryContainer = GameInk,
                        surface = GamePaper, onSurface = GameInk, surfaceVariant = Color(0xFFE9DFC9), onSurfaceVariant = GameInk,
                        secondaryContainer = Color(0xFFF4E5C4), onSecondaryContainer = GameInk,
                        outline = Color(0xFF947746), outlineVariant = Color(0xFFC7A977),
                    )) {
                        CompositionLocalProvider(LocalContentColor provides GamePaper, content = content)
                    }
                }
            }
            navigation()
        }
    }
}

@Composable
internal fun RoadmateJournalColumns(firstWeight: Float = .48f,
    first: @Composable ColumnScope.() -> Unit, second: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 860.dp) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(firstWeight), verticalArrangement = Arrangement.spacedBy(12.dp), content = first)
                Column(Modifier.weight(1f - firstWeight), verticalArrangement = Arrangement.spacedBy(12.dp), content = second)
            }
        } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            first()
            second()
        }
    }
}

@Composable
internal fun RoadmateMasthead(status: String, decorated: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (decorated) Surface(shape = RoundedCornerShape(13.dp), color = RoadmateForest) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Text("✦", Modifier.clearAndSetSemantics {}, color = RoadmateGold, style = MaterialTheme.typography.titleMedium)
            }
        }
        Text(stringResource(R.string.app_name), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface) {
            Text(status, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun RoadmateDock(content: @Composable RowScope.() -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(13.dp), color = GameInk.copy(alpha = .92f),
            border = BorderStroke(1.dp, GameGold.copy(alpha = .42f)), shadowElevation = 7.dp) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp), content = content)
        }
    }
}

@Composable
internal fun RoadmateDockItem(title: String, symbol: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val tone = when {
        !enabled -> GameMist.copy(alpha = .52f)
        selected -> GameInk
        else -> GamePaper
    }
    Row(Modifier.clip(RoundedCornerShape(8.dp))
        .background(if (selected) GameGold else Color.Transparent)
        .selectable(selected, enabled = enabled, role = Role.Tab, onClick = onClick)
        .heightIn(min = 52.dp).padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(symbol, Modifier.clearAndSetSemantics {}, color = if (selected) GameInk else GameGold.copy(alpha = .8f),
            style = MaterialTheme.typography.labelLarge)
        Text(title, color = tone, style = RoadmateType.control.copy(
            fontSize = 18.sp, lineHeight = 24.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium))
    }
}
