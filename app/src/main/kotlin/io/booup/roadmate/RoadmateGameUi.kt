package io.booup.roadmate

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal val GameInk = Color(0xFF111D2B)
internal val GamePaper = Color(0xFFFFF6DE)
internal val GameGold = Color(0xFFF5D795)
internal val GameMist = Color(0xFFBDD1CE)

@Composable
internal fun GameButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    primary: Boolean = false, enabled: Boolean = true) {
    Button(onClick, modifier.heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, GameGold.copy(alpha = if (primary) .92f else .44f)),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) GameGold else GameInk.copy(alpha = .83f),
            contentColor = if (primary) GameInk else GamePaper,
            disabledContainerColor = GameInk.copy(alpha = .68f), disabledContentColor = GameMist)) {
        Text(label, style = RoadmateType.control)
    }
}

@Composable
internal fun GamePill(label: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = GameInk.copy(alpha = .80f), shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, GameGold.copy(alpha = .32f))) {
        Text(label, Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = GamePaper, style = RoadmateType.metric)
    }
}
