package io.booup.roadmate

import io.booup.roadmate.model.CompanionId
import io.booup.roadmate.model.DataSource
import io.booup.roadmate.model.ProgressUiState
import io.booup.roadmate.model.RegionEvidenceSource
import io.booup.roadmate.model.RegionId
import io.booup.roadmate.model.TripStorageState
import io.booup.roadmate.model.TripState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

enum class JourneyPass(val title: String, val subtitle: String, val theme: String,
    val artworkIndex: Int, val companions: List<String>, val rewards: List<String>) {
    TRAVEL("치이카와와 함께하는 여행", "세 친구의 움직이는 여행 엽서", "분홍빛 여행", 0,
        listOf("chiikawa", "hachiware", "usagi"), listOf("여행 소인", "파스텔 액자", "움직이는 여행 엽서")),
    STARS("별빛 캠핑", "모아 둔 엽서에 별을 붙여 별자리 완성", "보랏빛 캠핑", 2,
        listOf("starlight"), listOf("별 스티커", "별자리 액자", "빛나는 캠핑 엽서")),
    COAST("바닷길 탐험대", "해안 도시의 풍경을 모으는 바닷길 앨범", "민트빛 바다", 1,
        listOf("waveotter"), listOf("조개 소인", "등대 액자", "움직이는 파도 엽서"));

    fun companionIds() = companions.map { CompanionId("preview:$it") }
}

data class PassGrant(val pass: JourneyPass, val drawn: Boolean)
data class JourneyMemory(val tripId: String, val region: String, val companion: CompanionId)
data class JourneyGameState(
    val grants: List<PassGrant> = emptyList(),
    val claimed: Set<String> = emptySet(),
    val openedCards: Set<String> = emptySet(),
    val starredCards: Set<String> = emptySet(),
    val checkedCare: Boolean = false,
    val companions: Set<CompanionId> = emptySet(),
    val equipped: CompanionId? = null,
    val equippedAt: Long = 0,
    val memories: List<JourneyMemory> = emptyList(),
)

internal val coastalPlaces = listOf(journeyPlaces[1], journeyPlaces[3])
internal const val JOURNEY_SEASON_ID = "roadmate-collab-journey-1"

enum class JourneyStamp(val title: String) {
    DISCOVERY("첫 지역 발견"), OPEN("엽서 펼쳐보기"), STAR("엽서에 별 붙이기"),
    REVIEW("드라이브 회고 마치기"), NOTE("회고에 기분이나 메모 남기기"), CARE("차량 케어 체험 확인"),
    COAST("바다 엽서 모으기"), TWO("엽서 두 장 모으기"), SECRET("두 지역의 추억 모으기"),
}

