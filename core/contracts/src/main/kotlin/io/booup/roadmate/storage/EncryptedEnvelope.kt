package io.booup.roadmate.storage

import io.booup.roadmate.model.RuntimeMode
import io.booup.roadmate.model.StorageError
import java.io.*
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Key material stays behind the provider. Creation is never used when opening ciphertext. */
interface StoreKeyProvider {
    fun existingKey(): SecretKey?
    fun createKey(): SecretKey
}

/** Sanitized, typed failure. No provider message, alias, bytes or user text is propagated. */
class StorageSecurityException(val error: StorageError) : Exception()

interface StorageEncryption {
    fun seal(plaintext: ByteArray): ByteArray
    fun open(envelope: ByteArray): ByteArray
}

/** Envelope v1 is independent of the domain codec version. JCA AES-256-GCM, not custom crypto. */
class AesGcmEnvelope(private val keys: StoreKeyProvider, private val mode: RuntimeMode) : StorageEncryption {
    override fun seal(plaintext: ByteArray): ByteArray = secured {
        verifyStore(plaintext.size <= MAX_PLAINTEXT, StorageError.LIMIT_EXCEEDED)
        val key = keys.existingKey() ?: keys.createKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        // Let the provider generate a fresh randomized IV, including Android Keystore enforcement.
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        verifyStore(iv.size == IV_BYTES)
        val header = ByteArrayOutputStream().also { b -> DataOutputStream(b).use {
            it.write(MAGIC); it.writeInt(1); it.writeByte(1) // algorithm 1 = AES-256-GCM / 128-bit tag
            it.writeByte(if (mode == RuntimeMode.DEMO) 0 else 1)
            it.writeByte(IV_BYTES); it.writeInt(plaintext.size + TAG_BYTES)
        } }.toByteArray()
        cipher.updateAAD(header)
        header + iv + cipher.doFinal(plaintext)
    }

    override fun open(envelope: ByteArray): ByteArray = secured {
        verifyStore(envelope.size in (HEADER_BYTES + IV_BYTES + TAG_BYTES)..MAX_ENVELOPE)
        val input = DataInputStream(ByteArrayInputStream(envelope))
        val magic = ByteArray(MAGIC.size); input.readFully(magic)
        verifyStore(magic.contentEquals(MAGIC))
        verifyStore(input.readInt() == 1, StorageError.INCOMPATIBLE_SCHEMA)
        verifyStore(input.readUnsignedByte() == 1, StorageError.INCOMPATIBLE_SCHEMA)
        val environment = input.readUnsignedByte()
        verifyStore(environment in 0..1)
        verifyStore(environment == if (mode == RuntimeMode.DEMO) 0 else 1, StorageError.WRONG_ENVIRONMENT)
        verifyStore(input.readUnsignedByte() == IV_BYTES)
        val length = input.readInt()
        verifyStore(length in TAG_BYTES..(MAX_PLAINTEXT + TAG_BYTES) && envelope.size == HEADER_BYTES + IV_BYTES + length)
        val iv = ByteArray(IV_BYTES); input.readFully(iv)
        val ciphertext = ByteArray(length); input.readFully(ciphertext)
        val key = keys.existingKey() ?: throw StorageSecurityException(StorageError.KEY_MISSING)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        cipher.updateAAD(envelope, 0, HEADER_BYTES)
        cipher.doFinal(ciphertext)
    }

    private inline fun <T> secured(block: () -> T): T = try { block() }
    catch (e: StorageSecurityException) { throw e }
    catch (e: StoreException) { throw e }
    catch (_: AEADBadTagException) { throw StorageSecurityException(StorageError.AUTHENTICATION_FAILED) }
    catch (_: InvalidKeyException) { throw StorageSecurityException(StorageError.KEY_INVALIDATED) }
    catch (_: ProviderException) { throw StorageSecurityException(StorageError.ENCRYPTION_UNAVAILABLE) }
    catch (_: GeneralSecurityException) { throw StorageSecurityException(StorageError.ENCRYPTION_UNAVAILABLE) }
    catch (_: IOException) { throw StoreException(StorageError.INVALID_DATA) }

    companion object {
        private val MAGIC = "RDMATEE1".toByteArray(Charsets.US_ASCII)
        const val HEADER_BYTES = 19
        const val IV_BYTES = 12
        const val TAG_BYTES = 16
        const val MAX_PLAINTEXT = 8 * 1024 * 1024
        const val MAX_ENVELOPE = MAX_PLAINTEXT + HEADER_BYTES + IV_BYTES + TAG_BYTES
        fun isEnvelope(bytes: ByteArray): Boolean = bytes.size >= MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
    }
}
