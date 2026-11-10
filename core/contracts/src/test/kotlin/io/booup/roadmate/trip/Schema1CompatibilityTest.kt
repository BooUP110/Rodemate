package io.booup.roadmate.trip

import io.booup.roadmate.testing.encryptedTestRepository
import io.booup.roadmate.model.*
import io.booup.roadmate.storage.FileTripRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.DataInputStream

class Schema1CompatibilityTest {
    @get:Rule val temporary = TemporaryFolder()
    @Test fun goldenSchemaOneReopensWithoutLosingTripsConsentActiveOrMemory() = runBlocking {
        val dir = temporary.newFolder()
        val bytes = javaClass.getResourceAsStream("/schema1-golden.bin")!!.use { it.readBytes() }
        File(dir, "trips.v1.bin").writeBytes(bytes)
        val r = encryptedTestRepository(dir, RuntimeMode.DEMO); success(r.load())
        val s = r.state.value
        assertEquals("legacy-trip", s.trips.single().tripId)
        assertEquals("legacy-active", s.active!!.tripId)
        assertTrue(s.consents[ConsentScope.TRIP_HISTORY]!!.granted)
        assertTrue(s.consents[ConsentScope.EMOTIONAL_MEMORY]!!.granted)
        assertEquals("Original confirmed memory", s.memories.single().text)
        assertTrue(s.postDrive.isEmpty()); assertTrue(s.visits.isEmpty()); assertTrue(s.postcards.isEmpty())
        DataInputStream(io.booup.roadmate.testing.testPlaintext(File(dir,"trips.v1.bin"),RuntimeMode.DEMO).inputStream()).use { it.readUTF(); assertEquals(4,it.readInt()) }
        val reopened=encryptedTestRepository(dir,RuntimeMode.DEMO); success(reopened.load()); assertEquals(s,reopened.state.value)
    }
}