class JourneyGame(private val storage: () -> TripStorageState,
    private val progress: () -> ProgressUiState, private val controlsAllowed: () -> Boolean,
    private val now: () -> Long, initial: JourneyGameState, private val persist: suspend (JourneyGameState) -> Boolean,
    private val random: Random = Random.Default) {
    private val mutex = Mutex()
    private val data = MutableStateFlow(initial)
    val state = data.asStateFlow()

    fun ownedCards() = storage().postcards.filter { it.source == RegionEvidenceSource.DEMO }
        .map { it.key.regionId }.toSet()

    fun stamps(current: JourneyGameState, owned: Set<RegionId> = ownedCards()): Set<JourneyStamp> {
        val post = storage().postDrive.values
        return buildSet {
            if (owned.isNotEmpty()) add(JourneyStamp.DISCOVERY)
            if (owned.any { it.value in current.openedCards }) add(JourneyStamp.OPEN)
            if (owned.any { it.value in current.starredCards }) add(JourneyStamp.STAR)
            if (post.any { it.reviewed }) add(JourneyStamp.REVIEW)
            if (post.any { it.reflection != null }) add(JourneyStamp.NOTE)
            if (current.checkedCare) add(JourneyStamp.CARE)
            if (coastalPlaces.any { it.id in owned }) add(JourneyStamp.COAST)
            if (owned.size >= 2) add(JourneyStamp.TWO)
            if (journeyPlaces.count { it.id in owned } >= 2) add(JourneyStamp.SECRET)
        }
    }

    fun hasLine(stamps: Set<JourneyStamp>): Boolean {
        val cells = JourneyStamp.entries
        val lines = listOf(listOf(0,1,2), listOf(3,4,5), listOf(6,7,8), listOf(0,3,6),
            listOf(1,4,7), listOf(2,5,8), listOf(0,4,8), listOf(2,4,6))
        return lines.any { line -> line.all { cells[it] in stamps } }
    }

    fun candidates(current: JourneyGameState) = JourneyPass.entries.filter { pass -> current.grants.none { it.pass == pass } }
    fun tickets(current: JourneyGameState, owned: Set<RegionId> = ownedCards()) =
        (1 + (if (coastalPlaces.all { it.id in owned }) 1 else 0) - current.grants.count { it.drawn }).coerceAtLeast(0)
    fun canChoose(current: JourneyGameState, stamps: Set<JourneyStamp>) = current.grants.none { !it.drawn } && hasLine(stamps)

    suspend fun draw(): JourneyPass? = mutex.withLock {
        val current = data.value
        if (!active() || tickets(current) == 0) return@withLock null
        val pass = candidates(current).randomOrNull(random) ?: return@withLock null
        pass.takeIf { save(current.copy(grants = current.grants + PassGrant(pass, true))) }
    }

    suspend fun choose(pass: JourneyPass) = update { current ->
        if (active() && pass in candidates(current) && canChoose(current, stamps(current)))
            current.copy(grants = current.grants + PassGrant(pass, false)) else current
    }

    suspend fun openCard(region: RegionId) = update { current ->
        if (region in ownedCards()) current.copy(openedCards = current.openedCards + region.value) else current
    }

    suspend fun star(region: RegionId) = update { current ->
        if (active() && region in ownedCards()) current.copy(starredCards = current.starredCards + region.value) else current
    }

    suspend fun careChecked() = update { it.copy(checkedCare = true) }

    suspend fun claimSet() = update { current ->
        if (coastalPlaces.all { it.id in ownedCards() }) current.copy(claimed = current.claimed + "coast-set") else current
    }

    suspend fun claim(pass: JourneyPass, tier: Int, premium: Boolean, companion: CompanionId? = null) = update { current ->
        val key = rewardKey(pass, tier, premium)
        val season = progress().active
        if (season?.season?.seasonId != JOURNEY_SEASON_ID || tier !in 1..3 || season.progress.currentLevel < rewardLevel(tier) ||
            key in current.claimed || (premium && current.grants.none { it.pass == pass })) return@update current
        val finalPack = premium && tier == 3
        if (finalPack && (companion == null || companion !in pass.companionIds())) return@update current
        current.copy(claimed = current.claimed + key,
            companions = if (finalPack) current.companions + requireNotNull(companion) else current.companions)
    }

    suspend fun equip(companion: CompanionId) = update { current ->
        if (companion in current.companions) current.copy(equipped = companion, equippedAt = now()) else current
    }

    suspend fun useDefaultCompanion() = update { it.copy(equipped = null, equippedAt = 0) }

    suspend fun remember(place: JourneyPlace, tripId: String) = update { current ->
        val companion = current.equipped ?: return@update current
        val stored = storage()
        if (stored.trips.none { it.tripId == tripId && it.startedAtEpochMillis >= current.equippedAt &&
                it.state == TripState.COMPLETED && it.source == DataSource.SIMULATED }) return@update current
        if (current.memories.any { it.tripId == tripId }) return@update current
        val newPostcard = stored.postcards.any { it.sourceTripId == tripId && it.key.regionId == place.id }
        if (newPostcard || current.memories.none { it.companion == companion }) {
            current.copy(memories = current.memories + JourneyMemory(tripId, place.name, companion))
        } else current
    }

    private fun active() = controlsAllowed() && progress().active?.season?.seasonId == JOURNEY_SEASON_ID

    private suspend fun update(transform: (JourneyGameState) -> JourneyGameState) = mutex.withLock {
        if (!controlsAllowed()) return@withLock false
        save(transform(data.value))
    }

    private suspend fun save(next: JourneyGameState): Boolean {
        if (next == data.value || !persist(next)) return false
        data.value = next
        return true
    }
}

internal fun rewardLevel(tier: Int) = when (tier) { 1 -> 1; 2 -> 3; else -> 5 }
internal fun rewardKey(pass: JourneyPass, tier: Int, premium: Boolean) = "${pass.name}:$tier:$premium"
