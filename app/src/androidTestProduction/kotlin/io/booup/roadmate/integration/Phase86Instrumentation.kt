package io.booup.roadmate.integration

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import io.booup.roadmate.model.*
import io.booup.roadmate.storage.*
import io.booup.roadmate.security.AndroidStoreKeys
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.*

/** TEST APK ONLY. Isolated fixed synthetic storage, ordinary app identity; no vehicle writes. */
class Phase86Instrumentation : Instrumentation() {
    private var options = Bundle()
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); options = arguments ?: Bundle(); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            val report = runBlocking {
                if(options.getString("mode")=="actual-migration") actualMigration()
                else if(options.getString("mode")=="outside") outside()
                else security(options.getString("mode") ?: "security")
            }
            result.putString("phase86",report.toString()); finish(Activity.RESULT_OK,result)
        } catch (failure: Throwable) {
            result.putString("phase86",JSONObject().put("result","FAIL").put("type",failure.javaClass.simpleName).toString())
            finish(Activity.RESULT_CANCELED,result)
        }
    }
    private suspend fun outside():JSONObject {
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        val clock=io.booup.roadmate.AndroidContextClock()
        val binding=io.booup.roadmate.RuntimeBindings.create(targetContext,scope,clock)
        try {
            binding.repository.foreground()
            suspend fun fresh()=withTimeout(25_000) {
                while(binding.repository.context.value.outsideTemperatureCelsius.currentValue==null) delay(100)
                binding.repository.context.value.outsideTemperatureCelsius
            }
            val first=fresh(); check(first.source==DataSource.SYSTEM)
            val initial=first.observation as Reading.Observed
            withTimeout(10_000) {
                while((binding.repository.context.value.outsideTemperatureCelsius.observation as? Reading.Observed)?.metadata?.sourceTimestampNanos==initial.metadata.sourceTimestampNanos) delay(100)
            }
            val updated=fresh(); val meta=(updated.observation as Reading.Observed).metadata
            check(meta.observedAtMonotonicMillis!!<=meta.receivedAtMonotonicMillis!!)
            val c=binding.repository.context.value
            check(c.driverPresent.currentValue==null && c.care.lighting.currentValue==null && c.care.preference.currentValue==null)
            val decision=binding.careEngine!!.evaluate(c)
            check(decision.emittedEvents.isEmpty() && decision.activeEvent==null)
            binding.repository.background()
            check(binding.repository.context.value.outsideTemperatureCelsius.currentValue==null)
            binding.repository.foreground(); val reconnected=fresh()
            return JSONObject().put("result","PASS").put("source",updated.source.name).put("value_celsius",updated.currentValue)
                .put("callback_timestamp_advanced",true).put("sample_nanos",meta.sourceTimestampNanos)
                .put("received_monotonic_ms",meta.receivedAtMonotonicMillis).put("age_ms",updated.ageMillis)
                .put("reconnect","PASS").put("reconnected_source",reconnected.source.name)
                .put("driver_synthesized",false).put("automatic_care_enabled",false)
        } finally { binding.repository.background(); binding.repository.close(); scope.cancel() }
    }
    private suspend fun actualMigration(): JSONObject {
        val root=File(targetContext.noBackupFilesDir,"roadmate-production")
        val file=File(root,"trips.v1.bin")
        val existed=file.exists()
        val legacy=existed && !AesGcmEnvelope.isEnvelope(file.readBytes())
        val repository=FileTripRepository(root,RuntimeMode.PRODUCTION,encryption=AesGcmEnvelope(
            AndroidStoreKeys("roadmate.production.aggregate.v1"),RuntimeMode.PRODUCTION))
        check(repository.load()==StorageResult.Success(Unit))
        check(!file.exists() || AesGcmEnvelope.isEnvelope(file.readBytes()))
        // Only counts leave this process; original user text and plaintext files never become artifacts.
        return JSONObject().put("result","PASS").put("existing_file",existed).put("legacy_migrated",legacy)
            .put("trip_count",repository.state.value.trips.size).put("memory_count",repository.state.value.memories.size)
            .put("progress_entries",repository.state.value.progression.ledger.size)
            .put("user_data_deleted",false)
    }
    private suspend fun security(mode: String): JSONObject {
        val root=File(targetContext.noBackupFilesDir,"phase86-security-fixture")
        root.mkdirs()
        val file=File(root,"trips.v1.bin")
        val keys=AndroidStoreKeys("roadmate.fixture.phase86.v1")
        val encryption=AesGcmEnvelope(keys,RuntimeMode.PRODUCTION)
        val report=JSONObject().put("mode",mode).put("shell_identity",false).put("vehicle_writes",0)
        if(mode=="security" && !file.exists()) legacyFixture(file)
        check(file.exists())
        val wasLegacy=!AesGcmEnvelope.isEnvelope(file.readBytes())
        val repository=FileTripRepository(root,RuntimeMode.PRODUCTION,encryption=encryption)
        check(repository.load()==StorageResult.Success(Unit))
        check(repository.state.value.trips.single().tripId=="phase86-fixture")
        check(repository.state.value.memories.single().text==FIXED_TEXT)
        val bytes=file.readBytes()
        check(AesGcmEnvelope.isEnvelope(bytes))
        check(keys.existingKey()?.encoded==null)
        check(!bytes.toString(Charsets.ISO_8859_1).contains(FIXED_TEXT))
        check(!File(root,"trips.pending").exists())
        report.put("legacy_migration",if(wasLegacy) "PASS" else "ALREADY_ENCRYPTED")
            .put("keystore_reopen","PASS").put("plaintext_scan","PASS")
        if(mode=="security") {
            val tampered=bytes.copyOf().also { it[it.lastIndex]=(it.last().toInt() xor 1).toByte() }
            try { encryption.open(tampered); error("authentication not enforced") }
            catch(e:StorageSecurityException) { check(e.error==StorageError.AUTHENTICATION_FAILED) }
            val missing=AndroidStoreKeys("roadmate.fixture.phase86.nevercreated")
            check(missing.existingKey()==null)
            try { AesGcmEnvelope(missing,RuntimeMode.PRODUCTION).open(bytes); error("missing key not enforced") }
            catch(e:StorageSecurityException) { check(e.error==StorageError.KEY_MISSING) }
            check(missing.existingKey()==null)
            // Delete only this test fixture's record using the real repository, never acceptance data.
            val deleteRoot=File(root,"delete-check"); deleteRoot.mkdirs()
            File(deleteRoot,"trips.v1.bin").writeBytes(bytes)
            val deletion=FileTripRepository(deleteRoot,RuntimeMode.PRODUCTION,encryption=encryption)
            check(deletion.load()==StorageResult.Success(Unit))
            check(deletion.delete("phase86-fixture")==StorageResult.Success(Unit))
            val reopened=FileTripRepository(deleteRoot,RuntimeMode.PRODUCTION,encryption=encryption)
            check(reopened.load()==StorageResult.Success(Unit)); check(reopened.state.value.trips.isEmpty())
            check(reopened.state.value.memories.isEmpty())
            report.put("tamper_rejection","PASS").put("missing_key_no_replacement","PASS").put("delete_encrypted_fixture","PASS")
        }
        return report.put("result","PASS")
    }
    private fun legacyFixture(file:File) = DataOutputStream(FileOutputStream(file)).use { out ->
        out.writeUTF("ROADmate-local"); out.writeInt(3); out.writeUTF("PRODUCTION"); out.writeLong(0)
        out.writeInt(2)
        listOf("TRIP_HISTORY","EMOTIONAL_MEMORY").forEach {
            out.writeUTF(it); out.writeBoolean(true); out.writeBoolean(true); out.writeLong(1)
            out.writeBoolean(false); out.writeUTF("USER_INPUT"); out.writeUTF("test-only")
        }
        out.writeInt(1)
        out.writeUTF("phase86-fixture"); out.writeLong(100); out.writeBoolean(true); out.writeLong(200); out.writeUTF("DERIVED")
        out.writeLong(100); out.writeLong(50); out.writeBoolean(false); out.writeBoolean(false); out.writeBoolean(false)
        out.writeBoolean(false); out.writeInt(0); out.writeUTF("CONFIRMED_STATIONARY"); out.writeUTF("PARTIAL"); out.writeLong(200); out.writeBoolean(false)
        out.writeBoolean(false) // no active checkpoint
        out.writeInt(1); out.writeUTF("phase86-memory"); out.writeUTF("phase86-fixture"); out.writeUTF(FIXED_TEXT)
        out.writeLong(250); out.writeUTF("USER_INPUT"); out.writeBoolean(true)
        out.writeInt(0); out.writeInt(0); out.writeInt(0) // post-drive / visits / postcards
        out.writeLong(0); out.writeLong(0); out.writeInt(0); out.writeInt(0); out.writeInt(0); out.writeInt(0) // progression
    }
    companion object { private const val FIXED_TEXT="PHASE86_FIXED_MEMORY_NOT_USER_DATA" }
}
