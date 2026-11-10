# 아키텍처

SDK/Android 연동, 결정적 로컬 판단, 화면 표현을 분리한다. 기본 제품은 3개 모듈이며 ViewModel의 StateFlow를 Compose가 구독한다.

| 모듈 | 역할 |
|---|---|
| app | 화면·ViewModel·플랫폼 lifecycle, Production Pleos/AAOS adapter와 Demo composition root |
| core:model | 차량 관측·출처·신선도, Care/Trip/Consent/Progress 모델. Android 의존성 없음 |
| core:contracts | Adapter/Repository 계약, Care·Trip·PostDrive·Progress 엔진, 파일 저장소. JVM 테스트 가능 |

## 데이터 흐름

~~~mermaid
flowchart TD
    V[Pleos Vehicle 읽기] --> A[Production SDK Adapter]
    M[AAOS 물리 motion] --> A
    O[AAOS 실제 외기 센서] --> A
    U[AAOS UX restrictions] --> A
    D[Demo 합성 Adapter] --> C[VehicleContextStore]
    A --> C
    C --> E[DeterministicCareEngine]
    E --> P[CompanionPresenter]
    C --> T[TripTracker / TripCoordinator]
    T --> R[로컬 Repository]
    R --> H[PostDrive / SafeProgressEngine]
    C --> G[정차 / UX ContentGate]
    P --> VM[ViewModel / StateFlow]
    H --> VM
    G --> VM
    VM --> UI[Compose UI]
~~~

## SDK와 정책 경계

Production SDK 타입은 app의 production/platform에 격리한다. VehicleAdapter와 DrivingRestrictionAdapter 등 앱 소유 계약을 통해 관측을 전달하며 NavigationAdapter/AudioAdapter는 실제 기능이 활성화되지 않은 확장 경계다. 계약의 존재가 SDK 연결 완료를 뜻하지 않는다.

관측은 출처·timestamp·품질·권한/지원 상태를 보존한다. stale/null/미지원/권한 거부/연결 오류를 정상 값으로 만들지 않는다. UI는 SDK를 직접 호출하거나 미수신 값을 생성하지 않는다.

물리 motion과 UX restrictions는 독립 입력이다. 속도/기어만으로 화면 허용을 만들거나, UX 허용만으로 물리적 정차·Trip 종료를 확정하지 않는다. 정차 콘텐츠는 fresh stationary와 명시적 UX 허용 및 해당 작업의 저장/완료 조건을 함께 요구한다. 제한 전환 시 상세 화면·입력·IME·확인창을 제거한다.

## 엔진과 표현

DeterministicCareEngine은 검증 가능한 입력으로 규칙·우선순위·중복 억제·쿨다운을 평가한다. CompanionPresenter는 결과를 동반자 표현으로 변환한다. 안전 등급·P1 문구·제어 정책에는 캐릭터/보상/유료 여부가 영향을 주지 않는다.

TripTracker는 기본 시작10초·정지120초를 사용하고 짧은 정차 후 같은 Trip을 재개한다. 관측 공백을 연속 운전으로 보간하지 않는다. 총 주행거리는 Trip 거리와 별개이며 Trip 기록 시간을 연속 운전 시간으로 전용하지 않는다.

SafeProgressEngine은 저장된 사실과 고정 정책으로 원장·레벨·미션·수령 가능 상태를 계산한다. UI는 XP를 직접 증가시키지 않는다. 보상은 별도 확인 후 소유 기록으로 남기며 자동 적용하지 않는다. Progress가 차량/Care 판단으로 역류하지 않는다.

## 저장과 실행 변형

현재 저장은 Room/외부 DB 없이 앱 전용 noBackupFilesDir 아래 FileTripRepository를 사용한다. Android Keystore + AES256-GCM envelope1로 보호하고 도메인 schema1/2/3 읽기 호환·schema4 쓰기와 실제 golden fixture를 유지한다. schema4의 명시 선호는 암호화 metadata와 별개다. [저장 보안](PHASE8_6_STORAGE_SECURITY.md)과 [migration](PHASE8_6_STORAGE_MIGRATION.md), [삭제·동의 정책](SAFETY_AND_PRIVACY.md)을 따른다.

## Phase8.6의 독립 로컬 기능

- Production의 OutsideTemperatureAdapter는 실제 `ENV_OUTSIDE_TEMPERATURE`를 읽어 기존 Reading/DataMetadata→VehicleContext로 전달한다. raw sensor timestamp·수신 시각을 분리하고 권한/지원/오류/최신성을 기존 모델로 평가한다. Demo에 실제 adapter/permission을 포함하지 않는다.
- StationaryComfortRule은 명시 USER_INPUT 선호·fresh stationary·현재 UX 허용·실제 외기/front-left target을 요구한다. 기존 자동 HvacRule과 독립이며 CareEvent/ControlIntent/XP/driverPresent를 생성하지 않는다. 설정에서 명시 요청한 경우만 일반 수동 조절 안내를 표시한다.
- MemoryRecallProjector는 동의된 현재 저장 데이터에서 최근 기억 preview와 UTC 월간 사실 요약을 계산한다. 요약 cache를 저장하지 않는다. 원문은 명시 열기 후만 표시하고 현재 데이터/동의/삭제/generation·주행 gate를 다시 검증한다. LLM·새 동의 모델은 없다.

Production과 Demo는 별도 앱 ID·데이터 경로·composition root를 사용한다. 합성 값과 시나리오는 demoDebug에만 두고 Production에 자동 fallback하지 않는다. release Demo는 생성하지 않는다.

현재 차량 제어는 실행하지 않는다. ControlGateway는 사용자 승인·권한·Capability·안전 조건·실제 결과 확인을 독립 검증해야 하는 경계이며 완료된 제어 구현이 아니다. LLM/외부 서비스도 연결하지 않았다.

도메인·파일 저장·adapter 매핑은 JVM에서 검증하고 Android 연동·실제 IME/화면 전환은 별도 AVD에서 검증한다. 선택적 tools/ui-probe는 -Pphase8UiProbe에만 포함하는 독립 testOnly APK로 제품 의존성이 아니다.
