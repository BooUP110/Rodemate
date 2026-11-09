package io.booup.roadmate.demo

import io.booup.roadmate.model.*

enum class DemoScenarioId { A_NORMAL, B_NIGHT_LIGHT_OFF, C_COLD_MORNING, D_WARNING, E_LONG_DRIVE, F_DISCONNECTED, G_UNSUPPORTED, H_PERMISSION_UNAVAILABLE }

/** Explicit simulation inputs. These values are not care thresholds or manufacturer codes. */
data class DemoScenario(
    val id: DemoScenarioId,
    val title: String,
    val restriction: UIMode = UIMode.PARKED,
    val motion: MotionState = MotionState.PARKED,
    val lowBeam: LowBeamState = LowBeamState.ON,
    val targetCelsius: Double = 22.0,
    val outsideCelsius: Double? = null,
    val warnings: List<String> = emptyList(),
    val tripSeconds: Long = 0,
    val tripMeters: Long = 0,
    val hvacUnsupported: Boolean = false,
    val warningPermissionDenied: Boolean = false,
    val disconnected: Boolean = false,
)

object DemoScenarios {
    val all = listOf(
        DemoScenario(DemoScenarioId.A_NORMAL, "A · 정상 상태"),
        DemoScenario(DemoScenarioId.B_NIGHT_LIGHT_OFF, "B · 야간 하향등 OFF", UIMode.RESTRICTED, MotionState.MOVING, LowBeamState.OFF),
        DemoScenario(DemoScenarioId.C_COLD_MORNING, "C · 추운 아침", targetCelsius = 19.0, outsideCelsius = 3.0),
        DemoScenario(DemoScenarioId.D_WARNING, "D · 미해석 경고 식별자", warnings = listOf("DEMO_WARNING_UNMAPPED")),
        DemoScenario(DemoScenarioId.E_LONG_DRIVE, "E · 긴 트립", UIMode.RESTRICTED, MotionState.MOVING, tripSeconds = 8_100, tripMeters = 120_000),
        DemoScenario(DemoScenarioId.F_DISCONNECTED, "F · 연결 해제", disconnected = true),
        DemoScenario(DemoScenarioId.G_UNSUPPORTED, "G · 공조 미지원", hvacUnsupported = true),
        DemoScenario(DemoScenarioId.H_PERMISSION_UNAVAILABLE, "H · 경고 조회 권한 없음", warningPermissionDenied = true),
    )

    fun frame(scenario: DemoScenario, time: ObservationTime, sequence: Long): VehicleFrame {
        fun <T : Any> observed(value: T): Reading<T> = Reading.Observed(value, DataMetadata(
            DataSource.SIMULATED, time.epochMillis, Freshness.FRESH,
            CapabilityState.SUPPORTED, PermissionState.GRANTED, DataQuality.VALID, time.monotonicMillis,
        ))
        fun unavailable(reason: UnavailableReason) = Reading.Unavailable(reason, DataSource.SIMULATED)
        if (scenario.disconnected) return VehicleFrame(
            sequence, ConnectionState(ConnectionStatus.DISCONNECTED),
            VehicleSnapshot(
                unavailable(UnavailableReason.DISCONNECTED), unavailable(UnavailableReason.DISCONNECTED),
                unavailable(UnavailableReason.DISCONNECTED), unavailable(UnavailableReason.DISCONNECTED),
                unavailable(UnavailableReason.DISCONNECTED), unavailable(UnavailableReason.DISCONNECTED),
                unavailable(UnavailableReason.DISCONNECTED),
            ), restrictions = unavailable(UnavailableReason.DISCONNECTED), motion = unavailable(UnavailableReason.DISCONNECTED),
        )
        return VehicleFrame(
            sequence, ConnectionState(ConnectionStatus.CONNECTED),
            VehicleSnapshot(
                observed(scenario.lowBeam),
                if (scenario.hvacUnsupported) unavailable(UnavailableReason.UNSUPPORTED) else observed(scenario.targetCelsius),
                observed(scenario.tripSeconds),
                if (scenario.warningPermissionDenied) unavailable(UnavailableReason.PERMISSION_DENIED) else observed(scenario.warnings),
                observed(scenario.tripMeters), observed(true),
                scenario.outsideCelsius?.let { observed(it) } ?: unavailable(UnavailableReason.NO_VALUE),
            ),
            restrictions = observed(DrivingRestrictions(
                mode = scenario.restriction,
                // Zero is an explicit simulated unrestricted value, not an invented AAOS flag.
                activeRestrictions = if (scenario.restriction == UIMode.PARKED) 0 else null,
                requiresDistractionOptimization = scenario.restriction != UIMode.PARKED,
                observedAtEpochMillis = time.epochMillis,
            )),
            motion = observed(scenario.motion),
            locationLabel = unavailable(UnavailableReason.NOT_VERIFIED),
            navigationLabel = unavailable(UnavailableReason.NOT_VERIFIED),
        )
    }
}
