package io.booup.roadmate.progress

import io.booup.roadmate.contracts.SeasonRepository
import io.booup.roadmate.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ProgressCoordinator(val repository:SeasonRepository,private val mode:RuntimeMode,private val clock:ContextClock,
    private val controlsAllowed:()->Boolean,private val initialSeasons:List<Season> = emptyList(),
    private val catalog:RegionCatalog?=null) {
    private val mutex=Mutex(); private var error:StorageError?=null
    private val mutableUi=MutableStateFlow(ProgressUiState()); val uiState=mutableUi.asStateFlow()
    suspend fun initialize()=mutex.withLock { capture(repository.installSeasons(initialSeasons)); publish() }
    suspend fun refresh()=mutex.withLock {
        val s=repository.state.value
        if(s.loaded && s.error==null && s.progression.seasons.isNotEmpty()) {
            val before=s.progression.ledger.sumOf { it.awardedXp }
            capture(repository.reconcileProgress(clock.now(),s.captureGeneration,s.progression.generation))
            publish((repository.state.value.progression.ledger.sumOf { it.awardedXp }-before).coerceAtLeast(0))
        } else publish()
    }
    suspend fun claim(seasonId:String,rewardId:String):Boolean=mutex.withLock {
        if(!controlsAllowed()) { publish(); return@withLock false }
        val ok=capture(repository.claimReward(seasonId,rewardId,clock.now(),repository.state.value.progression.generation))
        publish(); ok
    }
    private fun capture(result:StorageResult<Unit>):Boolean { error=(result as? StorageResult.Failure)?.error; return error==null }
    private fun publish(awarded:Long=0) {
        val s=repository.state.value
        val allowed=s.loaded && s.error==null && controlsAllowed()
        if(!allowed) { mutableUi.value=ProgressUiState(error=error?:s.error); return }
        val now=clock.now(); val views=s.progression.seasons.map { SafeProgressEngine().resolve(it,s.progression,now) }
        mutableUi.value=ProgressUiState(true,views.singleOrNull { now.epochMillis in it.season.startsAtEpochMillis until it.season.endsAtEpochMillis },
            views.filter { it.archived },ExplorationResolver().resolve(s.visits,mode,catalog),awarded,error?:s.error)
    }
}

/** Product configuration examples, not a paid entitlement provider or Demo fixtures. */
object LocalSeasonCatalog {
    val first=Season("roadmate-local-season-1","로드메이트 시즌 · 설계 예시",
        java.time.Instant.parse("2026-11-01T00:00:00Z").toEpochMilli(),java.time.Instant.parse("2027-01-01T00:00:00Z").toEpochMilli(),
        listOf(0,20,40,60,80).map { it.toLong() },listOf(
            SeasonReward("original-roadmate-badge","로드메이트 기본 배지",1,RewardTrack.FREE,CosmeticRewardType.BADGE),
            SeasonReward("original-postcard-theme","추억 카드 색상",3,RewardTrack.FREE,CosmeticRewardType.THEME,"first-postcard"),
            SeasonReward("original-somsom-pack","솜솜 표현 팩 · 임시",5,RewardTrack.FREE,CosmeticRewardType.COMPANION_ITEM,
                pack=CompanionPack("original-somsom-pack",CompanionProfiles.somsom.id,"soft-color","text-initials")),
            SeasonReward("original-premium-color","추억 색상 · 프리미엄",1,RewardTrack.PREMIUM,CosmeticRewardType.COSMETIC)),
        listOf(Mission("review-completed-trip","드라이브 정리하기 · 회고 건너뛰기 포함",ProgressEventType.POST_DRIVE_SESSION_COMPLETED,1),
            Mission("first-postcard","첫 엽서의 기록",ProgressEventType.POSTCARD_ACQUIRED,1,MissionVisibility.HIDDEN_UNTIL_COMPLETED),
            Mission("two-postcards","보유 엽서 두 장 모으기",ProgressEventType.POSTCARD_ACQUIRED,2)))
}
