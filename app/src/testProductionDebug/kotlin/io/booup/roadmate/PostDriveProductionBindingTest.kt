package io.booup.roadmate

import io.booup.roadmate.model.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PostDriveProductionBindingTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun productionHasPostDriveControllerButNoRegionProviderOrConsentBypass()=runTest {
        val binding=RuntimeBindings.create(backgroundScope,ContextClock { ObservationTime(1_000_000,100_000) },temporary.newFolder())
        binding.trips!!.initialize(); val p=binding.postDrive!!; p.refresh()
        assertFalse(p.uiState.value.contentAllowed); assertFalse(p.consent(ConsentScope.TRIP_HISTORY,true))
        assertFalse(p.open("unknown")); assertTrue(binding.trips.uiState.value.storage.consents.isEmpty())
        assertNull(binding.developerPanel); assertNull(binding.onLaunch); binding.repository.close()
    }
}
