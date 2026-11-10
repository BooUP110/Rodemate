package io.booup.roadmate

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ComfortSettingsContent(viewModel:RoadmateViewModel) {
    val state by viewModel.comfortUiState.collectAsStateWithLifecycle()
    if(!state.contentAllowed) return
    var draft by remember(state.preference) { mutableDoubleStateOf(state.preference?.valueCelsius ?: 22.0) }
    DisposableEffect(Unit) { onDispose { viewModel.comfortAction { it.dismiss() } } }
    RoadmateCard {
        Text("정차 중 온도 안내",style=MaterialTheme.typography.titleLarge)
        Text("이 기기에 저장한 선호를 사용해요. 자동으로 학습하거나 차량 설정을 바꾸지 않아요.")
        Text(state.preference?.let { "저장한 선호  "+UiCopy.temperature(it.valueCelsius) } ?: "저장한 선호가 없어요.")
        Row(horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            OutlinedButton(enabled=draft>16,onClick={draft=(draft-.5).coerceAtLeast(16.0)},modifier=Modifier.heightIn(min=56.dp)) { Text("선호 낮추기") }
            Text(UiCopy.temperature(draft),modifier=Modifier.padding(top=18.dp))
            OutlinedButton(enabled=draft<30,onClick={draft=(draft+.5).coerceAtMost(30.0)},modifier=Modifier.heightIn(min=56.dp)) { Text("선호 높이기") }
        }
        Button(onClick={viewModel.comfortAction { it.save(draft) }},modifier=Modifier.heightIn(min=60.dp)) { Text("이 기기의 선호로 저장") }
        if(state.preference!=null) {
            TextButton(onClick={viewModel.comfortAction { it.delete() }},modifier=Modifier.heightIn(min=56.dp)) { Text("저장한 선호 삭제") }
            OutlinedButton(onClick={viewModel.comfortAction { it.request() }},modifier=Modifier.heightIn(min=56.dp)) { Text("정차 온도 안내 보기") }
        }
        state.advice?.let { advice ->
            Text("차량 센서 외기 온도는 "+UiCopy.temperature(advice.outsideCelsius)+"예요.")
            Text("앞좌석 왼쪽 공조 설정은 "+UiCopy.temperature(advice.frontLeftTargetCelsius)+"예요.")
            Text("저장한 선호 온도는 "+UiCopy.temperature(advice.preferredCelsius)+"예요. 원하면 직접 조절해 보세요.")
        }
        if(state.preference!=null && state.advice==null) Text("현재 차량 정보가 확인되면 정차 온도 안내를 볼 수 있어요.")
        state.error?.let { Text(UiCopy.storageError(it),color=MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun MemoryRecallContent(viewModel:RoadmateViewModel) {
    val state by viewModel.recallUiState.collectAsStateWithLifecycle()
    if(!state.contentAllowed) return
    DisposableEffect(Unit) { onDispose { viewModel.closeRecall() } }
    state.summary?.let { summary ->
        RoadmateCard {
            Text("이번 달의 기록 · UTC",style=MaterialTheme.typography.titleLarge)
            Text("${summary.year}년 ${summary.month}월 · ${summary.driveCount}번의 드라이브")
            Text(summary.recordedDurationMillis?.let { "총 기록 시간 "+UiCopy.duration(it) } ?: "총 기록 시간을 확인할 수 없어요.")
            Text("${summary.reflectionCount}개의 드라이브에 회고를 남겼어요.")
            Text("저장된 기록만 정리한 정보예요. 연속 운전 시간이나 피로 판단이 아니에요.",style=MaterialTheme.typography.bodyMedium)
        }
    }
    RoadmateCard {
        Text("최근 별도 기억",style=MaterialTheme.typography.titleLarge)
        state.recent?.let { memory ->
            Text(UiCopy.date(memory.createdAtEpochMillis)+" · UTC")
            Text("기록 시간 "+UiCopy.duration(memory.durationMillis))
            Button(onClick={viewModel.openRecall(memory.memoryId)},modifier=Modifier.heightIn(min=60.dp)) { Text("기억 보기") }
        } ?: Text("별도 저장에 동의하고 한 번 더 확인한 이야기가 여기에 모여요.")
        if(state.openedText!=null) {
            Text(requireNotNull(state.openedText))
            TextButton(onClick={viewModel.closeRecall()},modifier=Modifier.heightIn(min=56.dp)) { Text("기억 닫기") }
        }
    }
}
