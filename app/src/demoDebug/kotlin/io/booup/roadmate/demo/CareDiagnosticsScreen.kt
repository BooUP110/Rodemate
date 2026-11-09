package io.booup.roadmate.demo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.booup.roadmate.model.*
import io.booup.roadmate.R
import io.booup.roadmate.RoadmateViewModel
import io.booup.roadmate.CareContent
import io.booup.roadmate.ProductContent

/** User Care stays above the scrollable, parked-only diagnostics. Not a reviewed driving UI. */
@Composable
fun CareDiagnosticsScreen(viewModel: RoadmateViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val care by viewModel.careDecision.collectAsStateWithLifecycle()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val interactions by viewModel.interactionRecords.collectAsStateWithLifecycle()
    val gate = remember { ContentGate() }
    MaterialTheme {
        Scaffold { insets ->
            Column(
                modifier = Modifier.fillMaxSize().padding(insets).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.build_mode), style = MaterialTheme.typography.titleMedium)
                CareContent(ui, { viewModel.respond(it) }, { id, revision -> viewModel.displayed(id, revision) },
                    { viewModel.selectCompanion(it) })
                if (ui.presentation == null && ui.notice == null) Text("케어 안내를 기다리고 있어요.")
                if (ui.executionStatus == ExecutionStatus.SIMULATED) Text("SIMULATED · 시뮬레이션 결과예요. 실제 차량은 변경하지 않았어요.")
                if (ui.fullContentAllowed) {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Phase 3 · 개발 상태 확인 화면")
                    Text("연결: ${state.connection.status} / ${state.connection.reason ?: "오류 없음"}")
                    Text("데이터 흐름: ${if (state.foreground) "활성" else "중단"}")
                    if (state.connection.status != ConnectionStatus.CONNECTED && state.connection.status != ConnectionStatus.DEGRADED) {
                        Text(stringResource(R.string.setup_pending))
                    }
                    if (state.connection.reason == UnavailableReason.NOT_VERIFIED) {
                        Text(stringResource(R.string.sdk_unavailable))
                    }
                    if (!gate.access(ContentArea.COLLECTION, state).allowed) {
                        Text(stringResource(R.string.parked_content_locked))
                    }
                }
                viewModel.developerPanel?.let { panel -> item { panel() } }
                item { TripDiagnostics(viewModel) }
                item { ProductContent(viewModel) }
                item {
                    Text("CARE_DIAGNOSTICS_DEMO_ONLY", style = MaterialTheme.typography.titleMedium)
                    Text("Active: ${ui.presentation?.eventId ?: "없음"}")
                    ui.presentation?.let { presentation ->
                        Text("Priority: ${presentation.priority}")
                        Text("Evidence: ${presentation.evidence}")
                    }
                    Text("Pending: ${care.pendingEvents.size}")
                    interactions.values.forEach { record -> Text("Interaction: ${record.stage} / ${record.executionStatus}") }
                    care.evaluations.forEach { evaluation ->
                        Text("${evaluation.ruleId}: ${evaluation.status} / ${evaluation.suppression}")
                        Text("Duration: ${evaluation.conditionDurationMillis} ms / cooldown next: ${evaluation.nextEligibleAtMonotonicMillis}")
                    }
                }
                item { ContextReading("주행 제한 (물리적 주차 판정 아님)", state.restrictions) { it.mode.name } }
                item { ContextReading("이동 상태", state.motion) { it.name } }
                item { ContextReading("하향등 상태", state.lowBeam) { it.name } }
                item { ContextReading("공조 설정", state.targetTemperatureCelsius) { "$it °C" } }
                item { ContextReading("외부 온도 컨텍스트", state.outsideTemperatureCelsius) { "$it °C" } }
                item { ContextReading("운전석 점유", state.driverPresent) { if (it) "탑승" else "비어 있음" } }
                item { ContextReading("경고 원시 식별자 (해석 미구현)", state.warnings) { it.joinToString().ifEmpty { "수신된 식별자 없음" } } }
                item { ContextReading("트립 시간", state.tripDrivingSeconds) { "$it 초" } }
                item { ContextReading("트립 거리", state.tripDistanceMeters) { "$it m" } }
                item { ContextReading("위치 (미연동)", state.locationLabel) { it } }
                item { ContextReading("내비 (미연동)", state.navigationLabel) { it } }
                item {
                    HorizontalDivider()
                    Text("정차 전용 콘텐츠 정책 · 실제 화면은 미구현")
                    ContentArea.entries.drop(3).forEach { area ->
                        val access = gate.access(area, state)
                        Text("${area.name}: ${if (access.allowed) "허용" else "잠금"} (${access.reason})")
                    }
                }
                }
                }
            }
        }
    }
}

@Composable
private fun <T> ContextReading(label: String, reading: EvaluatedReading<T>, format: (T) -> String) {
    Column {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Text(reading.currentValue?.let(format) ?: "현재 값 없음")
        Text("출처 ${reading.source} · ${reading.availability} · ${reading.freshness}")
        Text("관측 epoch ms: ${reading.observedAtEpochMillis ?: "없음"} · age ms: ${reading.ageMillis ?: "없음"}")
        (reading.observation as? Reading.Unavailable)?.let { Text("사용 불가: ${it.reason}") }
    }
}
