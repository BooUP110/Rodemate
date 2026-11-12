package io.booup.roadmate.demo

import io.booup.roadmate.Expedition
import io.booup.roadmate.JourneyPass
import io.booup.roadmate.RuntimeBindings
import io.booup.roadmate.model.*
import io.booup.roadmate.prepareTourStorage
import io.booup.roadmate.progress.SafeProgressEngine
import io.booup.roadmate.storage.FileTripRepository
import io.booup.roadmate.testing.testEncryption
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DemoScreenTourTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun sampleVisitRewardAndDrawAreSeparateFromOrdinaryDemoProgress() = runTest {
        val epoch = Instant.parse("2028-04-15T12:00:00Z").toEpochMilli()
        val clock = ContextClock { ObservationTime(epoch + testScheduler.currentTime, 100_000 + testScheduler.currentTime) }
        val encryption = testEncryption(RuntimeMode.DEMO)
        val ordinary = RuntimeBindings.create(backgroundScope, clock, temporary.newFolder(), encryption)
        val ordinaryTrips = requireNotNull(ordinary.trips)
        ordinaryTrips.initialize()
        val before = ordinaryTrips.repository.state.value

        val sample = FileTripRepository(temporary.newFolder(), RuntimeMode.DEMO,
            progressEngine = SafeProgressEngine(explorationRewards = true), encryption = encryption)
        assertTrue(prepareTourStorage(sample, clock.now()))
        val firstPoint = Expedition.GANGNEUNG.places.first().id
        val visits = sample.state.value.visits.map { it.regionId }.toSet()
        assertEquals(setOf(firstPoint), visits)
        assertEquals(Expedition.GANGNEUNG.places[1], Expedition.GANGNEUNG.next(visits))
        assertEquals(listOf(firstPoint), sample.state.value.postcards.map { it.key.regionId })

        val tour = RuntimeBindings.create(backgroundScope, clock, tourStorage = sample)
        val trips = requireNotNull(tour.trips)
        val progress = requireNotNull(tour.progress)
        val game = requireNotNull(tour.journeyGame)
        trips.initialize()
        progress.initialize()
        tour.repository.foreground()
        advanceTimeBy(1_200)
        runCurrent()
        trips.update(tour.repository.context.value, CareDecision())
        progress.refresh()
        val season = requireNotNull(progress.uiState.value.active)
        assertTrue(season.rewards.any { it.reward.track == RewardTrack.FREE && it.state == RewardClaimState.AVAILABLE })
        assertEquals(1, game.tickets(game.state.value))
        assertTrue(game.draw() in setOf(JourneyPass.COAST, JourneyPass.STARS))
        assertEquals(0, game.tickets(game.state.value))
        assertEquals(before, ordinaryTrips.repository.state.value)
        assertTrue(ordinaryTrips.repository.state.value.postcards.isEmpty())
        tour.repository.close()
        ordinary.repository.close()
    }
}
