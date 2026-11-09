package io.booup.roadmate

import io.booup.roadmate.model.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProgressProductionBindingTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun actualProductionConfigHasNoPremiumEntitlementOrDemoResetRoute()=runTest {
        val binding=RuntimeBindings.create(backgroundScope,ContextClock { ObservationTime(1_793_923_200_000,100_000) },temporary.newFolder())
        binding.trips!!.initialize(); val p=binding.progress!!; p.initialize(); p.refresh()
        assertFalse(p.uiState.value.contentAllowed); assertTrue(p.repository.state.value.progression.premiumSeasons.isEmpty())
        assertTrue(p.repository.state.value.progression.seasons.none { it.seasonId.contains("demo") })
        val season=p.repository.state.value.progression.seasons.single()
        assertEquals(StorageResult.Failure(StorageError.WRONG_ENVIRONMENT),p.repository.setSimulatedPremium(season.seasonId,true))
        assertFalse(p.claim(season.seasonId,season.rewards.first().rewardId)); assertNull(binding.onLaunch); assertNull(binding.developerPanel)
        binding.repository.close()
    }
}
