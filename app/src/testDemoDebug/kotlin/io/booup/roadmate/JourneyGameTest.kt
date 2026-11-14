package io.booup.roadmate

import io.booup.roadmate.model.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class JourneyGameTest {
    @Test fun companionSlotsMoveOwnedCharactersWithoutChangingTheActiveCompanion() = runBlocking {
        val fixture = Fixture().apply { parked = true }
        val collab = JourneyPass.TRAVEL.companionIds().first()
        val locked = JourneyPass.TRAVEL.companionIds()[1]
        fixture.saved = JourneyGameState(companions = setOf(collab), equipped = collab)
        val game = fixture.create()
        assertFalse(game.setCompanionSlot(0, locked))
        assertFalse(game.setCompanionSlot(3, collab))
        assertTrue(game.setCompanionSlot(2, collab))
        assertTrue(game.setCompanionSlot(0, collab))
        assertEquals(listOf(collab, CompanionProfiles.somsom.id, null), fixture.create().state.value.companionSlots)
        assertTrue(game.setCompanionSlot(0, null))
        assertEquals(collab, fixture.create().state.value.equipped)
        fixture.parked = false
        assertFalse(game.setCompanionSlot(2, collab))
    }

    @Test fun moonlightChaptersRequireOrderedDemoVisitsAndPremiumRewardsRequireEntitlement() = runBlocking {
        val fixture = Fixture().apply { parked = true }
        val game = fixture.create()
        val first = MoonlightChapter.OJUKHEON
        assertFalse(game.completeMoonlightChapter(first, first.answer))
        fixture.trip = fixture.trip.copy(visits = MoonlightChapter.entries.map { chapter ->
            RegionVisitRecord(chapter.place.id, chapter.ordinal.toLong(), null, true, RegionEvidenceSource.DEMO)
        })
        val second = MoonlightChapter.GYEONGPODAE
        assertFalse(game.completeMoonlightChapter(second, second.answer))
        assertFalse(game.completeMoonlightChapter(first, first.answer + 1))
        assertFalse(game.claimMoonlightOutfit(MoonlightOutfit.SCARF))
        assertTrue(game.completeMoonlightChapter(first, first.answer))
        assertTrue(game.completeMoonlightChapter(first, first.answer))
        assertEquals(1, fixture.create().state.value.moonlightCompleted)
        assertTrue(game.claimMoonlightOutfit(MoonlightOutfit.SCARF))
        assertTrue(game.equipMoonlightOutfit(MoonlightOutfit.SCARF))
        assertFalse(game.equipMoonlightOutfit(MoonlightOutfit.HAT))
        assertTrue(game.completeMoonlightChapter(second, second.answer))
        assertFalse(game.claimMoonlightOutfit(MoonlightOutfit.HAT))
        assertTrue(game.grantDemoMoonlightPremium())
        assertTrue(game.claimMoonlightOutfit(MoonlightOutfit.HAT))
        MoonlightChapter.entries.drop(2).forEach { assertTrue(game.completeMoonlightChapter(it, it.answer)) }
        assertTrue(game.claimMoonlightOutfit(MoonlightOutfit.CAPE))
        assertEquals(4, fixture.create().state.value.moonlightCompleted)
        assertEquals(MoonlightOutfit.SCARF.name, fixture.create().state.value.moonlightOutfit)
    }

    @Test fun expeditionOnlyConnectsRevealedPoints() {
        val route = Expedition.SEOUL
        val (first, second, third, last) = route.places
        assertEquals(listOf(first), route.revealed(emptySet()))
        assertEquals(listOf(first, second, third), route.revealed(setOf(first.id, second.id)))
        assertEquals(listOf(third, second, first), route.connection(third.id, first.id, setOf(first.id, second.id)))
        assertTrue(route.connection(first.id, last.id, setOf(first.id)).isEmpty())
        assertEquals(listOf(first, second, last), route.revealed(setOf(first.id, last.id)))
        assertTrue(route.connection(first.id, last.id, setOf(first.id, last.id)).isEmpty())
        assertNull(route.next(route.places.map { it.id }.toSet()))
        assertEquals(setOf(route.region.id), journeyRegionIds(setOf(first.id, second.id, route.region.id)))
        assertEquals(first, verifiedExpeditionPoint(TripLocation(first.name, DataSource.VEHICLE_SDK)))
        assertNull(verifiedExpeditionPoint(TripLocation(first.name, DataSource.USER_INPUT)))
        assertNull(verifiedExpeditionPoint(TripLocation(route.region.name, DataSource.SYSTEM)))
    }

    @Test fun landmarkRequiresRankedCompletionAndFeedbackCountsEachVisitorOnce() = runBlocking {
        val fixture = Fixture().apply { parked = true }
        val game = fixture.create()
        val route = Expedition.GANGNEUNG
        val point = route.places.first()
        assertFalse(game.createLandmark(point.id, "파도의 정원"))
        val completed = route.places.mapIndexed { index, place ->
            RegionVisitRecord(place.id, 1_000L + 2_000 * index, null, true, RegionEvidenceSource.DEMO)
        }
        fixture.trip = fixture.trip.copy(visits = completed.map { it.copy(source = RegionEvidenceSource.VERIFIED_CONTEXT) })
        assertFalse(game.createLandmark(point.id, "파도의 정원"))
        fixture.trip = fixture.trip.copy(visits = completed.dropLast(1) + completed.last().copy(firstVisitedAtEpochMillis = 6_000_000))
        assertEquals(4, route.demoRank(fixture.trip.visits))
        assertFalse(game.createLandmark(point.id, "파도의 정원"))
        fixture.trip = fixture.trip.copy(visits = completed)
        assertFalse(game.createLandmark(point.id, "  "))
        assertEquals(1, listOf(async { game.createLandmark(point.id, " 파도의 정원 ") },
            async { game.createLandmark(route.places.last().id, "두 번째 랜드마크") }).awaitAll().count { it })
        val landmark = game.state.value.landmarks.single()
        assertFalse(game.demoLandmarkFeedback(landmark.pointId, 1, like = true))
        assertTrue(game.demoLandmarkFeedback(landmark.pointId, 1, like = false))
        assertFalse(game.demoLandmarkFeedback(landmark.pointId, 1, like = false))
        assertTrue(game.demoLandmarkFeedback(landmark.pointId, 1, like = true))
        assertTrue(game.demoLandmarkFeedback(landmark.pointId, 2, like = false))
        assertEquals(10, fixture.create().state.value.landmarks.single().points)
        assertEquals(setOf(1), game.state.value.landmarks.single().likes)
    }

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
        fixture.xp = 60
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

    @Test fun previewUpgradeOpensReachedPaidRewardsWithoutSpendingDrawOrChoice() = runBlocking {
        val fixture = Fixture()
        val game = fixture.create()
        val pass = JourneyPass.TRAVEL
        val line = setOf(JourneyStamp.DISCOVERY, JourneyStamp.OPEN, JourneyStamp.STAR)
        assertFalse(game.previewUpgrade(pass))
        fixture.parked = true
        assertFalse(game.claim(pass, 1, premium = true))
        assertTrue(game.previewUpgrade(pass))
        assertFalse(game.previewUpgrade(pass))
        assertEquals(1, game.tickets(game.state.value))
        assertTrue(game.canChoose(game.state.value, line))
        assertFalse(game.claim(pass, 2, premium = true))
        assertFalse(game.claim(JourneyPass.COAST, 1, premium = true))
        assertTrue(game.claim(pass, 1, premium = true))
        assertTrue(game.claim(pass, 1, premium = false))
        assertTrue(fixture.create().state.value.grants.single().upgradePreview)
    }

    private class Fixture {
        var trip = TripStorageState(loaded = true, consents = mapOf(ConsentScope.TRIP_HISTORY to
            ConsentRecord(ConsentScope.TRIP_HISTORY, true, 1, null, DataSource.SIMULATED, "test")))
        var parked = false
        var writable = true
        var xp = 20L
        var saved = JourneyGameState()
        private val season = Season(JOURNEY_SEASON_ID, "여행", 1, 1000,
            listOf(0, 15, 30, 45, 60), emptyList(), emptyList())
        fun create(starters: Set<CompanionId> = emptySet()) = JourneyGame({ trip }, {
            ProgressUiState(active = SeasonView(season, SeasonProgress(season.seasonId, emptySet(),
                totalXp = xp, currentLevel = season.levels.count { it <= xp }),
                null, false, emptyList(), emptyList()))
        }, { parked }, { 100 }, saved, { updated -> writable.also { if (it) saved = updated } }, Random(7),
            starterCompanions = starters)

        fun addCard(place: JourneyPlace) {
            val calendar = CalendarAttributes(CalendarSeason.AUTUMN, CalendarTimeOfDay.DAY, "UTC")
            trip = trip.copy(postcards = trip.postcards + Postcard(PostcardKey(place.id), place.name,
                null, false, 20, calendar, RegionEvidenceSource.DEMO))
        }
    }

    @Test fun paidAndFreeRewardsHaveSeparateMilestones() {
        assertEquals((5L..60L step 5).toList(), journeyPassRewards.map { it.xp })
        assertEquals(listOf(20L, 40L, 60L), journeyPassRewards.filterNot { it.premium }.map { it.xp })
        assertEquals(9, journeyPassRewards.count { it.premium })
    }

    @Test fun deletedTripsAndRevokedConsentRemoveJourneyMemoriesWithoutLosingPasses() = runBlocking {
        val fixture = Fixture().apply { parked = true }
        val memory = JourneyMemory("deleted-trip", "region", CompanionProfiles.dodam.id)
        fixture.saved = JourneyGameState(grants = listOf(PassGrant(JourneyPass.TRAVEL, true)), memories = listOf(memory))
        val game = fixture.create()
        assertTrue(game.visibleMemories().isEmpty())
        assertTrue(game.reconcileStorage())
        assertTrue(game.state.value.memories.isEmpty())
        assertEquals(1, game.state.value.grants.size)
        fixture.trip = fixture.trip.copy(trips = listOf(TripRecord("deleted-trip", TripState.COMPLETED, 101, 120,
            DataSource.SIMULATED, 19, 12, completionReason = TripCompletionReason.CONFIRMED_STATIONARY,
            quality = TripDataQuality.COMPLETE, createdAtEpochMillis = 120)), consents = emptyMap())
        fixture.saved = fixture.saved.copy(memories = listOf(memory))
        val reopened = fixture.create()
        assertTrue(reopened.reconcileStorage())
        assertTrue(reopened.state.value.memories.isEmpty())
        fixture.trip = fixture.trip.copy(progression = fixture.trip.progression.copy(resetAtEpochMillis = 200))
        assertTrue(reopened.reconcileStorage())
        assertTrue(reopened.state.value.grants.isEmpty())
    }

    @Test fun demoStarterCompanionsStayEquippableAcrossAProgressReset() = runBlocking {
        val fixture = Fixture().apply { parked = true }
        val starters = JourneyPass.TRAVEL.companionIds().toSet()
        val game = fixture.create(starters)
        assertEquals(starters, game.state.value.companions)
        assertTrue(game.equip(starters.first()))
        assertTrue(game.reconcileStorage())
        fixture.trip = fixture.trip.copy(progression = fixture.trip.progression.copy(resetAtEpochMillis = 200))
        assertTrue(game.reconcileStorage())
        assertEquals(starters, game.state.value.companions)
        assertNull(game.state.value.equipped)
        assertTrue(game.equip(starters.last()))
    }

    @Test fun legacyGameAdoptsExistingResetMarkerWithoutDeletingEarnedPasses() = runBlocking {
        val fixture = Fixture().apply { parked = true }
        fixture.trip = fixture.trip.copy(progression = fixture.trip.progression.copy(resetAtEpochMillis = 200))
        fixture.saved = JourneyGameState(grants = listOf(PassGrant(JourneyPass.TRAVEL, true)))
        val game = fixture.create()
        assertTrue(game.reconcileStorage())
        assertEquals(200L, game.state.value.progressResetAt)
        assertEquals(listOf(PassGrant(JourneyPass.TRAVEL, true)), game.state.value.grants)
    }
}
