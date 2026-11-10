package io.booup.roadmate

import io.booup.roadmate.model.*
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Copy only; never changes health, motion or eligibility semantics. */
object UiCopy {
    fun storageError(error:StorageError)=when(error) {
        StorageError.KEY_MISSING,StorageError.KEY_INVALIDATED -> "이 기기의 보안 키로 기록을 열 수 없어요. 기존 기록 파일은 보존하고 있어요."
        StorageError.AUTHENTICATION_FAILED -> "저장된 기록의 무결성을 확인하지 못했어요. 기존 파일은 덮어쓰지 않았어요."
        StorageError.ENCRYPTION_UNAVAILABLE -> "기록 보호 기능을 사용할 수 없어 저장을 중단했어요."
        StorageError.CONSENT_REQUIRED -> "기록 저장 동의와 별도 확인이 필요해요."
        StorageError.NOT_FOUND -> "기록이 삭제되었거나 변경됐어요."
        else -> "저장을 완료하지 못했어요. 기존 기록은 임의로 지우지 않았어요."
    }
    fun temperatureReading(reading:EvaluatedReading<Double>):String=reading.currentValue?.let(::temperature)
        ?: if(reading.freshness in setOf(Freshness.STALE,Freshness.EXPIRED)) "정보가 오래되어 다시 확인 중이에요" else unavailable(reading.availability)
    fun connection(status: ConnectionStatus) = when (status) {
        ConnectionStatus.CONNECTED -> "차량 정보 연결됨"
        ConnectionStatus.DEGRADED -> "일부 정보 확인 중"
        ConnectionStatus.CONNECTING -> "차량과 연결 중"
        ConnectionStatus.DISCONNECTED -> "차량 연결 대기"
        ConnectionStatus.FAILED -> "연결을 확인해 주세요"
    }
    fun trip(state: TripState, gap: Boolean = false) = if (gap) "운행 정보 다시 확인 중" else when (state) {
        TripState.IDLE -> "다음 드라이브를 기다려요"
        TripState.START_CANDIDATE -> "드라이브 시작 확인 중"
        TripState.IN_PROGRESS -> "드라이브 중"
        TripState.STOP_CANDIDATE -> "잠시 정차 중"
        TripState.COMPLETED -> "드라이브를 마쳤어요"
    }
    fun unavailable(availability: Availability) = when (availability) {
        Availability.UNSUPPORTED -> "현재 차량에서는 사용할 수 없어요"
        Availability.PERMISSION_DENIED -> "정보를 읽을 권한이 필요해요"
        Availability.ERROR -> "정보를 불러오지 못했어요"
        Availability.DISCONNECTED -> "연결 후 확인할 수 있어요"
        else -> "아직 확인되지 않았어요"
    }
    fun duration(millis: Long): String {
        val seconds = millis.coerceAtLeast(0) / 1_000
        return if (seconds < 60) seconds.toString() + "초" else (seconds / 60).toString() + "분 " + (seconds % 60) + "초"
    }
    fun date(epoch: Long): String = DateTimeFormatter.ofPattern("yyyy.MM.dd  HH:mm", Locale.KOREA)
        .withZone(ZoneOffset.UTC).format(Instant.ofEpochMilli(epoch))
    fun mood(mood: ReflectionMood) = when (mood) {
        ReflectionMood.GOOD -> "좋았어요"; ReflectionMood.NEUTRAL -> "평범했어요"; ReflectionMood.BAD -> "아쉬웠어요"
    }
    fun season(season: CalendarSeason) = when (season) {
        CalendarSeason.SPRING -> "봄"; CalendarSeason.SUMMER -> "여름"
        CalendarSeason.AUTUMN -> "가을"; CalendarSeason.WINTER -> "겨울"
    }
    fun temperature(celsius: Double) = String.format(Locale.KOREA, "%.1f°C", celsius)
}
