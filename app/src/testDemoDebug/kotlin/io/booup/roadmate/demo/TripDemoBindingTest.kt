package io.booup.roadmate.demo

import io.booup.roadmate.RuntimeBindings
import io.booup.roadmate.model.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TripDemoBindingTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun actualDemoBindingWritesOnlyDemoDirectoryAndReopensConsent() = runTest {
        val dir = temporary.newFolder(); val clock = ContextClock { ObservationTime(1_000, 1_000) }
        val binding = RuntimeBindings.create(backgroundScope, clock, dir, io.booup.roadmate.testing.testEncryption(RuntimeMode.DEMO))
        val trips = binding.trips!!; trips.initialize(); trips.consent(ConsentScope.TRIP_HISTORY, true)
        assertTrue(File(dir, "roadmate-demo/trips.v1.bin").isFile)
        assertFalse(File(dir, "roadmate-production").exists())
        val reopened = RuntimeBindings.create(backgroundScope, clock, dir, io.booup.roadmate.testing.testEncryption(RuntimeMode.DEMO)); reopened.trips!!.initialize()
        assertTrue(reopened.trips.uiState.value.storage.consents[ConsentScope.TRIP_HISTORY]!!.granted)
        binding.repository.close(); reopened.repository.close()
    }
}
