# 현재 구현 상태

`main` Phase8.6과 `wkdal1433`의 통합 변경은 [통합 검토 기록](MERGE_PHASE86_WKDAL.md)을 따른다. 아래 표와 수치는 병합 전 Phase8.6 기준이며, 통합 검증과 구분한다.

기준: Phase8.6, 2026-11-10. 범위는 R27/R02/R03/R25 네 영역이다. 공모전 제출본 기준이며, 접근성 확대 검수는 **PARTIAL**이고 실차·Market·DO 승인은 받지 않았다. 이전 Phase 보고서는 로컬 개발 보관소에 보존한다.

| 범위 | 실제 구현 | 검증 / 제한 |
|---|---|---|
| R27 필수 보안 | Android Keystore AES256-GCM, envelope1, 암호화 staging/atomic replace, legacy1/2/3 migration | JVM SEC01–12와 추가 실패/오키 테스트. 실제 Keystore·migration·process reopen은 AVD. 실사용 키 무효화 시험은 하지 않음 |
| R02 실제 외기 | AAOS ENV_OUTSIDE_TEMPERATURE global Float 읽기, GET/callback, freshness와 session generation | API34 AVD SYSTEM25°C, callback/reconnect 확인. 날씨/실내 온도 아님 |
| R03 명시 선호 | USER_INPUT 선호16–30°C 저장/수정/삭제, 정차 시 요청한 수동 안내 | 실제 앞좌석 왼쪽 목표와 외기 필요. driver/occupancy 합성·CareEvent·제어·XP 없음 |
| R25 로컬 회상 | 추가 동의/확인한 최근 기억을 명시적으로 열기, UTC 월간 완료 Trip 수/기록 시간/회고 수 | 현재 저장 상태만 투영. 운전 시간 추정·피로/감정 추론·위치·LLM 없음 |
| 기존 Trip/회고/시즌 | 실제 motion 기반10초 시작/120초 완료, 선택 회고, 무료 로컬 진행 | 기존 동의/삭제/보상 정책 유지. 실제 지역 엽서는 비활성 |
| 기존 자동 Care | 필요한 신뢰 입력이 미확인인 Light/HVAC/Rest/Warning 비활성 | 외기와 정차 선호 추가가 자동 HVAC/Low Beam Gate를 열지 않음 |
| Demo | 기존 SIMULATED 시연, 별도 UID·저장·키 | Production에 fixture/host scenario 입력 없음. Demo에는 실제 Pleos Vehicle/외기 session 없음 |

현재 전체 JVM 결과는 **기존492 + 신규49 = 541고유 / 565회**, failure/error/skip **0/0/0**이다. APK3종과 계측 APK 빌드 성공. lint·AVD 세부 결과와 한계는 [Phase8.6 보고서](PHASE8_6_ENHANCED_LOCAL_PLATFORM_REPORT.md), [검증 요약](VALIDATION_SUMMARY.md), [기계 판독 결과](verification/phase8-6-artifacts.json)를 따른다.

NIGHT, 위치/Region/실제 엽서, 연속 운전 추정, 음성/AudioFocus, Warning/Seat, Vehicle Control, Navi, DO, LLM/Backend/Cloud, 결제와 Visual redesign은 구현하지 않았다. 원본 요구사항의 F ID/우선순위를 재정의하지 않으며 Phase8.5의 R ID는 회복 과제 식별자로만 쓴다.
