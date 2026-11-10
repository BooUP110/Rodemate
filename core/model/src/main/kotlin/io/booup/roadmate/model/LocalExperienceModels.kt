package io.booup.roadmate.model

/** Device-local explicit preference, never learned or identified as the driver's preference. */
data class PreferredCabinTemperature(val valueCelsius:Double,val source:DataSource,val confirmedAtEpochMillis:Long) {
    init { require(valueCelsius.isFinite() && valueCelsius in 16.0..30.0 && confirmedAtEpochMillis>=0) }
}
data class ComfortAdvice(val outsideCelsius:Double,val frontLeftTargetCelsius:Double,val preferredCelsius:Double)
data class ComfortUiState(val contentAllowed:Boolean=false,val preference:PreferredCabinTemperature?=null,
    val advice:ComfortAdvice?=null,val error:StorageError?=null)
data class PeriodMemorySummary(val year:Int,val month:Int,val zoneId:String,val driveCount:Int,
    val recordedDurationMillis:Long?,val reflectionCount:Int)
/** Recent preview deliberately contains no reflection/memory text. */
data class RecentMemoryPreview(val memoryId:String,val tripId:String,val createdAtEpochMillis:Long,val durationMillis:Long)
data class MemoryRecallState(val contentAllowed:Boolean=false,val summary:PeriodMemorySummary?=null,
    val recent:RecentMemoryPreview?=null,val openedMemoryId:String?=null,val openedText:String?=null)
