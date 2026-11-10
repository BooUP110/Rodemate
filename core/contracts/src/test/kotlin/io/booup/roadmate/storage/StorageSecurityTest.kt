package io.booup.roadmate.storage

import io.booup.roadmate.model.*
import io.booup.roadmate.testing.*
import io.booup.roadmate.trip.record
import io.booup.roadmate.trip.memory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class StorageSecurityTest {
    @Test fun providerRuntimeFailurePreservesCiphertextAndReturnsSanitizedUnavailable() = runBlocking {
        val (dir,_) = fixture(); val original=file(dir).readBytes()
        val unavailable=object:javax.crypto.SecretKey {
            override fun getAlgorithm()="AES"
            override fun getFormat()="RAW"
            override fun getEncoded():ByteArray=throw java.security.ProviderException("fixed provider failure detail")
        }
        val keys=object:StoreKeyProvider {
            override fun existingKey()=unavailable
            override fun createKey():javax.crypto.SecretKey=error("decrypt must not replace key")
        }
        val r=FileTripRepository(dir,RuntimeMode.DEMO,encryption=AesGcmEnvelope(keys,RuntimeMode.DEMO))
        assertEquals(StorageResult.Failure(StorageError.ENCRYPTION_UNAVAILABLE),r.load())
        assertFalse(r.state.value.loaded); assertTrue(r.state.value.memories.isEmpty())
        assertArrayEquals(original,file(dir).readBytes())
    }
    @get:Rule val temporary = TemporaryFolder()
    private fun file(dir: File) = File(dir, "trips.v1.bin")
    private fun legacy() = TripStorageState(loaded = true, trips = listOf(record()),
        consents = listOf(ConsentScope.TRIP_HISTORY, ConsentScope.EMOTIONAL_MEMORY).associateWith {
            ConsentRecord(it, true, 1, null, DataSource.SIMULATED, "fixture")
        }, memories = listOf(memory()))
    private suspend fun fixture(): Pair<File, FileTripRepository> {
        val dir = temporary.newFolder()
        file(dir).writeBytes(plaintextSchemaThree())
        val r = encryptedTestRepository(dir, RuntimeMode.DEMO)
        assertEquals(StorageResult.Success(Unit), r.load())
        return dir to r
    }
    private fun plaintextSchemaThree():ByteArray {
        val bytes=TripStoreCodec(RuntimeMode.DEMO).encode(legacy())
        val input=java.io.DataInputStream(bytes.inputStream()); input.readUTF()
        val offset=bytes.size-input.available()
        java.nio.ByteBuffer.wrap(bytes).putInt(offset,3)
        // schema4 appends a nullable preference flag; this fixture deliberately represents schema3.
        return bytes.copyOf(bytes.size-1)
    }
    @Test fun SEC01_plaintextSchema3MigratesWithoutLosingValidatedState() = runBlocking {
        val (dir, r) = fixture()
        assertEquals(legacy(), r.state.value)
        assertTrue(AesGcmEnvelope.isEnvelope(file(dir).readBytes()))
        assertFalse(File(dir,"trips.pending").exists())
    }
    @Test fun SEC02_encryptedReopenUsesExistingKeyAndPreservesAllData() = runBlocking {
        val (dir, r) = fixture(); val reopened = encryptedTestRepository(dir, RuntimeMode.DEMO)
        assertEquals(StorageResult.Success(Unit), reopened.load()); assertEquals(r.state.value, reopened.state.value)
    }
    @Test fun SEC03_tamperedCiphertextIsRejectedWithoutOverwriteOrCachedDisclosure() = runBlocking {
        val (dir, r) = fixture(); val bytes = file(dir).readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); file(dir).writeBytes(bytes)
        assertEquals(StorageResult.Failure(StorageError.AUTHENTICATION_FAILED), r.load())
        assertFalse(r.state.value.loaded); assertTrue(r.state.value.memories.isEmpty())
        assertArrayEquals(bytes,file(dir).readBytes())
    }
    @Test fun SEC04_alteredNonceFailsAuthentication() = runBlocking {
        val (dir, _) = fixture(); val bytes = file(dir).readBytes()
        bytes[AesGcmEnvelope.HEADER_BYTES] = (bytes[AesGcmEnvelope.HEADER_BYTES].toInt() xor 1).toByte()
        file(dir).writeBytes(bytes)
        assertEquals(StorageResult.Failure(StorageError.AUTHENTICATION_FAILED),encryptedTestRepository(dir,RuntimeMode.DEMO).load())
    }
    @Test fun SEC05_interruptedStagingPreservesPreviousAuthenticatedFile() = runBlocking {
        val (dir, _) = fixture(); val before = file(dir).readBytes()
        val r=FileTripRepository(dir,RuntimeMode.DEMO,encryption=testEncryption(RuntimeMode.DEMO),
            beforeAtomicReplace={
                assertTrue(AesGcmEnvelope.isEnvelope(File(dir,"trips.pending").readBytes()))
                throw IOException()
            })
        assertEquals(StorageResult.Failure(StorageError.IO),r.clearHistory())
        assertArrayEquals(before,file(dir).readBytes()); assertFalse(File(dir,"trips.pending").exists())
        val reopened=encryptedTestRepository(dir,RuntimeMode.DEMO)
        assertEquals(StorageResult.Success(Unit),reopened.load()); assertEquals(legacy(),reopened.state.value)
    }
    @Test fun SEC06_missingOrInvalidatedKeyNeverCreatesReplacementOrResetsFile() = runBlocking {
        val (dir, _) = fixture(); val before=file(dir).readBytes(); var creates=0
        val missing=object:StoreKeyProvider {
            override fun existingKey():javax.crypto.SecretKey?=null
            override fun createKey():javax.crypto.SecretKey { creates++; error("must not create") }
        }
        val r=FileTripRepository(dir,RuntimeMode.DEMO,encryption=AesGcmEnvelope(missing,RuntimeMode.DEMO))
        assertEquals(StorageResult.Failure(StorageError.KEY_MISSING),r.load()); assertEquals(0,creates)
        val invalid=object:StoreKeyProvider {
            override fun existingKey():javax.crypto.SecretKey?=throw StorageSecurityException(StorageError.KEY_INVALIDATED)
            override fun createKey():javax.crypto.SecretKey=error("must not create")
        }
        assertEquals(StorageResult.Failure(StorageError.KEY_INVALIDATED),FileTripRepository(dir,RuntimeMode.DEMO,
            encryption=AesGcmEnvelope(invalid,RuntimeMode.DEMO)).load())
        assertArrayEquals(before,file(dir).readBytes())
    }
    @Test fun SEC07_historyConsentWithdrawalDeletesTripMemoryAndReopensEncrypted() = runBlocking {
        val (dir,r)=fixture()
        assertEquals(StorageResult.Success(Unit),r.setConsent(ConsentScope.TRIP_HISTORY,false,500,DataSource.SIMULATED))
        val reopened=encryptedTestRepository(dir,RuntimeMode.DEMO); reopened.load()
        assertTrue(reopened.state.value.trips.isEmpty()); assertTrue(reopened.state.value.memories.isEmpty())
        assertTrue(AesGcmEnvelope.isEnvelope(file(dir).readBytes()))
    }
    @Test fun SEC08_fullHistoryDeletionPreservesConsentAndInvalidatesCapture() = runBlocking {
        val (dir,r)=fixture(); assertEquals(StorageResult.Success(Unit),r.clearHistory())
        val reopened=encryptedTestRepository(dir,RuntimeMode.DEMO); reopened.load()
        assertTrue(reopened.state.value.trips.isEmpty()); assertEquals(1L,reopened.state.value.captureGeneration)
        assertTrue(reopened.state.value.consents[ConsentScope.TRIP_HISTORY]!!.granted)
    }
    @Test fun SEC09_emotionalDeletionOnlyPreservesTripFacts() = runBlocking {
        val (dir,r)=fixture(); r.setConsent(ConsentScope.EMOTIONAL_MEMORY,false,500,DataSource.SIMULATED)
        val reopened=encryptedTestRepository(dir,RuntimeMode.DEMO); reopened.load()
        assertTrue(reopened.state.value.memories.isEmpty()); assertEquals(legacy().trips,reopened.state.value.trips)
    }
    @Test fun SEC10_persistedBytesContainNoPlaintextAndEveryEncryptionUsesDifferentNonce() = runBlocking {
        val (dir,r)=fixture(); val first=file(dir).readBytes()
        assertFalse(first.toString(Charsets.ISO_8859_1).contains(memory().text))
        r.setConsent(ConsentScope.FUNCTIONAL_PREFERENCE,true,500,DataSource.SIMULATED)
        val second=file(dir).readBytes()
        assertFalse(first.copyOfRange(19,31).contentEquals(second.copyOfRange(19,31)))
    }
    @Test fun SEC11_legacySchemaOneAndTwoMigrateIntoAuthenticatedEnvelope() = runBlocking {
        for(version in listOf(1,2)) {
            val dir=temporary.newFolder(); file(dir).writeBytes(javaClass.getResourceAsStream("/schema$version-golden.bin")!!.use { it.readBytes() })
            val r=encryptedTestRepository(dir,RuntimeMode.DEMO); assertEquals(StorageResult.Success(Unit),r.load())
            assertTrue(AesGcmEnvelope.isEnvelope(file(dir).readBytes())); assertEquals("legacy-trip",r.state.value.trips.single().tripId)
            assertTrue(r.state.value.memories.isNotEmpty())
        }
    }
    @Test fun SEC12_newRepositoryReopensAfterRuntimeObjectDisposal() = runBlocking {
        val dir=fixture().first
        val next=encryptedTestRepository(dir,RuntimeMode.DEMO); assertEquals(StorageResult.Success(Unit),next.load())
        assertEquals(legacy(),next.state.value)
        // Actual process/Android Keystore restart is separately exercised on AVD.
    }
    @Test fun migrationWriteFailureKeepsOriginalLegacyBytesAndBlocksPublication() = runBlocking {
        val dir=temporary.newFolder(); val original=plaintextSchemaThree(); file(dir).writeBytes(original)
        val r=FileTripRepository(dir,RuntimeMode.DEMO,encryption=testEncryption(RuntimeMode.DEMO),beforeAtomicReplace={throw IOException()})
        assertEquals(StorageResult.Failure(StorageError.IO),r.load()); assertArrayEquals(original,file(dir).readBytes())
        assertFalse(r.state.value.loaded); assertTrue(r.state.value.memories.isEmpty())
        assertEquals(StorageResult.Success(Unit),encryptedTestRepository(dir,RuntimeMode.DEMO).load())
    }
    @Test fun wrongKeyIsRejectedAndDoesNotOverwriteValidCiphertext()=runBlocking {
        val (dir,_)=fixture(); val before=file(dir).readBytes()
        val wrong=javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val provider=object:StoreKeyProvider {
            override fun existingKey()=wrong
            override fun createKey()=error("opening must not create a key")
        }
        val r=FileTripRepository(dir,RuntimeMode.DEMO,encryption=AesGcmEnvelope(provider,RuntimeMode.DEMO))
        assertEquals(StorageResult.Failure(StorageError.AUTHENTICATION_FAILED),r.load())
        assertArrayEquals(before,file(dir).readBytes())
    }
}
