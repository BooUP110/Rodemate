package io.booup.roadmate

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.booup.roadmate.model.CompanionProfile
import io.booup.roadmate.model.CompanionProfiles

@Composable
internal fun MoonlightRoster(current: CompanionProfile, state: JourneyGameState, viewModel: RoadmateViewModel,
    close: () -> Unit) {
    val owned = viewModel.companionPreviews.filter { it.profile.id in state.companions }
    AlertDialog(onDismissRequest = close, title = { Text("동행 선택") }, text = {
        Column(Modifier.heightIn(max = 330.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompanionProfiles.all.forEach { original ->
                OutlinedButton({ viewModel.equipDefaultCompanion(original.id); close() }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(original.displayName.substringBefore(" (") + if (current.id == original.id) " · 동행 중" else "")
                }
            }
            owned.forEach { preview ->
                OutlinedButton({ viewModel.equipJourneyCompanion(preview.profile.id); close() }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(preview.profile.displayName + if (current.id == preview.profile.id) " · 동행 중" else "")
                }
            }
        }
    }, confirmButton = { TextButton(close) { Text("닫기") } })
}

@Composable
internal fun MoonlightWardrobe(state: JourneyGameState, viewModel: RoadmateViewModel, companion: CompanionProfile, close: () -> Unit) {
    val premium = MOONLIGHT_DEMO_PREMIUM in state.claimed
    AlertDialog(onDismissRequest = close, title = { Text("강릉 패스 의상") }, text = {
        Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MoonlightOutfit.entries.forEach { outfit ->
                val owned = outfit.claimKey in state.claimed
                val equipped = state.moonlightOutfit == outfit.name
                val unlocked = state.moonlightCompleted >= outfit.chapter
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (outfit == MoonlightOutfit.JACKET && companion.id == CompanionProfiles.dodam.id) {
                        OriginalCompanionPortrait(companion, Modifier.size(82.dp), otterMotion = OtterMotion.SAILOR_COAT)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(outfit.label, style = MaterialTheme.typography.bodyMedium)
                        Text("${if (outfit.premium) "프리미엄" else "무료"} · ${outfit.chapter}장 완료", style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(enabled = owned || (unlocked && (!outfit.premium || premium)), onClick = {
                        viewModel.journeyAction {
                            if (owned) equipMoonlightOutfit(outfit.takeUnless { equipped }) else claimMoonlightOutfit(outfit)
                        }
                    }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(when { equipped -> "벗기"; owned -> "입기"; !unlocked -> "잠김"; outfit.premium && !premium -> "전용"; else -> "받기" })
                    }
                }
            }
            if (!premium) OutlinedButton({ viewModel.journeyAction { grantDemoMoonlightPremium() } }, Modifier.heightIn(min = 52.dp)) {
                Text("프리미엄 권한 시연")
            }
        }
    }, confirmButton = { TextButton(close) { Text("닫기") } })
}
