package io.booup.roadmate

import io.booup.roadmate.storage.AesGcmEnvelope
import io.booup.roadmate.storage.StorageEncryption
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** Demo-only sidecar. Never writes plaintext, including staging and legacy migration. */
internal class EncryptedJourneyGameStore(private val directory: File, private val encryption: StorageEncryption) {
    private val file get() = File(directory, "journey.v1.bin")

    fun load(): ByteArray? {
        if (!file.exists()) return null
        if (file.length() > AesGcmEnvelope.MAX_ENVELOPE) throw IOException("Game store exceeds size limit")
        return encryption.open(file.readBytes())
    }

    fun save(plaintext: ByteArray) {
        val envelope = encryption.seal(plaintext)
        Files.createDirectories(directory.toPath())
        val staging = File.createTempFile("journey-", ".staging", directory)
        try {
            FileOutputStream(staging).use { it.write(envelope); it.fd.sync() }
            Files.move(staging.toPath(), file.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
        } finally {
            envelope.fill(0)
            staging.delete()
        }
    }
}
