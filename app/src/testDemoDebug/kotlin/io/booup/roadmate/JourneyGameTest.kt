package io.booup.roadmate

import io.booup.roadmate.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class JourneyGameTest {
    @Test fun ticketsChoicesAndConcurrentDrawsUnlockEachPassOnlyOnce() = runBlocking {
        val fixture = Fixture()
        val game = fixture.create()
        assertNull(game.draw())
        fixture.parked = true
        fixture.writable = false
        assertNull(game.draw())
        assertEquals(1, game.tickets(game.state.value))
        fixture.writable = true
        val first = listOf(async { game.draw() }, async { game.draw() }).awaitAll().filterNotNull().single()
        assertFalse(game.choose(first))

        val coast = coastalPlaces.first()
        fixture.addCard(coast)
        assertTrue(game.openCard(coast.id))
        assertTrue(game.star(coast.id))
        assertFalse(game.star(coast.id))
        assertTrue(game.hasLine(game.stamps(game.state.value)))
        val selected = game.candidates(game.state.value).first()
        assertTrue(game.choose(selected))
        assertFalse(game.choose(game.candidates(game.state.value).single()))

        fixture.addCard(coastalPlaces.last())
        val remaining = game.candidates(game.state.value).single()
        assertEquals(remaining, game.draw())
        assertNull(game.draw())
        assertEquals(setOf(first, selected, remaining), fixture.create().state.value.grants.map { it.pass }.toSet())
    }

    @Test fun sharedLevelAndPremiumControlFinalClaimAndACompletedTripUnlocksMemory() = runBlocking {
        val fixture = Fixture().apply { parked = true }
        val game = fixture.create()
        val pass = requireNotNull(game.draw())
        val companion = pass.companionIds().first()
        assertFalse(game.claim(pass, 3, true, companion))
        assertFalse(game.equip(companion))
        fixture.level = 5
        assertTrue(game.claim(pass, 2, true))
        assertTrue(game.claim(pass, 3, true, companion))
        assertFalse(game.claim(pass, 3, true, companion))
        assertTrue(game.equip(companion))
        assertFalse(game.remember(journeyPlaces.first(), "new-trip"))
        fixture.trip = fixture.trip.copy(trips = listOf(TripRecord("new-trip", TripState.COMPLETED, 101, 120,
            DataSource.SIMULATED, 19, 12, completionReason = TripCompletionReason.CONFIRMED_STATIONARY,
            quality = TripDataQuality.COMPLETE, createdAtEpochMillis = 120)))
        assertTrue(game.remember(journeyPlaces.first(), "new-trip"))
        assertFalse(game.remember(journeyPlaces.first(), "new-trip"))
        assertEquals(companion, fixture.create().state.value.memories.single().companion)
    }

    private class Fixture {
        var trip = TripStorageState()
        var parked = false
        var writable = true
        var level = 1
        var saved = JourneyGameState()
        private val season = Season(JOURNEY_SEASON_ID, "여행", 1, 1000,
            listOf(0, 15, 30, 45, 60), emptyList(), emptyList())
        fun create() = JourneyGame({ trip }, {
            ProgressUiState(active = SeasonView(season, SeasonProgress(season.seasonId, emptySet(), currentLevel = level),
                null, false, emptyList(), emptyList()))
        }, { parked }, { 100 }, saved, { updated -> writable.also { if (it) saved = updated } }, Random(7))

        fun addCard(place: JourneyPlace) {
            val calendar = CalendarAttributes(CalendarSeason.AUTUMN, CalendarTimeOfDay.DAY, "UTC")
            trip = trip.copy(postcards = trip.postcards + Postcard(PostcardKey(place.id), place.name,
                null, false, 20, calendar, RegionEvidenceSource.DEMO))
        }
    }
}
