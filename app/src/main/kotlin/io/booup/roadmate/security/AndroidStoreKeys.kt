package io.booup.roadmate.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import io.booup.roadmate.model.StorageError
import io.booup.roadmate.storage.StorageSecurityException
import io.booup.roadmate.storage.StoreKeyProvider
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Android Keystore owns the non-exportable key. No key bytes, aliases or exceptions are logged. */
class AndroidStoreKeys(private val alias: String) : StoreKeyProvider {
    override fun existingKey(): SecretKey? = access {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(alias)) null
        else store.getKey(alias, null) as? SecretKey
            ?: throw StorageSecurityException(StorageError.KEY_INVALIDATED)
    }

    override fun createKey(): SecretKey = access {
        // FileTripRepository serializes this check/create under the aggregate's OS file lock.
        existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }

    private inline fun <T> access(block: () -> T): T = try { block() }
    catch (e: StorageSecurityException) { throw e }
    catch (_: KeyPermanentlyInvalidatedException) { throw StorageSecurityException(StorageError.KEY_INVALIDATED) }
    catch (_: Exception) { throw StorageSecurityException(StorageError.ENCRYPTION_UNAVAILABLE) }
}
