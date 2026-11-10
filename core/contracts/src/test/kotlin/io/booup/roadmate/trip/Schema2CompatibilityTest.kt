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

class Schema2CompatibilityTest {
    @get:Rule val temporary=TemporaryFolder()
    @Test fun goldenSchemaTwoPreservesTripConsentReflectionCollectionAndActive()=runBlocking {
        val dir=temporary.newFolder()
        File(dir,"trips.v1.bin").writeBytes(javaClass.getResourceAsStream("/schema2-golden.bin")!!.use { it.readBytes() })
        val r=encryptedTestRepository(dir,RuntimeMode.DEMO); success(r.load())
        assertEquals("legacy-trip",r.state.value.trips.single().tripId)
        assertEquals("legacy-active",r.state.value.active!!.tripId)
        assertEquals("Schema two local reflection",r.state.value.postDrive["legacy-trip"]!!.reflection!!.text)
        assertEquals("golden_region",r.state.value.postcards.single().key.regionId.value)
        assertEquals(1,r.state.value.visits.size); assertEquals(1,r.state.value.memories.size)
    }
}
