package io.booup.roadmate.storage

import io.booup.roadmate.model.*
import java.io.*

/** Explicit schema1/2 compatibility; schema3 writes. Unknown data is never reset. */
internal class TripStoreCodec(private val mode: RuntimeMode) {
    private val maxFileBytes = 8L * 1_024 * 1_024
    private val maxRows = 10_000
    private val maxTextChars = 4_096
    fun validate(s: TripStorageState) {
        verifyStore(s.captureGeneration >= 0)
        verifyStore(s.trips.size <= maxRows && s.memories.size <= maxRows, StorageError.LIMIT_EXCEEDED)
        verifyStore(s.trips.map { it.tripId }.distinct().size == s.trips.size)
        verifyStore(s.memories.map { it.memoryId }.distinct().size == s.memories.size)
        fun source(value: DataSource) = if (mode == RuntimeMode.DEMO) value == DataSource.SIMULATED
            else value != DataSource.SIMULATED && value != DataSource.UNKNOWN
        fun location(value: TripLocation?) { if (value != null) verifyStore(value.label.isNotBlank() && source(value.source), StorageError.WRONG_ENVIRONMENT) }
        fun events(values: List<TripCareOccurrence>, id: String, start: Long, end: Long) {
            verifyStore(values.size <= maxRows && values.map { it.eventId }.distinct().size == values.size)
            values.forEach { verifyStore(it.tripId == id && it.eventId.isNotBlank() && it.occurredAtEpochMillis in start..end && source(it.source)) }
        }
        s.trips.forEach { t ->
            val ended = t.endedAtEpochMillis
            verifyStore(t.tripId.isNotBlank() && t.state == TripState.COMPLETED && t.startedAtEpochMillis >= 0 &&
                ended != null && ended >= t.startedAtEpochMillis && t.createdAtEpochMillis >= ended &&
                t.durationMillis >= t.confirmedDrivingMillis && t.confirmedDrivingMillis >= 0 && t.distanceMeters?.let { it >= 0 } != false &&
                t.quality != TripDataQuality.INVALID)
            verifyStore(source(t.source) && (mode == RuntimeMode.DEMO || t.completionReason != TripCompletionReason.MANUAL_DEMO), StorageError.WRONG_ENVIRONMENT)
            location(t.startLocation); location(t.endLocation)
            t.routeSummary?.let { verifyStore(it.start == t.startLocation && it.end == t.endLocation && it.distanceMeters == t.distanceMeters) }
            if (t.quality == TripDataQuality.COMPLETE) verifyStore(t.distanceMeters != null && t.routeSummary != null)
            events(t.careEvents, t.tripId, t.startedAtEpochMillis, requireNotNull(t.endedAtEpochMillis))
        }
        s.active?.let { a ->
            verifyStore(a.tripId.isNotBlank() && a.state in setOf(TripState.IN_PROGRESS, TripState.STOP_CANDIDATE) &&
                a.startedAtEpochMillis >= 0 && a.lastEvidenceAtEpochMillis >= a.startedAtEpochMillis &&
                a.accumulatedDurationMillis >= a.confirmedDrivingMillis && a.confirmedDrivingMillis >= 0 &&
                a.distanceMeters?.let { it >= 0 } != false && s.trips.none { it.tripId == a.tripId })
            verifyStore(source(a.source), StorageError.WRONG_ENVIRONMENT); location(a.startLocation)
            events(a.careEvents, a.tripId, a.startedAtEpochMillis, a.lastEvidenceAtEpochMillis)
        }
        s.consents.forEach { (scope, c) ->
            verifyStore(scope == c.scope && c.version.isNotBlank() &&
                (if (mode == RuntimeMode.DEMO) c.source == DataSource.SIMULATED else c.source == DataSource.USER_INPUT), StorageError.WRONG_ENVIRONMENT)
            verifyStore(c.grantedAtEpochMillis?.let { it >= 0 } != false && c.revokedAtEpochMillis?.let { it >= 0 } != false)
            verifyStore(if (c.granted) c.grantedAtEpochMillis != null && c.revokedAtEpochMillis == null else c.revokedAtEpochMillis != null)
        }
        if (s.trips.isNotEmpty() || s.active != null || s.memories.isNotEmpty()) verifyStore(s.consents[ConsentScope.TRIP_HISTORY]?.granted == true, StorageError.CONSENT_REQUIRED)
        if (s.memories.isNotEmpty()) verifyStore(s.consents[ConsentScope.EMOTIONAL_MEMORY]?.granted == true, StorageError.CONSENT_REQUIRED)
        s.memories.forEach { m -> verifyStore(m.memoryId.isNotBlank() && m.text.isNotBlank() && m.createdAtEpochMillis >= 0 &&
            s.trips.any { it.tripId == m.tripId } && source(m.source) && m.confirmedByUser) }
        verifyStore(s.postDrive.size <= maxRows && s.visits.size <= maxRows && s.postcards.size <= maxRows, StorageError.LIMIT_EXCEEDED)
        s.postDrive.forEach { (id, m) ->
            val trip = s.trips.singleOrNull { it.tripId == id }
            verifyStore(id == m.tripId && trip != null && m.reviewed == trip.reviewed && !(m.skipped && m.reflection != null))
            m.reflection?.let { r -> verifyStore(r.tripId == id && r.persistence == ReflectionPersistence.TRIP_LOCAL &&
                (r.mood != null || !r.text.isNullOrBlank()) && r.text?.isBlank() != true &&
                r.createdAtEpochMillis >= requireNotNull(trip!!.endedAtEpochMillis) &&
                (if (mode == RuntimeMode.DEMO) r.source == DataSource.SIMULATED else r.source == DataSource.USER_INPUT)) }
        }
        verifyStore(s.visits.map { it.regionId }.distinct().size == s.visits.size && s.postcards.map { it.key }.distinct().size == s.postcards.size)
        fun regionSource(value: RegionEvidenceSource) = value == if (mode == RuntimeMode.DEMO) RegionEvidenceSource.DEMO else RegionEvidenceSource.VERIFIED_CONTEXT
        fun link(id: String?, deleted: Boolean) { verifyStore(if (deleted) id == null else id != null && s.trips.any { it.tripId == id }) }
        s.visits.forEach { v ->
            verifyStore(v.regionId.value.isNotBlank() && v.firstVisitedAtEpochMillis >= 0 && regionSource(v.source))
            link(v.sourceTripId, v.sourceTripDeleted)
        }
        s.postcards.forEach { p ->
            val visit = s.visits.singleOrNull { it.regionId == p.key.regionId }
            verifyStore(p.key.type == PostcardType.BASIC && p.key.editionId == null && p.regionLabel.isNotBlank() &&
                p.acquiredAtEpochMillis >= 0 && regionSource(p.source) && visit != null &&
                visit.sourceTripId == p.sourceTripId && visit.sourceTripDeleted == p.sourceTripDeleted && visit.source == p.source)
            link(p.sourceTripId, p.sourceTripDeleted)
            try { java.time.ZoneId.of(p.calendar.zoneId) } catch (_: java.time.DateTimeException) { verifyStore(false) }
        }
        verifyStore(s.visits.size == s.postcards.size)
        ProgressStoreCodec(mode).validate(s)
    }
    fun write(file: File, state: TripStorageState) {
        validate(state)
        val bytes = ByteArrayOutputStream().also { buffer -> DataOutputStream(buffer).use { out ->
            out.writeUTF("ROADmate-local"); out.writeInt(3); out.writeUTF(mode.name); out.writeLong(state.captureGeneration)
            out.writeInt(state.consents.size)
            state.consents.toSortedMap(compareBy { it.name }).values.forEach { c ->
                out.text(c.scope.name); out.writeBoolean(c.granted); out.number(c.grantedAtEpochMillis); out.number(c.revokedAtEpochMillis); out.text(c.source.name); out.text(c.version)
            }
            out.writeInt(state.trips.size); state.trips.forEach { out.trip(it) }
            out.writeBoolean(state.active != null); state.active?.let { out.active(it) }
            out.writeInt(state.memories.size); state.memories.forEach { m ->
                out.text(m.memoryId); out.text(m.tripId); out.text(m.text); out.writeLong(m.createdAtEpochMillis); out.text(m.source.name); out.writeBoolean(m.confirmedByUser)
            }
            out.writeInt(state.postDrive.size)
            state.postDrive.toSortedMap().values.forEach { m ->
                out.text(m.tripId); out.writeBoolean(m.skipped); out.writeBoolean(m.reviewed); out.writeBoolean(m.revealConsumed)
                out.writeBoolean(m.reflection != null)
                m.reflection?.let { r -> out.writeBoolean(r.mood != null); r.mood?.let { out.text(it.name) }
                    out.writeBoolean(r.text != null); r.text?.let { out.text(it) }; out.writeLong(r.createdAtEpochMillis); out.text(r.source.name) }
            }
            out.writeInt(state.visits.size)
            state.visits.forEach { v -> out.text(v.regionId.value); out.writeLong(v.firstVisitedAtEpochMillis); out.optional(v.sourceTripId); out.writeBoolean(v.sourceTripDeleted); out.text(v.source.name) }
            out.writeInt(state.postcards.size)
            state.postcards.forEach { p -> out.text(p.key.regionId.value); out.text(p.regionLabel); out.optional(p.sourceTripId); out.writeBoolean(p.sourceTripDeleted)
                out.writeLong(p.acquiredAtEpochMillis); out.text(p.calendar.season.name); out.text(p.calendar.timeOfDay.name); out.text(p.calendar.zoneId); out.text(p.source.name) }
            ProgressStoreCodec(mode).write(out,state.progression)
        } }.toByteArray()
        verifyStore(bytes.size <= maxFileBytes, StorageError.LIMIT_EXCEEDED)
        FileOutputStream(file).use { it.write(bytes); it.fd.sync() }
    }
    fun read(file: File): TripStorageState {
        verifyStore(file.length() <= maxFileBytes, StorageError.LIMIT_EXCEEDED)
        try {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                verifyStore(input.readUTF() == "ROADmate-local")
                val version = input.readInt()
                verifyStore(version in 1..3, StorageError.INCOMPATIBLE_SCHEMA)
                verifyStore(input.readUTF() == mode.name, StorageError.WRONG_ENVIRONMENT)
                val generation = input.readLong()
                val consentList = List(input.count()) { ConsentRecord(enumValueOf(input.text()), input.readBoolean(), input.number(), input.number(), enumValueOf(input.text()), input.text()) }
                verifyStore(consentList.map { it.scope }.distinct().size == consentList.size)
                val trips = List(input.count()) { input.trip() }
                val active = if (input.readBoolean()) input.active() else null
                val memories = List(input.count()) { PersistentEmotionalMemory(input.text(), input.text(), input.text(), input.readLong(), enumValueOf(input.text()), input.readBoolean()) }
                val metadata = if (version == 1) emptyList() else List(input.count()) {
                    val id = input.text(); val skipped = input.readBoolean(); val reviewed = input.readBoolean(); val reveal = input.readBoolean()
                    val reflection = if (input.readBoolean()) TripReflection(id,
                        if (input.readBoolean()) enumValueOf<ReflectionMood>(input.text()) else null,
                        if (input.readBoolean()) input.text() else null, input.readLong(), enumValueOf(input.text()), ReflectionPersistence.TRIP_LOCAL) else null
                    PostDriveMetadata(id, reflection, skipped, reviewed, reveal)
                }
                verifyStore(metadata.map { it.tripId }.distinct().size == metadata.size)
                val visits = if (version == 1) emptyList() else List(input.count()) { RegionVisitRecord(RegionId(input.text()), input.readLong(), input.optional(), input.readBoolean(), enumValueOf(input.text())) }
                val postcards = if (version == 1) emptyList() else List(input.count()) { Postcard(PostcardKey(RegionId(input.text())), input.text(), input.optional(), input.readBoolean(),
                    input.readLong(), CalendarAttributes(enumValueOf(input.text()), enumValueOf(input.text()), input.text()), enumValueOf(input.text())) }
                val progress=if(version<3) ProgressData() else ProgressStoreCodec(mode).read(input)
                verifyStore(input.read() == -1)
                return TripStorageState(true, trips = trips, active = active, consents = consentList.associateBy { it.scope }, memories = memories, captureGeneration = generation,
                    postDrive = metadata.associateBy { it.tripId }, visits = visits, postcards = postcards, progression=progress).also { validate(it) }
            }
        } catch (e: StoreException) { throw e }
        catch (_: IOException) { throw StoreException(StorageError.INVALID_DATA) }
        catch (_: IllegalArgumentException) { throw StoreException(StorageError.INVALID_DATA) }
    }
    fun version(file: File): Int = DataInputStream(FileInputStream(file)).use { it.readUTF(); it.readInt() }
    private fun DataOutputStream.optional(value: String?) { writeBoolean(value != null); value?.let { text(it) } }
    private fun DataInputStream.optional(): String? = if (readBoolean()) text() else null
    private fun DataOutputStream.text(s: String) { verifyStore(s.length <= maxTextChars, StorageError.LIMIT_EXCEEDED); writeUTF(s) }
    private fun DataInputStream.text() = readUTF().also { verifyStore(it.length <= maxTextChars, StorageError.LIMIT_EXCEEDED) }
    private fun DataInputStream.count() = readInt().also { verifyStore(it in 0..maxRows, StorageError.LIMIT_EXCEEDED) }
    private fun DataOutputStream.number(n: Long?) { writeBoolean(n != null); n?.let { writeLong(it) } }
    private fun DataInputStream.number(): Long? = if (readBoolean()) readLong() else null
    private fun DataOutputStream.location(l: TripLocation?) { writeBoolean(l != null); l?.let { text(it.label); text(it.source.name) } }
    private fun DataInputStream.location(): TripLocation? = if (readBoolean()) TripLocation(text(), enumValueOf(text())) else null
    private fun DataOutputStream.events(values: List<TripCareOccurrence>) {
        writeInt(values.size); values.forEach { text(it.tripId); text(it.eventId); text(it.ruleId.name); text(it.priority.name); writeLong(it.occurredAtEpochMillis); text(it.source.name) }
    }
    private fun DataInputStream.events() = List(count()) { TripCareOccurrence(text(), text(), enumValueOf(text()), enumValueOf(text()), readLong(), enumValueOf(text())) }
    private fun DataOutputStream.trip(t: TripRecord) {
        text(t.tripId); writeLong(t.startedAtEpochMillis); number(t.endedAtEpochMillis); text(t.source.name)
        writeLong(t.durationMillis); writeLong(t.confirmedDrivingMillis); number(t.distanceMeters); location(t.startLocation); location(t.endLocation)
        writeBoolean(t.routeSummary != null); events(t.careEvents); text(t.completionReason.name); text(t.quality.name); writeLong(t.createdAtEpochMillis); writeBoolean(t.reviewed)
    }
    private fun DataInputStream.trip(): TripRecord {
        val id = text(); val start = readLong(); val end = number(); val source: DataSource = enumValueOf(text())
        val duration = readLong(); val driving = readLong(); val distance = number(); val startPlace = location(); val endPlace = location()
        val route = if (readBoolean()) RouteSummary(requireNotNull(startPlace), requireNotNull(endPlace), distance) else null
        return TripRecord(id, TripState.COMPLETED, start, end, source, duration, driving, distance, startPlace, endPlace, route,
            events(), enumValueOf(text()), enumValueOf(text()), readLong(), readBoolean())
    }
    private fun DataOutputStream.active(a: ActiveTripSnapshot) {
        text(a.tripId); text(a.state.name); writeLong(a.startedAtEpochMillis); writeLong(a.lastEvidenceAtEpochMillis)
        writeLong(a.accumulatedDurationMillis); writeLong(a.confirmedDrivingMillis); number(a.distanceMeters); location(a.startLocation)
        text(a.source.name); writeBoolean(a.hasGap); events(a.careEvents)
    }
    private fun DataInputStream.active() = ActiveTripSnapshot(text(), enumValueOf(text()), readLong(), readLong(), readLong(), readLong(), number(), location(), enumValueOf(text()), readBoolean(), events())
}
