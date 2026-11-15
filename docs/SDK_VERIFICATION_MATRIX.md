# SDK · 플랫폼 검증 상태

## 2026-11-15 펫 이동 도우미 추가

| 항목 | 현재 구현·근거 | 미검증/제외 Gate |
|---|---|---|
| 펫 휴식 동행 타이머 | 기존 `ContextClock`·motion evidence를 소비하는 앱 Kotlin 로직. 독립 보호자 세션이며 Trip/건강/연속 운전 판단 아님 | 주행 중 알림·백그라운드 알람·음성·DO 승인을 추가하지 않음 |
| 동반 조건 검색 | 서울시·운영사·제주관광공사 공식 공개 사실을 확인해 담은 오프라인8곳. [행별 출처](PET_MOBILITY_FEATURES.md) | 실시간 운영/예약·전체 POI·위치·Navi 경로·입장 승인은 미제공 |
| 한국관광공사 반려동물 동반여행 API | [공식 서비스 안내](https://www.data.go.kr/data/15135102/openapi.do) 존재 확인 | 사용자가 인증키 없음/오프라인 우선 선택. 계정 승인·키·실제 인증 호출은 미검증, 클라이언트 미추가 |
| 저장 호환성 | 기존 AES-GCM 펫 파일 schema1 읽기, 다음 변경 쓰기에서2로 저장 | 새 키·권한·dependency·Trip schema 변경 없음 |

아래는 기존 Phase 기준 검증표다. 새 API 사용 권한으로 해석하지 않는다.

기준일 2026-11-10. 공식 API 존재, 다운로드된 아티팩트, 계정 이용권, AVD 결과, 실차 지원, Market/DO 승인은 서로 다른 Gate다. 기존 Vehicle2.3.2/vehicle-data1.1.11 의존성을 유지하며 새 Maven 좌표를 추가하지 않았다.

| 경계 / 실제 API | 버전 / 권한 | Capability·실행 검증 | 제한·근거 |
|---|---|---|---|
| 외기 `VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE` | API34 public AAOS / `Car.PERMISSION_EXTERIOR_ENVIRONMENT` = `android.car.permission.CAR_EXTERIOR_ENVIRONMENT`, normal | global0·Float·READ/READ_WRITE config 확인 → `getProperty<Float>` → `registerCallback`; AVD SYSTEM25°C와 갱신/reconnect | 날씨/실내 온도 아님. [property](https://developer.android.com/reference/android/car/VehiclePropertyIds#ENV_OUTSIDE_TEMPERATURE), [Car 권한](https://developer.android.com/reference/android/car/Car#PERMISSION_EXTERIOR_ENVIRONMENT) |
| 외기 lifecycle `Car.createCar`·`getCarManager`·`unregisterCallback`·`disconnect` | Android car optional platform library, min API29 가드 | Main lifecycle/IO read, ready/session generation, 취소·해제; old callback 차단 JVM | API28 외기 UNSUPPORTED. [manager](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager), [Car](https://developer.android.com/reference/android/car/Car) |
| 외기 sample `CarPropertyValue.timestamp/status/areaId` | public AAOS / 위 읽기 권한 | 원래 monotonic ns와 수신 시각을 별도 유지. 기존 external60초 정책; stale/denied/disconnect/invalid는 unavailable | 최신 callback보다 오래된 GET은 버림. [timestamp](https://developer.android.com/reference/android/car/hardware/CarPropertyValue#getTimestamp()) |
| 기존 motion / UX restrictions | AAOS CAR_SPEED·CAR_POWERTRAIN / public UX manager | 실제 속도/기어·UX 제한, fail-closed. 최신 sample 보존하는 읽기 순서 경합만 수정 | MotionClassifier threshold/UNKNOWN/freshness 정책과 DO 미선언 유지 |
| 기존 하향등·주행거리·앞좌석 왼쪽 목표 | Pleos Vehicle2.3.2 / 기존 CAR_LIGHTS·CAR_DRIVE·CAR_SAFETY·CAR_CLIMATE | 다운로드 SDK/샘플과 기존 AVD 읽기 유지 | 목표값을 실내/driver 값으로 바꾸지 않음. [Pleos 문서](https://document.pleos.ai/), [공식 아티팩트 저장소](https://nexus-playground.pleos.ai/repository/maven-releases/) |
| 로컬 저장 `AndroidKeyStore`, `KeyGenParameterSpec.Builder`, `Cipher` | min28에서 사용 가능한 Android/JCA, 새 권한 없음 | AES256/GCM/NoPadding, randomized IV, non-exportable key. 실제 API34 fixture/migration/reopen | StrongBox/사용자 인증/복구/전체 파일 replay 방지는 보장하지 않음. [Keystore](https://developer.android.com/privacy-and-security/keystore), [crypto](https://developer.android.com/privacy-and-security/cryptography) |
| 명시 선호 / Comfort / 기억 회상 | 앱 로컬 Kotlin / 새 SDK·permission·ControlGateway 없음 | 실제 정차+현재 UX+입력 freshness+동의에 종속 | 명시 선호가 운전자 신원·기존 자동 HVAC의 driver preference 계약을 대신하지 않음 |

외기 permission만 Production Manifest에 추가했다. Demo에는 차량 읽기 permission/session/Vehicle SDK가 없다. APK에 합쳐지는 기존 SDK `VEHICLE_BINDING`과 AndroidX DYNAMIC_RECEIVER permission을 새 기능 권한으로 세지 않는다.

**계속 닫힌 Gate:** NIGHT/Low Beam 활성화, 위치·Region·실제 Postcard, 연속 운전 추정, Warning/Seat, 음성/AudioFocus, Vehicle Control/Navi, DO, LLM/Backend/Cloud, Premium, 외부 API/IP. 공식 문서 재조사와 현재 계정/실차 권한·정책 확인 없이 열지 않는다. Pleos 정책은 [등록 보안 기준](https://document.pleos.ai/docs/pleos-only/app-registration-guide/app-security-guide)과 별도 심사를 따른다.

이전 Phase7.5의31행(A13/B4/C8/D6)을 덮어쓰거나 재분류하지 않는다. 외기와 로컬 회복은 이번 Phase의 별도 증거로 기록한다. [전체 결과](PHASE8_6_ENHANCED_LOCAL_PLATFORM_REPORT.md), [JSON](verification/phase8-6-artifacts.json), [Phase8.5 근거 목록](PHASE8_5_SOURCE_REGISTER.md).
