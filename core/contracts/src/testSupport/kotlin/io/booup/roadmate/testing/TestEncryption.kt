package io.booup.roadmate.testing

import io.booup.roadmate.model.*
import io.booup.roadmate.storage.*
import java.io.File
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** JVM TEST SOURCE SETS ONLY. Real randomized JCA AES-GCM, never a production key provider. */
object TestKeys : StoreKeyProvider {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override fun existingKey() = key
    override fun createKey() = key
}
fun testEncryption(mode: RuntimeMode = RuntimeMode.PRODUCTION): StorageEncryption = AesGcmEnvelope(TestKeys, mode)
fun encryptedTestRepository(directory: File, mode: RuntimeMode,
    retention: CollectionRetentionPolicy = CollectionRetentionPolicy.KEEP_WITHOUT_SOURCE_TRIP): FileTripRepository =
    FileTripRepository(directory, mode, retention, testEncryption(mode))
fun testPlaintext(file: File, mode: RuntimeMode): ByteArray = testEncryption(mode).open(file.readBytes())
