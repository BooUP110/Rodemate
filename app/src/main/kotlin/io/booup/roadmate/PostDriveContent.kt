package io.booup.roadmate

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.booup.roadmate.model.*

/** UI owns navigation/drafts/dialogs only. Coordinators revalidate every action. */
@Composable
fun PostDriveContent(viewModel: RoadmateViewModel) {
    val state by viewModel.postDriveUiState.collectAsStateWithLifecycle()
    val trip by viewModel.tripUiState.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    DisposableEffect(Unit) { onDispose { focus.clearFocus(force = true); keyboard?.hide() } }
    if (!state.contentAllowed) return
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var memoryConfirmation by remember(state.session.tripId) { mutableStateOf<TripReflection?>(null) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("드라이브 기억", style = MaterialTheme.typography.headlineMedium)
        Text("함께 달린 순간을, 확인된 기록과 직접 남긴 이야기로 간직해요.", style = MaterialTheme.typography.bodyMedium)
        StorageNotice(state.error)
        MemoryRecallContent(viewModel)
        state.session.card?.let { card ->
            RoadmateCard(tinted = true) {
                RoadmateBadge(if (card.source == DataSource.SIMULATED) "SIMULATED · 기억 카드" else "기억 카드")
                Text(UiCopy.date(card.startedAtEpochMillis), style = MaterialTheme.typography.titleLarge)
                Text("드라이브 기록 시간  " + UiCopy.duration(card.durationMillis), style = MaterialTheme.typography.headlineMedium)
                Text("종료 " + UiCopy.date(card.endedAtEpochMillis) + " · UTC", style = MaterialTheme.typography.bodyMedium)
                card.distanceMeters?.let { Text("확인된 거리  " + it + " m") }
                card.startLabel?.let { Text("출발  " + if (card.source == DataSource.SIMULATED) "시연 출발지" else it) }
                card.endLabel?.let { Text("도착  " + if (card.source == DataSource.SIMULATED) "시연 도착지" else it) }
                Text("달력 기준 " + UiCopy.season(card.calendar.season) + " · " + card.calendar.zoneId, style = MaterialTheme.typography.bodyMedium)
                if (card.careSummary.isNotEmpty()) {
                    Text("이번 드라이브의 안내", style = MaterialTheme.typography.titleMedium)
                    card.careSummary.forEach { summary ->
                        Text(when (summary.rule) {
                            RuleId.LIGHT -> "전조등 안내"; RuleId.HVAC -> "공조 제안"
                            RuleId.REST -> "휴식 권장"; RuleId.WARNING -> "차량 경고"
                        } + " · " + summary.occurrences + "회", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("안내가 발생한 기록이에요. 실제 행동을 수행했다는 뜻은 아니에요.", style = MaterialTheme.typography.bodyMedium)
                }
                card.reflection?.let { reflection ->
                    HorizontalDivider()
                    Text("직접 남긴 이야기", style = MaterialTheme.typography.titleMedium)
                    reflection.mood?.let { Text(UiCopy.mood(it)) }
                    reflection.text?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                    Text(if (reflection.persistence == ReflectionPersistence.TRIP_LOCAL) "이 드라이브에 저장됨" else "현재 화면에서만 보기",
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            state.session.postcard?.let { postcard ->
                RoadmateCard {
                    RoadmateBadge(if (postcard.source == RegionEvidenceSource.DEMO) "SIMULATED · 지역 엽서" else "지역 엽서")
                    Text(if (state.session.reveal) "새로운 기억을 발견했어요" else "함께 모은 엽서", style = MaterialTheme.typography.titleLarge)
                    Text(postcard.regionLabel, style = MaterialTheme.typography.headlineMedium)
                    Text(UiCopy.season(postcard.calendar.season) + "의 드라이브 · 컬렉션에 저장됨")
                }
            }
            if (state.session.stage != PostDriveStage.COMPLETED) {
                if (state.session.stage != PostDriveStage.REFLECTION_PENDING) {
                    Button(onClick = { viewModel.postDriveAction { it.requestReflection(card.tripId) } },
                        modifier = Modifier.heightIn(min = 60.dp)) {
                        Text(if (card.reflection == null) "회고 남기기 · 선택" else "회고 수정")
                    }
                }
                if (state.session.stage == PostDriveStage.REFLECTION_PENDING) {
                    ReflectionEditor(card, viewModel)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    TextButton(onClick = { viewModel.postDriveAction { it.skip(card.tripId) } },
                        modifier = Modifier.heightIn(min = 56.dp)) { Text("회고 건너뛰기") }
                    Button(onClick = { focus.clearFocus(); keyboard?.hide(); viewModel.postDriveAction { it.complete(card.tripId) } },
                        modifier = Modifier.heightIn(min = 60.dp)) { Text("드라이브 정리 완료") }
                }
                Text("회고를 쓰지 않아도 드라이브 정리 보상은 같아요.", style = MaterialTheme.typography.bodyMedium)
            } else Text("정리를 마친 기록이에요. 저장한 회고는 읽기 전용으로 볼 수 있어요.", style = MaterialTheme.typography.bodyMedium)
            if (card.reflection?.persistence == ReflectionPersistence.TRIP_LOCAL) {
                OutlinedButton(enabled = state.memoryEnabled, onClick = { memoryConfirmation = card.reflection },
                    modifier = Modifier.heightIn(min = 56.dp)) { Text("이 이야기를 별도 기억으로 저장") }
                Text(if (state.memoryEnabled) "회고 저장과 별개예요. 한 번 더 확인한 이야기만 기억해요."
                    else "별도 기억을 원하면 설정에서 먼저 허용해 주세요.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("저장된 드라이브", style = MaterialTheme.typography.titleLarge)
        if (state.trips.isEmpty()) RoadmateCard {
            Text("아직 저장된 드라이브가 없어요.", style = MaterialTheme.typography.titleMedium)
            Text(if (state.historyEnabled) "드라이브가 완료되면 이곳에 모여요." else "기록 저장은 기본으로 꺼져 있어요. 설정에서 원할 때 켤 수 있어요.",
                style = MaterialTheme.typography.bodyMedium)
        }
        trip.runtime.completed?.takeIf { record -> state.trips.none { it.tripId == record.tripId } }?.let { record ->
            OutlinedButton(onClick = { viewModel.postDriveAction { it.open(record.tripId) } },
                modifier = Modifier.heightIn(min = 56.dp)) { Text("이번 드라이브 카드 열기 · 현재 세션만") }
        }
        state.trips.reversed().forEach { record ->
            RoadmateCard {
                Text(UiCopy.date(record.startedAtEpochMillis) + " · UTC", style = MaterialTheme.typography.titleMedium)
                Text(UiCopy.duration(record.durationMillis) + "의 드라이브", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = { viewModel.postDriveAction { it.open(record.tripId) } }, modifier = Modifier.heightIn(min = 56.dp)) { Text("기억 카드 열기") }
                    TextButton(onClick = { deleteTarget = record.tripId }, modifier = Modifier.heightIn(min = 56.dp)) { Text("이 기록 삭제") }
                }
            }
        }
    }
    deleteTarget?.let { id ->
        AlertDialog(onDismissRequest = { deleteTarget = null }, title = { Text("이 드라이브를 삭제할까요?") },
            text = { Text("이 기록의 회고와 연결된 기억도 삭제해요. 엽서와 획득한 시즌 진행도는 원본 기록 연결 없이 유지해요.") },
            confirmButton = { TextButton(onClick = { deleteTarget = null; viewModel.postDriveAction { it.delete(id) } }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("취소") } })
    }
    memoryConfirmation?.let { item ->
        AlertDialog(onDismissRequest = { memoryConfirmation = null }, title = { Text("이 이야기를 별도로 기억할까요?") },
            text = { Text("확인한 이야기만 이 기기의 별도 기억에 보관해요. 회고 저장만으로는 만들어지지 않아요.") },
            confirmButton = { TextButton(onClick = { memoryConfirmation = null; viewModel.postDriveAction {
                it.remember(confirmed = true, expectedTripId = item.tripId, expectedReflection = item)
            } }) { Text("기억하기") } },
            dismissButton = { TextButton(onClick = { memoryConfirmation = null }) { Text("취소") } })
    }
}

@Composable
private fun ReflectionEditor(card: DriveMemoryCard, viewModel: RoadmateViewModel) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var text by remember(card.tripId) { mutableStateOf(card.reflection?.text ?: "") }
    var mood by remember(card.tripId) { mutableStateOf(card.reflection?.mood) }
    RoadmateCard {
        Text("오늘의 드라이브는 어땠나요?", style = MaterialTheme.typography.titleLarge)
        Text("선택 사항이에요. 느낌이나 한마디 중 원하는 것만 남겨 주세요.", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ReflectionMood.entries.forEach { choice ->
                FilterChip(selected = mood == choice, onClick = { mood = choice }, label = { Text(UiCopy.mood(choice)) },
                    modifier = Modifier.heightIn(min = 56.dp))
            }
        }
        OutlinedTextField(value = text, onValueChange = { if (it.length <= 4096) text = it },
            label = { Text("한마디 · 선택") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
        Text("회고는 드라이브 기록에 저장돼요. 별도 기억으로 자동 저장하지 않아요.", style = MaterialTheme.typography.bodyMedium)
        Button(enabled = mood != null || text.isNotBlank(), onClick = {
            viewModel.postDriveAction { it.reflect(mood, text, saveLocally = true, expectedTripId = card.tripId) }
            focus.clearFocus(); keyboard?.hide()
        }, modifier = Modifier.heightIn(min = 60.dp)) { Text("회고를 이 기록에 저장") }
        TextButton(enabled = mood != null || text.isNotBlank(), onClick = {
            viewModel.postDriveAction { it.reflect(mood, text, saveLocally = false, expectedTripId = card.tripId) }
            focus.clearFocus(); keyboard?.hide()
        }, modifier = Modifier.heightIn(min = 56.dp)) { Text("현재 세션에서만 보기") }
    }
}

@Composable
fun RecordSettingsContent(viewModel: RoadmateViewModel) {
    val state by viewModel.postDriveUiState.collectAsStateWithLifecycle()
    val care by viewModel.uiState.collectAsStateWithLifecycle()
    if (!state.contentAllowed) return
    var confirmation by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("나의 동행 설정", style = MaterialTheme.typography.headlineMedium)
        Text("기록은 이 기기에만 보관해요. 저장할지는 직접 선택해 주세요.", style = MaterialTheme.typography.bodyMedium)
        StorageNotice(state.error)
        ComfortSettingsContent(viewModel)
        RoadmateCard {
            Text("드라이브 기록", style = MaterialTheme.typography.titleLarge)
            Text(if (state.historyEnabled) "저장 켜짐" else "저장 꺼짐", style = MaterialTheme.typography.bodyLarge)
            Text("완료된 드라이브와 직접 저장한 회고를 보관해요. 끄면 저장된 기록·회고·연결된 기억을 지워요.",
                style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { if (state.historyEnabled) confirmation = "history-off" else
                viewModel.postDriveAction { it.consent(ConsentScope.TRIP_HISTORY, true) } },
                modifier = Modifier.heightIn(min = 60.dp)) { Text(if (state.historyEnabled) "드라이브 기록 저장 끄기" else "드라이브 기록 저장 켜기") }
        }
        RoadmateCard {
            Text("별도 기억", style = MaterialTheme.typography.titleLarge)
            Text(if (state.memoryEnabled) "허용됨" else "허용하지 않음")
            Text("회고에서 한 번 더 확인한 이야기만 별도로 기억해요. 이 설정만 켜거나 회고를 저장했다고 자동으로 기억하지 않아요.",
                style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { if (state.memoryEnabled) confirmation = "memory-off" else
                viewModel.postDriveAction { it.consent(ConsentScope.EMOTIONAL_MEMORY, true) } },
                modifier = Modifier.heightIn(min = 56.dp)) { Text(if (state.memoryEnabled) "별도 기억 허용 끄기" else "별도 기억 허용 켜기") }
        }
        RoadmateCard {
            Text("함께할 동반자", style = MaterialTheme.typography.titleLarge)
            Text("현재 " + care.companion.displayName, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CompanionProfiles.all.forEach { profile ->
                    FilterChip(selected = care.companion.id == profile.id, onClick = { viewModel.selectCompanion(profile.id) },
                        label = { Text(profile.displayName) }, modifier = Modifier.heightIn(min = 56.dp))
                }
            }
            Text("오리지널 임시 이름과 디자인이에요. 동반자가 바뀌어도 안전 안내와 우선순위는 같아요.",
                style = MaterialTheme.typography.bodyMedium)
        }
        Text("현재 안내는 기능 설정을 위한 문안이에요. 최종 개인정보 안내는 검토 중이에요.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(enabled = state.trips.isNotEmpty(), onClick = { confirmation = "clear" },
            modifier = Modifier.heightIn(min = 56.dp)) { Text("드라이브 기록 전체 삭제") }
    }
    confirmation?.let { action ->
        AlertDialog(onDismissRequest = { confirmation = null },
            title = { Text(when (action) { "clear" -> "모든 드라이브를 삭제할까요?"; "memory-off" -> "별도 기억 허용을 끌까요?"; else -> "드라이브 기록 저장을 끌까요?" }) },
            text = { Text(if (action == "memory-off") "별도로 저장된 기억을 지워요. 드라이브 기록과 회고는 유지해요."
                else "기록·회고·연결된 기억을 지워요. 엽서와 획득한 시즌 진행도는 원본 기록 연결 없이 유지해요.") },
            confirmButton = { TextButton(onClick = { confirmation = null; viewModel.postDriveAction {
                when (action) {
                    "clear" -> it.clearHistory(confirmed = true)
                    "memory-off" -> it.consent(ConsentScope.EMOTIONAL_MEMORY, false)
                    else -> it.consent(ConsentScope.TRIP_HISTORY, false)
                }
            } }) { Text("확인") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("취소") } })
    }
}

@Composable
fun CollectionContent(viewModel: RoadmateViewModel) {
    val state by viewModel.postDriveUiState.collectAsStateWithLifecycle()
    if (!state.contentAllowed) return
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("드라이브 컬렉션", style = MaterialTheme.typography.headlineMedium)
        Text("함께 발견한 지역의 기억을 모아요.", style = MaterialTheme.typography.bodyMedium)
        if (state.collection.isEmpty()) RoadmateCard(tinted = true) {
            Text("아직 모인 엽서가 없어요", style = MaterialTheme.typography.titleLarge)
            Text("지원되는 지역 기록이 생기면 여기에 모여요. 확인되지 않은 장소의 엽서는 만들지 않아요.",
                style = MaterialTheme.typography.bodyMedium)
        }
        state.collection.forEach { postcard ->
            RoadmateCard(tinted = true) {
                RoadmateBadge(if (postcard.source == RegionEvidenceSource.DEMO) "SIMULATED · 지역 엽서" else "지역 엽서")
                Text(postcard.regionLabel, style = MaterialTheme.typography.headlineMedium)
                Text(UiCopy.season(postcard.calendar.season) + "의 기억 · " + UiCopy.date(postcard.acquiredAtEpochMillis) + " UTC")
            }
        }
        Text("드라이브 기록을 지워도 모은 엽서는 원본 기록 연결 없이 유지해요.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun StorageNotice(error: StorageError?) {
    if (error == null) return
    Text(UiCopy.storageError(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
}
