package io.booup.roadmate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = Color(0xFF183D36)
private val Palette = lightColorScheme(
    primary = Color(0xFF205E50), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEEE4), onPrimaryContainer = Ink,
    secondary = Color(0xFF70502E), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF5E7D3), onSecondaryContainer = Color(0xFF48351F),
    background = Color(0xFFF7F6EF), onBackground = Ink,
    surface = Color(0xFFFFFEFA), onSurface = Ink,
    surfaceVariant = Color(0xFFE8EDE5), onSurfaceVariant = Color(0xFF435950),
    outline = Color(0xFF667A70), error = Color(0xFF9F2626), onError = Color.White,
    errorContainer = Color(0xFFFFE2DF), onErrorContainer = Color(0xFF631A19),
)

@Composable
fun RoadmateTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Palette, typography = Typography(
        headlineLarge = TextStyle(fontSize = 42.sp, lineHeight = 52.sp, fontWeight = FontWeight.Bold),
        headlineMedium = TextStyle(fontSize = 36.sp, lineHeight = 46.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 26.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 26.sp, lineHeight = 36.sp),
        bodyMedium = TextStyle(fontSize = 22.sp, lineHeight = 32.sp),
        bodySmall = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
        labelLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
        labelSmall = TextStyle(fontSize = 20.sp, lineHeight = 28.sp),
    ), shapes = Shapes(medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp)), content = content)
}

@Composable
fun RoadmateCard(modifier: Modifier = Modifier, tinted: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = if (tinted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
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

/** Replaceable original vector placeholder; no external IP or continuous animation. */
@Composable
fun CompanionPortrait(name: String, warm: Boolean, modifier: Modifier = Modifier) {
    val body = if (warm) Color(0xFFECD3B5) else Color(0xFFB9D9C6)
    Canvas(modifier.semantics { contentDescription = name + " · 오리지널 임시 동반자" }.height(260.dp)) {
        val w = size.width.coerceAtMost(size.height * 1.2f); val h = size.height
        translate(left = (size.width - w) / 2) {
        drawOval(Color(0x22205E50), Offset(w * .15f, h * .83f), Size(w * .7f, h * .09f))
        drawCircle(body, w * .105f, Offset(w * .27f, h * .28f))
        drawCircle(body, w * .105f, Offset(w * .73f, h * .28f))
        drawRoundRect(body, Offset(w * .16f, h * .25f), Size(w * .68f, h * .6f), CornerRadius(w * .22f))
        drawCircle(Ink, w * .023f, Offset(w * .38f, h * .49f))
        drawCircle(Ink, w * .023f, Offset(w * .62f, h * .49f))
        drawArc(Ink, 20f, 140f, false, Offset(w * .45f, h * .51f), Size(w * .1f, h * .08f), style = Stroke(w * .014f))
        drawOval(Color(0xFFE4AC99), Offset(w * .26f, h * .55f), Size(w * .1f, h * .05f))
        drawOval(Color(0xFFE4AC99), Offset(w * .64f, h * .55f), Size(w * .1f, h * .05f))
        val leaf = Path().apply {
            moveTo(w * .5f, h * .27f)
            cubicTo(w * .47f, h * .11f, w * .64f, h * .1f, w * .65f, h * .12f)
            cubicTo(w * .65f, h * .25f, w * .56f, h * .29f, w * .5f, h * .27f)
        }
        drawPath(leaf, Color(0xFF3F8064))
        }
    }
}
