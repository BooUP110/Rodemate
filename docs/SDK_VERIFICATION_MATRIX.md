# SDK·플랫폼 검증표

기준2026-11-15, 최신 Production 소스. **문서 존재·다운로드·현재 계정 사용권·AVD 동작·실차·Market/DO 승인은 별개**다. 아래 AVD는 기존 실행 결과이며 현재 signed APK의 clean install은 미실행이다. 새 SDK 좌표·permission·DO를 추가하지 않았다.

| 경계 / 실제 API | SDK·권한 | 구현·Capability·검증 상태 | 출처 / 남은 Gate |
|---|---|---|---|
| 하향등 `checkLowBeamLightStateCapabilities`, `getCurrentLowBeamLightState`, `registerLowBeamLightState` | Vehicle2.3.2 / `pleos.car.permission.CAR_LIGHTS` | 실제 AAR·샘플에 맞춘 read 경로, READ/READ_WRITE capability 확인. 기존 AVD 값 수신 | [Pleos Vehicle](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/intro), 다운로드 AAR·`SdkVehicleReadSession.kt`. NIGHT 입력·실차·Market 별도 |
| 누적 거리 `checkCurrentDistanceDrivenCapabilities`, `getCurrentDistanceDriven`, `registerDistanceDriven` | Vehicle2.3.2 / CAR_DRIVE | capability 뒤 GET/callback, 누적km로만 사용. 실제 AVD 수신 | 같은 실제 SDK/Production adapter. Trip 거리 차분·연속 운전으로 재해석하지 않음 |
| 앞좌석 왼쪽 목표 `checkHvacTemperatureCapabilities`, `getHvacTemperature` | Vehicle2.3.2 / CAR_CLIMATE | `SeatArea(ROW_1_LEFT)`, CELSIUS, 영역 bounds·read access 확인. 기존 AVD 값 수신 | SDK AAR/`SdkVehicleReadSession.kt`. 운전자 좌석·실내/반려동물 체감온도·자동 제어를 의미하지 않음 |
| 공개 washer warning capability/get, `getTelltalesInfo` 정차 일회 조회 | Vehicle2.3.2 / CAR_SAFETY | 개별 washer 현재 AVD 미지원, 목록 조회는 정보 확인 불가 응답. 실제 목록 수신/Warning Care 미확인 | PRIVATE aggregate capability/listener 사용 안 함. 실제 payload·OEM 코드북·차종별 의미 Gate |
| Vehicle 종속 타입 | `ai.pleos.playground:vehicle-data:1.1.11`, 전이 vehicle-aidl1.1.1 | 실제 POM/AAR로 확인한 compile/runtime ABI | [공식 Maven](https://nexus-playground.pleos.ai/repository/maven-releases/). SDK 제공·배포 약관은 별도 |
| motion `PERF_VEHICLE_SPEED`, `GEAR_SELECTION`, `PARKING_BRAKE_ON` | API34 public AAOS / CAR_SPEED·CAR_POWERTRAIN | CarPropertyManager 읽기·timestamp·status, freshness와 표본 순서 검증. 기존 실제 Drive/Park 관측 | [VehiclePropertyIds](https://developer.android.com/reference/android/car/VehiclePropertyIds), `AaosMotionAdapter.kt`. 실차·다른 VHAL 별도 |
| `CarUxRestrictionsManager` | public `android.car`, 플랫폼 제공 | 물리 motion과 독립. 미확인·stale·restricted이면 정차 상세 차단 | [UX manager](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager). DO 미선언/미승인 |
| 외기 `ENV_OUTSIDE_TEMPERATURE` / `getProperty<Float>`·`registerCallback` | API34 public AAOS / `android.car.permission.CAR_EXTERIOR_ENVIRONMENT` | global0·Float·read config, 원 sample timestamp와 수신 시각 분리. 기존 AVD SYSTEM25°C·callback/reconnect | [property](https://developer.android.com/reference/android/car/VehiclePropertyIds#ENV_OUTSIDE_TEMPERATURE). 날씨/실내 아님. deprecated callback은 API34 호환 코드, 실차 정확도 미검증 |
| `Car.createCar`·manager·unsubscribe/disconnect | `useLibrary("android.car")`, 해당 adapter API29 가드 | lifecycle/session generation·취소·구독 해제, API28에서는 unavailable | [Car](https://developer.android.com/reference/android/car/Car). minSdk28이 모든 플랫폼 기능 지원을 뜻하지 않음 |
| AndroidKeyStore·KeyGenParameterSpec·JCA Cipher | Android/JCA, 새 권한 없음 | AES256/GCM/NoPadding, randomized IV, 기존 AVD 생성/복원과 JVM 변조/실패/호환 검사 | [Keystore](https://developer.android.com/privacy-and-security/keystore). StrongBox·키 복구·전체 파일 replay 방지 보장 없음 |
| 명시 온도 선호·기억 회상 | 앱 Kotlin 로컬, 새 SDK 없음 | USER_INPUT와 실제 정차/UX/freshness·동의, 기존 AVD 관측 | 자동 HVAC·driver identity를 충족하는 입력으로 전용하지 않음 |
| 앱 관측 운전 시간 | `ContinuousDrivingTracker` Kotlin, SYSTEM motion 소비 | 전경 관측·공백 reset·짧은 정차 pause·정차10분 reset, 규칙 구현 | 공식 연속 운전·실제 휴식/피로 판정 아님. 최신 만기 AVD/DO 주행 알림 미검증 |
| 펫 휴식 도우미 | ContextClock·기존 motion·로컬 파일 | 별도 보호자 간격/저장 동의/세션·체크포인트. 직전 AVD Demo/Production 복원·명시 확인 | [펫 이동 도우미](PET_MOBILITY_FEATURES.md). 배경 알람·건강·탑승 감지 없음 |
| 동반 조건 검색 | 내장 공식 사실8곳, 네트워크 없음 | 실제 장소/명시 체중/종류/이동장 비교, 출처/확인일/미기재 구분 | [행별 출처](PET_MOBILITY_FEATURES.md). 실시간 운영/전국 POI/입장 승인 미제공 |
| 한국관광공사 동반여행 API | 미연동 | 사용자 인증키 없음·오프라인 우선 선택, 인증 호출 NOT_RUN | [공식 서비스 안내](https://www.data.go.kr/data/15135102/openapi.do). 계정 승인/키/캐시권 별도 |
| 짧은 UI 효과음 | Android 로컬 자원·SoundPool | 제품 자원5종, 음성 합성/인식과 구분 | 실제 청취 품질·전체 AudioFocus UX 별도. TTS 구현 주장 금지 |

Production 병합 Manifest의 읽기권한7개 외에 SDK `ai.pleos.playground.service.vehicle.VEHICLE_BINDING`과 AndroidX receiver 권한1개가 포함된다. Demo는 차량 SDK/읽기 권한이 없으며 앱별 receiver 권한만 있다. 실제 APK 목록은 [검증 현황](VALIDATION_SUMMARY.md)을 따른다. `CAR_SPEED` 시스템 요청은 정상 사용자 승인으로 처리하며 identity 우회는 하지 않는다.

NIGHT/자동 공조의 미확인 입력, 실제 위치·지역 발급, Seat 의미·warning payload, STT/TTS·Navi·차량 제어, DO·Market, LLM/백엔드/클라우드·결제와 외부 IP 권리는 미해결/미구현 Gate다. NavigationAdapter/AudioAdapter/ControlGateway 계약만으로 구현 완료를 주장하지 않는다. [Pleos 등록 보안 기준](https://document.pleos.ai/docs/pleos-only/app-registration-guide/app-security-guide)과 개별 SDK/배포 심사가 별도로 필요하다.
