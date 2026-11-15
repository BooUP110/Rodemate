package io.booup.roadmate.storage

import io.booup.roadmate.model.*
import java.io.*

/** Independent sidecar schema; never changes the existing Trip/XP/collection aggregate. */
internal class PetTravelCodec(private val mode: RuntimeMode) {
    fun encode(data: PetTravelData): ByteArray {
        validate(data)
        return ByteArrayOutputStream().also { bytes -> DataOutputStream(bytes).use { out ->
            out.writeUTF("Roadmate-pet"); out.writeInt(2); out.writeUTF(mode.name)
            out.writeBoolean(data.consentGranted); out.writeBoolean(data.enabled)
            out.writeBoolean(data.profile != null)
            data.profile?.let { out.writeUTF(it.name); out.writeInt(it.kind.ordinal) }
            out.writeInt(data.purpose.ordinal); out.enums(data.packing)
            out.writeBoolean(data.plan != null)
            data.plan?.let { p ->
                out.writeUTF(p.id); out.writeInt(p.purpose.ordinal); out.writeLong(p.createdAtEpochMillis)
                out.writeLong(p.captureGeneration); out.nullable(p.tripId); out.enums(p.stopChecks)
            }
            out.writeInt(data.cards.size)
            data.cards.forEach { out.writeUTF(it.tripId); out.writeUTF(it.petName); out.writeInt(it.purpose.ordinal)
                out.writeUTF(it.ownerNote); out.writeLong(it.savedAtEpochMillis) }
            out.writeInt(data.memos.size)
            data.memos.forEach { out.writeUTF(it.id); out.writeUTF(it.text); out.nullable(it.sourceTripId); out.writeBoolean(it.checked) }
            out.writeInt(data.places.size)
            data.places.forEach { out.writeUTF(it.id); out.writeUTF(it.title); out.writeUTF(it.note); out.writeBoolean(it.returnWanted) }
            out.enums(data.claimed); out.writeInt(data.frame.ordinal)
            out.writeInt(data.restIntervalMinutes ?: 0)
            out.writeBoolean(data.restSession != null)
            data.restSession?.let { s ->
                out.writeUTF(s.id); out.writeLong(s.startedAtEpochMillis); out.writeInt(s.intervalMinutes)
                out.writeLong(s.elapsedTotalMillis); out.writeLong(s.elapsedSinceRestMillis); out.writeInt(s.confirmedRests)
                out.writeBoolean(s.paused); out.writeBoolean(s.resting); out.enums(s.checks)
            }
        } }.toByteArray()
    }
    fun decode(bytes: ByteArray): PetTravelData = try {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            verifyStore(input.readUTF() == "Roadmate-pet")
            val schema=input.readInt()
            verifyStore(schema in 1..2, StorageError.INCOMPATIBLE_SCHEMA)
            verifyStore(input.readUTF() == mode.name, StorageError.WRONG_ENVIRONMENT)
            val consent=input.readBoolean(); val enabled=input.readBoolean()
            val profile=if(input.readBoolean()) PetProfile(input.readUTF(),input.enum<PetKind>()) else null
            val purpose=input.enum<PetTravelPurpose>(); val packing=input.enums<PetPackingItem>()
            val plan=if(input.readBoolean()) PetTravelPlan(input.readUTF(),input.enum<PetTravelPurpose>(),
                input.readLong(),input.readLong(),input.nullable(),input.enums()) else null
            val cards=List(input.count(200)) { PetTravelCard(input.readUTF(),input.readUTF(),input.enum(),input.readUTF(),input.readLong()) }
            val memos=List(input.count(100)) { PetDepartureMemo(input.readUTF(),input.readUTF(),input.nullable(),input.readBoolean()) }
            val places=List(input.count(100)) { PetPlaceNote(input.readUTF(),input.readUTF(),input.readUTF(),input.readBoolean()) }
            val base=PetTravelData(consent,enabled,profile,purpose,packing,plan,cards,memos,places,input.enums(),input.enum())
            val result=if(schema==1) base else {
                val interval=input.readInt().let { if(it==0) null else it }
                val rest=if(input.readBoolean()) PetRestSession(input.readUTF(),input.readLong(),input.readInt(),
                    input.readLong(),input.readLong(),input.readInt(),input.readBoolean(),input.readBoolean(),input.enums()) else null
                base.copy(restIntervalMinutes=interval,restSession=rest)
            }
            verifyStore(input.available()==0); validate(result); result
        }
    } catch (e: StoreException) { throw e }
      catch (_: IOException) { throw StoreException(StorageError.INVALID_DATA) }

    fun validate(data: PetTravelData) {
        fun text(value:String,max:Int,empty:Boolean=false) {
            verifyStore(value.length<=max && (empty || value.isNotBlank()) && value.none { it.isISOControl() && it!='\n' })
        }
        if(!data.consentGranted) { verifyStore(data==PetTravelData()); return }
        val profile=data.profile ?: throw StoreException(StorageError.INVALID_DATA)
        text(profile.name,24); verifyStore(profile.name==profile.name.trim() && !profile.name.contains('\n'))
        verifyStore(data.cards.size<=200 && data.memos.size<=100 && data.places.size<=100,StorageError.LIMIT_EXCEEDED)
        verifyStore(data.cards.map { it.tripId }.distinct().size==data.cards.size)
        verifyStore(data.memos.map { it.id }.distinct().size==data.memos.size)
        verifyStore(data.places.map { it.id }.distinct().size==data.places.size)
        data.plan?.let { text(it.id,200); verifyStore(it.createdAtEpochMillis>=0 && it.captureGeneration>=0)
            it.tripId?.let { id -> text(id,200) } }
        data.cards.forEach { text(it.tripId,200); text(it.petName,24); text(it.ownerNote,240,true); verifyStore(it.savedAtEpochMillis>=0) }
        data.memos.forEach { text(it.id,200); text(it.text,180); it.sourceTripId?.let { id -> text(id,200) } }
        data.places.forEach { text(it.id,200); text(it.title,60); text(it.note,240,true) }
        verifyStore(data.frame!=PetCardFrame.PAW || PetPassReward.PAW_FRAME in data.claimed)
        verifyStore(data.frame!=PetCardFrame.STAR || PetPassReward.STAR_FRAME in data.claimed)
        verifyStore(data.restIntervalMinutes==null || data.restIntervalMinutes in 15..240)
        data.restSession?.let { s ->
            text(s.id,200); verifyStore(data.enabled && s.startedAtEpochMillis>=0 && s.intervalMinutes in 15..240)
            verifyStore(s.elapsedTotalMillis in 0..604_800_000L && s.elapsedSinceRestMillis in 0..s.elapsedTotalMillis)
            verifyStore(s.confirmedRests in 0..10_000 && (!s.resting || s.paused) && (s.resting || s.checks.isEmpty()))
        }
    }
    private fun DataOutputStream.nullable(value:String?) { writeBoolean(value!=null); if(value!=null) writeUTF(value) }
    private fun DataInputStream.nullable()=if(readBoolean()) readUTF() else null
    private fun <E:Enum<E>> DataOutputStream.enums(values:Set<E>) { writeInt(values.size); values.sortedBy { it.ordinal }.forEach { writeInt(it.ordinal) } }
    private inline fun <reified E:Enum<E>> DataInputStream.enum():E = enumValues<E>().getOrNull(readInt()) ?: throw StoreException(StorageError.INVALID_DATA)
    private inline fun <reified E:Enum<E>> DataInputStream.enums():Set<E> {
        val list=List(count(enumValues<E>().size)) { enum<E>() }; verifyStore(list.distinct().size==list.size); return list.toSet()
    }
    private fun DataInputStream.count(max:Int):Int=readInt().also { verifyStore(it in 0..max,StorageError.LIMIT_EXCEEDED) }
}
