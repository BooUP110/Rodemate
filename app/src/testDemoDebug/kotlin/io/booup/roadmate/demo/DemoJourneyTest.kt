package io.booup.roadmate.demo

import io.booup.roadmate.CompanionPreview
import io.booup.roadmate.RuntimeBindings
import io.booup.roadmate.journeyPlaces
import io.booup.roadmate.model.*
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DemoJourneyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun visitUnlocksOneDistrictAndOnePostcardAndAdvancesSeason() = runTest {
        val epoch = Instant.parse("2026-11-10T00:00:00Z").toEpochMilli()
        val clock = ContextClock { ObservationTime(epoch + testScheduler.currentTime, 100_000 + testScheduler.currentTime) }
        val preview = CompanionPreview(CompanionProfiles.dodam, "portrait.png", "테스트 시즌")
        val binding = RuntimeBindings.create(backgroundScope, clock, temporary.newFolder(), io.booup.roadmate.testing.testEncryption(RuntimeMode.DEMO), listOf(preview))
        val trips = requireNotNull(binding.trips)
        val progress = requireNotNull(binding.progress)
        val post = requireNotNull(binding.postDrive)
        val visit = requireNotNull(binding.simulateVisit)
        trips.initialize(); progress.initialize(); binding.repository.foreground()
        advanceTimeBy(1_200); runCurrent()
        trips.update(binding.repository.context.value, CareDecision())
        assertTrue(post.consent(ConsentScope.TRIP_HISTORY, true))

        val place = journeyPlaces.first()
        val result = async { visit(place) }
        runCurrent()
        val samples = mutableListOf<String>()
        repeat(16) {
            advanceTimeBy(1_000); runCurrent()
            val vehicle = binding.repository.context.value
            // Finish the real file checkpoint before the virtual replay clock can advance.
            runBlocking { trips.update(vehicle, CareDecision()) }
            runCurrent()
            val state = trips.uiState.value
            samples += "${testScheduler.currentTime}: ${vehicle.motion.currentValue}/${state.runtime.state}/${state.operationError}"
        }
        assertTrue("${samples.joinToString()} · post=${post.uiState.value.error}", result.await())
        val saved = trips.repository.state.value
        assertEquals(listOf(place.id), saved.visits.map { it.regionId })
        assertEquals(listOf(place.id), saved.postcards.map { it.key.regionId })
        advanceTimeBy(1_200); runCurrent()
        runBlocking { trips.update(binding.repository.context.value, CareDecision()) }
        assertTrue("A duplicate is tried after fresh parked observations resume", post.controlsAllowed())
        assertTrue(visit(place))
        assertEquals(1, trips.repository.state.value.postcards.size)
        runBlocking { progress.refresh() }
        val active = requireNotNull(progress.uiState.value.active)
        assertEquals(15L, active.progress.totalXp)
        assertTrue(active.missions.any { it.visible && it.completed && it.mission.missionId == "first-postcard" })
        runBlocking {
            progress.previewPremium(active.season.seasonId, true)
            assertTrue(progress.claim(active.season.seasonId, "journey-frame"))
        }
        binding.repository.close()
    }
}
