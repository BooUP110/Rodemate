package io.booup.roadmate

import io.booup.roadmate.model.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TripProductionBindingTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun actualProductionBindingHasNoDemoInputAndUsesSeparateDurableDirectory() = runTest {
        val dir = temporary.newFolder(); val clock = ContextClock { ObservationTime(1_000, 1_000) }
        val binding = RuntimeBindings.create(backgroundScope, clock, dir)
        assertNull(binding.developerPanel); assertNull(binding.onLaunch)
        val trips = binding.trips!!; trips.initialize()
        assertTrue(trips.uiState.value.storage.consents.isEmpty()); assertFalse(trips.manual(true))
        trips.consent(ConsentScope.TRIP_HISTORY, true)
        assertTrue(File(dir, "roadmate-production/trips.v1.bin").isFile)
        assertFalse(File(dir, "roadmate-demo").exists())
        val reopened = RuntimeBindings.create(backgroundScope, clock, dir); reopened.trips!!.initialize()
        assertTrue(reopened.trips.uiState.value.storage.consents[ConsentScope.TRIP_HISTORY]!!.granted)
        binding.repository.close(); reopened.repository.close()
    }
}
