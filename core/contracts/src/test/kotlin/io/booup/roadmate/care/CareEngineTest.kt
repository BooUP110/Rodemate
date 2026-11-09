package io.booup.roadmate.care

import io.booup.roadmate.model.*
import org.junit.Assert.*
import org.junit.Test

class CareEngineTest {
    private val onlyLight: (VehicleFrame) -> VehicleFrame = { it.copy(care = it.care.copy(
        preference = Reading.Unavailable(UnavailableReason.NO_VALUE),
        continuousDriving = Reading.Unavailable(UnavailableReason.NO_VALUE))) }

    private fun run(engine: DeterministicCareEngine, clock: TestCareClock, elapsed: Long,
        transform: (VehicleFrame) -> VehicleFrame = onlyLight, session: Long = 1): CareDecision {
        clock.elapsed = elapsed
        return engine.evaluate(transform(frame(elapsed)).context(elapsed, sessionId = session))
    }
    private fun run(engine: DeterministicCareEngine, clock: TestCareClock, elapsed: Long,
        transform: (VehicleFrame) -> VehicleFrame): CareDecision = run(engine, clock, elapsed, transform, 1)
    @Test fun sustainedLightReachesExactThresholdWithFreshOverlappingSamples() {
        val clock = TestCareClock(); val engine = DeterministicCareEngine(clock)
        assertNull(run(engine, clock, 0).activeEvent)
        assertNull(run(engine, clock, 4_999).activeEvent)
        assertNull(run(engine, clock, 9_999).activeEvent)
        val decision = run(engine, clock, 10_000)
        assertEquals(CarePriority.P1_SAFETY, decision.activeEvent?.priority)
        assertEquals(10_000L, decision.activeEvent?.conditionDurationMillis)
        assertEquals(3, decision.activeEvent?.evidence?.size)
        assertTrue(decision.activeEvent!!.evidence.all { it.source == DataSource.SIMULATED && it.ageMillis == 0L })
    }
    @Test fun missingSamplesCannotBridgeAStaleIntervalEvenWithFreshFinalSample() {
        val clock = TestCareClock(); val engine = DeterministicCareEngine(clock)
        run(engine, clock, 0)
        val result = run(engine, clock, 10_000)
        assertNull(result.activeEvent)
        assertEquals(0L, result.evaluations.single { it.ruleId == RuleId.LIGHT }.conditionDurationMillis)
    }
    @Test fun staleCachedContextIsRecheckedAtConsumptionAndResetsDuration() {
        val clock = TestCareClock(); val engine = DeterministicCareEngine(clock)
        val cached = onlyLight(frame()).context()
        engine.evaluate(cached); clock.elapsed = 6_000
        val stale = engine.evaluate(cached)
        assertNull(stale.activeEvent)
        assertEquals(RuleStatus.INELIGIBLE_STALE, stale.evaluations.single { it.ruleId == RuleId.LIGHT }.status)
        assertNull(run(engine, clock, 7_000).activeEvent)
        assertNull(run(engine, clock, 12_000).activeEvent)
        assertEquals(CarePriority.P1_SAFETY, run(engine, clock, 17_000).activeEvent?.priority)
    }
    @Test fun restoredLightClearsEventAndStartsNewEpisode() {
        val clock = TestCareClock(); val engine = DeterministicCareEngine(clock, CareConfig(lightSustainMillis = 0))
        val first = run(engine, clock, 0).activeEvent!!
        assertNull(run(engine, clock, 1) { onlyLight(it).copy(vehicle = it.vehicle.copy(lowBeam = observed(LowBeamState.ON, 1))) }.activeEvent)
        val second = run(engine, clock, 2).emittedEvents.single()
        assertNotEquals(first.eventId, second.eventId)
    }
    @Test fun disconnectAndBackgroundRemoveRealtimeEvents() {
        val clock = TestCareClock(); val engine = DeterministicCareEngine(clock, CareConfig(lightSustainMillis = 0))
        run(engine, clock, 0)
        assertNull(run(engine, clock, 1) { it.copy(connection = ConnectionState(ConnectionStatus.DISCONNECTED)) }.activeEvent)
        val cached = frame(2).context(2, foreground = false)
        clock.elapsed = 2
        assertTrue(engine.evaluate(cached).emittedEvents.isEmpty())
    }
    @Test fun reconnectSessionBoundaryResetsEvenWhenDisconnectedStateWasConflated() {
        val clock = TestCareClock(); val engine = DeterministicCareEngine(clock)
        run(engine, clock, 0); run(engine, clock, 5_000)
        val decision = run(engine, clock, 10_000, session = 2)
        assertNull(decision.activeEvent)
        assertEquals(0L, decision.evaluations.single { it.ruleId == RuleId.LIGHT }.conditionDurationMillis)
    }
    @Test fun foregroundLifecycleSessionBoundaryResetsConditionTime() {
        val clock = TestCareClock(); val engine = DeterministicCareEngine(clock)
        run(engine, clock, 0); run(engine, clock, 5_000)
        assertNull(run(engine, clock, 10_000, session = 3).activeEvent)
    }
    @Test fun clockRollbackCannotBecomeElapsedConditionTime() {
        val clock = TestCareClock(); val engine = DeterministicCareEngine(clock)
        run(engine, clock, 5_000); run(engine, clock, 10_000)
        val result = run(engine, clock, 0)
        assertNull(result.activeEvent)
        assertEquals(0L, result.evaluations.single { it.ruleId == RuleId.LIGHT }.conditionDurationMillis)
    }
    @Test fun duplicateContextDoesNotReemitButKeepsActiveEvent() {
        val clock = TestCareClock(); val engine = DeterministicCareEngine(clock, CareConfig(lightSustainMillis = 0))
        val first = run(engine, clock, 0)
        val duplicate = run(engine, clock, 0)
        assertEquals(1, first.emittedEvents.size)
        assertEquals(first.activeEvent, duplicate.activeEvent)
        assertTrue(duplicate.emittedEvents.isEmpty())
        assertEquals(Suppression.DUPLICATE, duplicate.evaluations.single { it.ruleId == RuleId.LIGHT }.suppression)
    }
    @Test fun p1CooldownExpiresAtBoundaryAndNotificationCountIsBounded() {
        val clock = TestCareClock(); val engine = DeterministicCareEngine(clock,
            CareConfig(lightSustainMillis = 0, p1RepeatIntervalMillis = 10, p1MaxNotificationsPerEpisode = 2))
        assertEquals(1, run(engine, clock, 0).emittedEvents.size)
        val suppressed = run(engine, clock, 9)
        assertTrue(suppressed.emittedEvents.isEmpty())
        assertEquals(Suppression.COOLDOWN, suppressed.evaluations.single { it.ruleId == RuleId.LIGHT }.suppression)
        assertEquals(1, run(engine, clock, 10).emittedEvents.size)
        val limited = run(engine, clock, 20)
        assertTrue(limited.emittedEvents.isEmpty())
        assertNotNull(limited.activeEvent)
        assertEquals(Suppression.REPEAT_LIMIT, limited.evaluations.single { it.ruleId == RuleId.LIGHT }.suppression)
    }
    @Test fun p2CooldownIsCentralConfigAndSubjectSpecific() {
        val clock = TestCareClock(); val config = CareConfig(cooldowns = RuleId.entries.associateWith { 20L })
        val engine = DeterministicCareEngine(clock, config)
        assertEquals(2, run(engine, clock, 0, { it }).emittedEvents.size)
        assertTrue(run(engine, clock, 19, { it }).emittedEvents.isEmpty())
        assertEquals(2, run(engine, clock, 20, { it }).emittedEvents.size)
    }
    @Test fun restCounterResetAndNewDriveSessionAllowANewRecommendation() {
        val clock = TestCareClock(); val engine = DeterministicCareEngine(clock)
        val first = run(engine, clock, 0, { it }).emittedEvents.single { it.ruleId == RuleId.REST }
        val cleared = run(engine, clock, 1) { it.copy(care = it.care.copy(continuousDriving = observed(ContinuousDriving(0, true, "new"), 1))) }
        assertTrue((listOfNotNull(cleared.activeEvent) + cleared.pendingEvents).none { it.ruleId == RuleId.REST })
        val again = run(engine, clock, 2, { it }).emittedEvents.single { it.ruleId == RuleId.REST }
        assertNotEquals(first.eventId, again.eventId)
    }
    @Test fun p1PreemptsWithoutLosingPendingP2ToCooldown() {
        val clock = TestCareClock(); val engine = DeterministicCareEngine(clock)
        run(engine, clock, 0, { it }); run(engine, clock, 5_000, { it })
        val all = run(engine, clock, 10_000, { it })
        assertEquals(RuleId.LIGHT, all.activeEvent?.ruleId)
        assertEquals(listOf(RuleId.HVAC, RuleId.REST), all.pendingEvents.map { it.ruleId })
        assertEquals(listOf(RuleId.LIGHT), all.emittedEvents.map { it.ruleId })
        val restored = run(engine, clock, 10_001) { it.copy(vehicle = it.vehicle.copy(lowBeam = observed(LowBeamState.ON, 10_001))) }
        assertEquals(RuleId.HVAC, restored.activeEvent?.ruleId)
        assertEquals(listOf(RuleId.REST), restored.pendingEvents.map { it.ruleId })
        assertTrue(restored.emittedEvents.isEmpty())
    }
    @Test fun reducerIsReferentiallyDeterministicWithFixedClockAndState() {
        val reducer = CareReducer(CareConfig(lightSustainMillis = 0))
        val context = frame().context(); val state = CareRuntimeState()
        val expected = reducer.evaluate(context, state, time())
        repeat(10) { assertEquals(expected, reducer.evaluate(context, state, time())) }
    }
    @Test fun replayingTheSameClockAndInputsProducesSameEventsAndSuppression() {
        fun replay(): List<CareDecision> {
            val clock = TestCareClock(); val engine = DeterministicCareEngine(clock)
            return listOf(0L, 5_000, 10_000, 10_000, 10_001).map { run(engine, clock, it, { f -> f }) }
        }
        assertEquals(replay(), replay())
    }
    @Test fun resetStartsANewRuntimeWithNoInheritedCooldown() {
        val clock = TestCareClock(); val engine = DeterministicCareEngine(clock, CareConfig(lightSustainMillis = 0))
        run(engine, clock, 0); engine.reset()
        assertEquals(1, run(engine, clock, 1).emittedEvents.size)
    }
    @Test fun productionRejectsSimulatedEvidenceEvenWhenBypassingStore() {
        val clock = TestCareClock(); val engine = DeterministicCareEngine(clock, CareConfig(lightSustainMillis = 0))
        val decision = engine.evaluate(frame().context(mode = RuntimeMode.PRODUCTION))
        assertNull(decision.activeEvent)
        assertTrue(decision.evaluations.all { it.status == RuleStatus.INELIGIBLE_SOURCE })
    }
    @Test fun staleIntervalAlsoClearsAnAlreadyActiveEvent() {
        val clock = TestCareClock(); val engine = DeterministicCareEngine(clock, CareConfig(lightSustainMillis = 0))
        val cached = onlyLight(frame()).context()
        assertNotNull(engine.evaluate(cached).activeEvent)
        clock.elapsed = 5_001
        assertNull(engine.evaluate(cached).activeEvent)
    }
    @Test fun separateWarningSubjectsDoNotSuppressEachOther() {
        val clock = TestCareClock(); val engine = DeterministicCareEngine(clock)
        val transform: (VehicleFrame) -> VehicleFrame = { it.copy(care = it.care.copy(safetySignals = observed(listOf(
            SafetySignal("first", SafetyClassification.SERVICE_RECOMMENDED), SafetySignal("second", SafetyClassification.SERVICE_RECOMMENDED))))) }
        assertEquals(2, run(engine, clock, 0, transform).emittedEvents.count { it.ruleId == RuleId.WARNING })
        assertTrue(run(engine, clock, 0, transform).emittedEvents.isEmpty())
    }
}
