package io.booup.roadmate

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import io.booup.roadmate.model.CompanionProfile

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun RoadmateHomeStage(
    insets: PaddingValues, profile: CompanionProfile, preview: CompanionPreview?,
    status: String, drivingState: String, postcards: Int, xp: Long?, openStory: (() -> Unit)?,
    openMap: () -> Unit, openRoster: () -> Unit, openWardrobe: (() -> Unit)?, endTrial: (() -> Unit)?,
    openMemory: () -> Unit, openSettings: () -> Unit, openSeason: () -> Unit,
    vehicleStatus: String, historyEnabled: Boolean, availableRewards: Int,
    petName: String?, openPet: (() -> Unit)?,
    navigation: @Composable () -> Unit, priorityCare: @Composable () -> Unit,
    vehicleDetails: @Composable ColumnScope.() -> Unit,
) {
    var showVehicle by remember { mutableStateOf(false) }
    val atlas = artwork("journey/postcards.png")
    Box(Modifier.fillMaxSize().background(GameInk)) {
        RoadmateLandscape("home-parked.png", 3, Modifier.matchParentSize(), atlas)
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(
            0f to GameInk.copy(alpha = .82f), .43f to GameInk.copy(alpha = .22f), .75f to Color.Transparent)))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to GameInk.copy(alpha = .55f), .25f to Color.Transparent, .72f to Color.Transparent,
            1f to GameInk.copy(alpha = .91f))))

        BoxWithConstraints(Modifier.fillMaxSize().padding(insets)) {
            val wide = maxWidth >= 740.dp
            val margin = if (wide) 36.dp else 16.dp
            val titleInset = margin + if (wide) 16.dp else 4.dp
            val actorSize = minOf(maxWidth * if (wide) .38f else .63f, maxHeight * if (wide) .64f else .34f)
            val actor = Modifier.align(BiasAlignment(if (wide) .65f else .35f, if (wide) .34f else -.30f)).size(actorSize)
            if (preview == null) OriginalCompanionPortrait(profile, actor, roam = true, onOpenRoster = openRoster)
            else CompanionPreviewPortrait(preview, actor, roam = true, onOpenRoster = openRoster)

            Column(Modifier.align(if (wide) BiasAlignment(-1f, .14f) else Alignment.BottomStart)
                .padding(start = titleInset, end = margin, bottom = if (wide) 72.dp else 106.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.app_name), color = GamePaper, style = RoadmateType.heading.copy(
                    fontSize = if (wide) 70.sp else 40.sp, lineHeight = if (wide) 78.sp else 48.sp))
                Text(profile.displayName, color = GameGold,
                    style = RoadmateType.cardTitle.copy(fontSize = if (wide) 28.sp else 22.sp, lineHeight = 34.sp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (openStory != null) GameButton("강릉 이야기", openStory, primary = true)
                    GameButton("탐험 시작", openMap, primary = openStory == null)
                    GameButton("드라이브 기억", openMemory)
                    if (availableRewards > 0) GameButton("받을 보상 ${availableRewards}개", openSeason)
                }
            }

            FlowRow(Modifier.align(if (wide) BiasAlignment(.92f, .66f) else BiasAlignment(1f, -.04f))
                .padding(horizontal = margin), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (endTrial != null) GameButton("체험 끝내기", endTrial)
                GameButton("동행 바꾸기", openRoster)
                if (openWardrobe != null) GameButton("옷장", openWardrobe)
            }

            Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = margin, vertical = 12.dp)) {
                navigation()
            }

            Column(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = margin, vertical = 12.dp)
                .zIndex(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GamePill(status)
                    GamePill("엽서 $postcards")
                    if (xp != null) GamePill("$xp XP")
                }
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GamePill(vehicleStatus)
                    GameButton(if (historyEnabled) "기록 저장 켜짐" else "기록 저장 꺼짐", openSettings)
                    GameButton("차량 상태", { showVehicle = true })
                    openPet?.let { open -> GameButton(petName?.let { "$it 와 함께" } ?: "반려동물 동행",open,primary=petName!=null) }
                }
                priorityCare()
            }
        }
    }

    if (showVehicle) AlertDialog(onDismissRequest = { showVehicle = false },
        title = { Text(drivingState, style = MaterialTheme.typography.titleMedium) }, text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), content = vehicleDetails)
        }, confirmButton = { GameButton("닫기", { showVehicle = false }) })
}
