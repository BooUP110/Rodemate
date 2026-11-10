package io.booup.roadmate

import io.booup.roadmate.model.RuntimeMode
import io.booup.roadmate.model.StorageError
import io.booup.roadmate.storage.*
import io.booup.roadmate.testing.testEncryption
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedJourneyGameStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun gameReopensEncryptedAndNeverLeavesPlaintextStaging() {
        val directory = temporary.newFolder()
        val encryption = testEncryption(RuntimeMode.DEMO)
        val payload = "private-trip-id and companion journey".toByteArray()
        EncryptedJourneyGameStore(directory, encryption).save(payload)
        val bytes = File(directory, "journey.v1.bin").readBytes()
        assertTrue(AesGcmEnvelope.isEnvelope(bytes))
        assertFalse(bytes.toString(Charsets.UTF_8).contains("private-trip-id"))
        assertArrayEquals(payload, EncryptedJourneyGameStore(directory, encryption).load())
        assertEquals(listOf("journey.v1.bin"), directory.listFiles()!!.map { it.name })
    }

    @Test fun failedEncryptionPreservesExistingGameAndTamperingIsRejected() {
        val directory = temporary.newFolder()
        val encryption = testEncryption(RuntimeMode.DEMO)
        EncryptedJourneyGameStore(directory, encryption).save("old-state".toByteArray())
        val file = File(directory, "journey.v1.bin")
        val original = file.readBytes()
        val unavailable = object : StorageEncryption {
            override fun seal(plaintext: ByteArray): ByteArray = throw StorageSecurityException(StorageError.KEY_INVALIDATED)
            override fun open(envelope: ByteArray) = encryption.open(envelope)
        }
        try {
            EncryptedJourneyGameStore(directory, unavailable).save("new-state".toByteArray())
            fail("Expected encryption failure")
        } catch (error: StorageSecurityException) { assertEquals(StorageError.KEY_INVALIDATED, error.error) }
        assertArrayEquals(original, file.readBytes())
        val tampered = original.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        file.writeBytes(tampered)
        try {
            EncryptedJourneyGameStore(directory, encryption).load()
            fail("Expected authentication failure")
        } catch (error: StorageSecurityException) { assertEquals(StorageError.AUTHENTICATION_FAILED, error.error) }
        assertArrayEquals(tampered, file.readBytes())
    }
}
