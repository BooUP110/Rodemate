package io.booup.roadmate.storage

import io.booup.roadmate.contracts.PetTravelRepository
import io.booup.roadmate.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import java.nio.file.StandardOpenOption.*
import java.util.Collections

/** Encrypted owner-consented pet data only. No plaintext migration/fallback and no shared Trip writes. */
class FilePetTravelRepository(private val directory:File, mode:RuntimeMode,
    private val encryption:StorageEncryption, private val beforeReplace:()->Unit = {}) : PetTravelRepository {
    private val codec=PetTravelCodec(mode)
    private val mutex=Mutex()
    private val mutable=MutableStateFlow(PetTravelStorage())
    override val state=mutable.asStateFlow()
    private val file get()=File(directory,"pet-travel.v1.bin")
    override suspend fun load()=transaction(false) { it }
    override suspend fun update(transform:(PetTravelData)->PetTravelData)=transaction(true,transform)
    private suspend fun transaction(write:Boolean,transform:(PetTravelData)->PetTravelData):StorageResult<Unit> = mutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                Files.createDirectories(directory.toPath())
                FileChannel.open(File(directory,"pet.lock").toPath(),CREATE,WRITE).use { channel -> channel.lock().use {
                    val current=if(!file.exists()) PetTravelData() else decode(file)
                    val pending=File(directory,"pet.pending")
                    if(pending.isFile) Files.delete(pending.toPath())
                    val next=freeze(transform(current)); codec.validate(next)
                    if(write && next!=current) {
                        val bytes=codec.encode(next)
                        val encrypted=try { encryption.seal(bytes) } finally { bytes.fill(0) }
                        try {
                            FileOutputStream(pending).use { it.write(encrypted); it.fd.sync() }
                            verifyStore(decode(pending)==next)
                            beforeReplace()
                            Files.move(pending.toPath(),file.toPath(),ATOMIC_MOVE,REPLACE_EXISTING)
                        } finally { encrypted.fill(0); if(pending.isFile) Files.deleteIfExists(pending.toPath()) }
                    }
                    mutable.value=PetTravelStorage(true,next)
                } }
                StorageResult.Success(Unit)
            } catch(e:StorageSecurityException) { failed(e.error) }
              catch(e:StoreException) { failed(e.code) }
              catch(_:IOException) { failed(StorageError.IO) }
              catch(_:OverlappingFileLockException) { failed(StorageError.IO) }
        }
    }
    private fun decode(input:File):PetTravelData {
        val encrypted=FileInputStream(input).use { stream ->
            val bytes=ByteArrayOutputStream(); val buffer=ByteArray(8192)
            while(true) {
                val count=stream.read(buffer); if(count<0) break
                verifyStore(bytes.size()+count<=1024*1024,StorageError.LIMIT_EXCEEDED); bytes.write(buffer,0,count)
            }
            bytes.toByteArray()
        }
        val bytes=encryption.open(encrypted)
        return try { freeze(codec.decode(bytes)) } finally { bytes.fill(0) }
    }
    private fun failed(error:StorageError):StorageResult.Failure {
        mutable.value=PetTravelStorage(error=error); return StorageResult.Failure(error)
    }
    private fun freeze(data:PetTravelData)=data.copy(
        packing=Collections.unmodifiableSet(HashSet(data.packing)),
        plan=data.plan?.let { it.copy(stopChecks=Collections.unmodifiableSet(HashSet(it.stopChecks))) },
        cards=Collections.unmodifiableList(ArrayList(data.cards)),memos=Collections.unmodifiableList(ArrayList(data.memos)),
        places=Collections.unmodifiableList(ArrayList(data.places)),claimed=Collections.unmodifiableSet(HashSet(data.claimed)),
    )
}
