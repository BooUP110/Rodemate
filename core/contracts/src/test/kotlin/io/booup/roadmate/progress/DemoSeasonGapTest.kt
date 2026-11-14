package io.booup.roadmate.progress

import io.booup.roadmate.model.*
import io.booup.roadmate.testing.encryptedTestRepository
import io.booup.roadmate.trip.record
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DemoSeasonGapTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun expiredDemoSeasonKeepsClaimsXpAndTripsWhenMissingCurrentSeasonIsAdded() = runBlocking {
        val directory = temporary.newFolder()
        val repository = encryptedTestRepository(directory, RuntimeMode.DEMO)
        assertEquals(StorageResult.Success(Unit), repository.load())
        assertEquals(StorageResult.Success(Unit), repository.setConsent(ConsentScope.TRIP_HISTORY, true, 1, DataSource.SIMULATED))
        assertEquals(StorageResult.Success(Unit), repository.save(record(), 0))
        val old = season("old", end = 2000)
        assertEquals(StorageResult.Success(Unit), repository.installSeasons(listOf(old)))
        assertEquals(StorageResult.Success(Unit), repository.reconcileProgress(now(), 0, 0))
        assertEquals(StorageResult.Success(Unit), repository.claimReward("old", "free", now(), 0))
        val before = repository.state.value
        val current = season("current", end = 5000)
        val coordinator = ProgressCoordinator(repository, RuntimeMode.DEMO, ContextClock { now(3000) }, { true },
            listOf(current), seedOnlyWhenEmpty = true, fillDemoSeasonGap = true)

        coordinator.initialize()
        coordinator.refresh()
        val after = repository.state.value
        assertEquals(before.copy(progression = before.progression.copy(seasons = listOf(old,
            current.copy(startsAtEpochMillis = 2000)))), after)
        assertEquals("current", coordinator.uiState.value.active?.season?.seasonId)
        assertEquals(0L, coordinator.uiState.value.active?.progress?.totalXp)
        assertEquals(1, coordinator.uiState.value.archived.single().progress.claimedRewards.size)

        val reopened = encryptedTestRepository(directory, RuntimeMode.DEMO)
        assertEquals(StorageResult.Success(Unit), reopened.load())
        val second = ProgressCoordinator(reopened, RuntimeMode.DEMO, ContextClock { now(3000) }, { true },
            listOf(current), seedOnlyWhenEmpty = true, fillDemoSeasonGap = true)
        second.initialize(); second.refresh()
        assertEquals(after, reopened.state.value)
    }

    @Test fun activeSavedSeasonIsNotReplacedAndGapStopsAtFutureSeason() = runBlocking {
        val repository = encryptedTestRepository(temporary.newFolder(), RuntimeMode.DEMO)
        assertEquals(StorageResult.Success(Unit), repository.load())
        val old = season("old", end = 2000)
        val future = season("future", start = 4000, end = 6000)
        assertEquals(StorageResult.Success(Unit), repository.installSeasons(listOf(old, future)))
        var time = 1000L
        val coordinator = ProgressCoordinator(repository, RuntimeMode.DEMO, ContextClock { now(time) }, { true },
            listOf(season("current", end = 5000)), seedOnlyWhenEmpty = true, fillDemoSeasonGap = true)
        val before = repository.state.value
        coordinator.initialize()
        assertEquals(before, repository.state.value)
        assertEquals("old", coordinator.uiState.value.active?.season?.seasonId)
        time = 3000
        coordinator.refresh()
        assertEquals(listOf(old, season("current", start = 2000, end = 4000), future),
            repository.state.value.progression.seasons)
        time = 4000
        coordinator.refresh()
        assertEquals("future", coordinator.uiState.value.active?.season?.seasonId)
    }

    @Test fun sameIdExpiredSeasonAndOutOfPeriodCatalogAreNeverRewritten() = runBlocking {
        val repository = encryptedTestRepository(temporary.newFolder(), RuntimeMode.DEMO)
        assertEquals(StorageResult.Success(Unit), repository.load())
        assertEquals(StorageResult.Success(Unit), repository.installSeasons(listOf(season("current", end = 2000))))
        val before = repository.state.value
        val coordinator = ProgressCoordinator(repository, RuntimeMode.DEMO, ContextClock { now(3000) }, { true },
            listOf(season("current", end = 5000), season("not-started", start = 4000, end = 5000)),
            seedOnlyWhenEmpty = true, fillDemoSeasonGap = true)
        coordinator.initialize(); coordinator.refresh()
        assertEquals(before, repository.state.value)
        assertNull(coordinator.uiState.value.active)
        assertEquals(1, coordinator.uiState.value.archived.size)
    }

    @Test fun productionCannotOptIntoDemoGapFilling() {
        val repository = encryptedTestRepository(temporary.newFolder(), RuntimeMode.PRODUCTION)
        assertThrows(IllegalArgumentException::class.java) {
            ProgressCoordinator(repository, RuntimeMode.PRODUCTION, ContextClock { now() }, { true }, fillDemoSeasonGap = true)
        }
    }
}
