package io.booup.roadmate.model

const val EXPEDITION_POINT_PREFIX = "expedition:"
const val EXPEDITION_FIRST_VISIT_XP = 20L

enum class ProgressEventType { TRIP_COMPLETED, FIRST_REGION_VISIT, POSTCARD_ACQUIRED, REFLECTION_COMPLETED,
    POST_DRIVE_SESSION_COMPLETED, LOW_BEAM_OFF_WARNING, IMMEDIATE_VEHICLE_WARNING, REST_RECOMMENDATION, HVAC_SUGGESTION }
data class ProgressEvent(val eventId:String, val type:ProgressEventType, val sourceId:String, val tripId:String,
    val occurredAtEpochMillis:Long, val source:DataSource, val quality:TripDataQuality, val distanceMeters:Long?=null)
enum class XpReason { AWARDED, DAILY_CAP, TRIP_CAP, NON_XP_EVENT }
data class XpLedgerEntry(val ledgerId:String, val seasonId:String, val progressEventId:String,
    val sourceType:ProgressEventType, val sourceId:String?, val sourceTripId:String?, val sourceDeleted:Boolean,
    val tripKey:String, val awardedXp:Long, val awardedAtEpochMillis:Long, val dayKey:String,
    val reason:XpReason, val source:DataSource)
enum class RewardTrack { FREE, PREMIUM }
enum class CosmeticRewardType { COSMETIC, COMPANION_ITEM, THEME, BADGE }
data class CompanionPack(val packId:String, val companionProfileId:CompanionId, val themeId:String, val expressionSetId:String)
data class SeasonReward(val rewardId:String, val title:String, val level:Int, val track:RewardTrack,
    val type:CosmeticRewardType, val missionId:String?=null, val pack:CompanionPack?=null)
enum class RewardClaimState { LOCKED, AVAILABLE, CLAIMED, UNCLAIMED_EXPIRED }
data class RewardClaim(val seasonId:String, val rewardId:String, val claimedAtEpochMillis:Long, val source:DataSource)
enum class MissionVisibility { VISIBLE, HIDDEN_UNTIL_COMPLETED }
enum class MissionSafety { POST_DRIVE_OR_COLLECTION_ONLY }
data class Mission(val missionId:String, val title:String, val eventType:ProgressEventType, val target:Int,
    val visibility:MissionVisibility=MissionVisibility.VISIBLE, val safety:MissionSafety=MissionSafety.POST_DRIVE_OR_COLLECTION_ONLY)
data class MissionProgress(val mission:Mission, val count:Int, val completed:Boolean, val visible:Boolean)
data class ProgressConfig(val tripXp:Long=20, val postcardXp:Long=5, val postDriveXp:Long=10,
    val dailyCap:Long=60, val tripCap:Long=30, val zoneId:String="UTC", val allowExpiredClaims:Boolean=false)
data class Season(val seasonId:String, val title:String, val startsAtEpochMillis:Long, val endsAtEpochMillis:Long,
    val levels:List<Long>, val rewards:List<SeasonReward>, val missions:List<Mission>, val config:ProgressConfig=ProgressConfig())
data class ProgressData(val seasons:List<Season> = emptyList(), val ledger:List<XpLedgerEntry> = emptyList(),
    val claims:List<RewardClaim> = emptyList(), val premiumSeasons:Set<String> = emptySet(),
    val generation:Long=0, val resetAtEpochMillis:Long=0)
data class ExplorationProgress(val visitedRegionIds:Set<RegionId>, val discoveredCount:Int,
    val totalKnownRegionCount:Int?=null, val percentage:Double?=null)
data class RegionCatalog(val regionIds:Set<RegionId>, val source:RegionEvidenceSource)
data class RewardView(val reward:SeasonReward, val state:RewardClaimState)
data class SeasonView(val season:Season, val progress:SeasonProgress, val nextLevelXp:Long?,
    val archived:Boolean, val rewards:List<RewardView>, val missions:List<MissionProgress>)
data class ProgressUiState(val contentAllowed:Boolean=false, val active:SeasonView?=null,
    val archived:List<SeasonView> = emptyList(), val exploration:ExplorationProgress=ExplorationProgress(emptySet(),0),
    val lastAwardedXp:Long=0, val error:StorageError?=null)
