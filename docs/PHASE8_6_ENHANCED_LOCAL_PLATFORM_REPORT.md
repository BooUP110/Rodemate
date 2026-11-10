# AI Roadmate Phase 8.6 Result

**Overall: PASS — PHASE8_6_IMPLEMENTATION_COMPLETE**  
**User Acceptance: PENDING**  
실행 기간: 2026-11-09~10 KST. R27/R02/R03/R25 네 영역만 구현했다. 제출 Freeze/Visual/Tier2는 시작하지 않았다. 아래 PASS는 명시된 JVM·API34 AVD·정적 검사 범위의 구현 판정이며 실차/Market/DO/법적 승인이나 사용자 인수를 뜻하지 않는다. [기계 판독 증거](verification/phase8-6-artifacts.json).

## 1. Storage Security — mandatory Gate0 PASS

Android Keystore 관리 AES256 키와 표준 JCA `AES/GCM/NoPadding`, 매 암호화마다 provider가 생성하는12-byte IV,128-bit tag를 사용한다. 키 bytes를 내보내거나 APK/PIN/캐릭터 문자열로 만들지 않는다. 키 namespace와 앱 UID·저장은 Production/Demo별로 분리했다.

Envelope1의19-byte 헤더는 AAD이며 domain schema4와 독립이다. 기존 평문을 검증한 후 메모리에서 암호화하고, 암호화 staging fsync → 재인증/도메인 비교 → atomic replace 성공 후 상태를 발행한다. 평문 backup/copy를 만들지 않는다. 누락/무효 키·인증 실패를 typed error로 숨기지 않고 기존 파일을 보존하며 자동 초기화/새 키 교체를 하지 않는다. 삭제는 실제 aggregate 항목 제거 후 재암호화한다.

SEC01–12 + migration 실패/잘못된 키/JCA provider 런타임 오류15개 테스트와 실제 Android Keystore 계측이 통과했다. `ProviderException`도 메시지 없이 ENCRYPTION_UNAVAILABLE로 바꾸고 기존 암호문을 보존한다. 실제 UI로 저장한 고정 회고 문구가 ciphertext에 없음을 확인했다. 사용자 원문 파일·키·alias·암호문을 공개 artifact에 저장하지 않았다. [보안 설계](PHASE8_6_STORAGE_SECURITY.md).

## 2. Existing Data Migration

| 항목 | 결과 / 범위 |
|---|---|
| plaintext schema1/2 | golden fixture → encrypted reopen JVM PASS |
| plaintext schema3 | JVM + 실제 API34 Keystore fixture PASS |
| 원래 Production 파일 | 최초 계측 legacy=true → encrypted 전환, Trip0/Memory0/기존 진행6 유지 |
| encrypted reopen | 실제 force-stop·재계측 PASS; 최종 기존 진행6 유지 |
| migration 중 실패 | JVM staging/move 실패에서 기존 bytes 유지·민감 상태 미발행 PASS |
| domain schema4 | 명시 선호 추가. 쓰기는4, 읽기는1–4. 이미 암호화된3은 재오픈만으로 불필요하게 재작성하지 않음 |

원래 파일에는 사용자 회고/기억이 없었으므로 실제 사용자 문구 migration까지 검증했다고 주장하지 않는다. 별도 synthetic fixture와 JVM이 그 경로를 보완한다. 사용자 앱을 삭제하거나 pm clear/키 삭제를 하지 않았다. [migration 계약](PHASE8_6_STORAGE_MIGRATION.md).

## 3. Outside Temperature

Public AAOS `ENV_OUTSIDE_TEMPERATURE`, `Car.PERMISSION_EXTERIOR_ENVIRONMENT` normal 권한을 Production에만 추가했다. global0·Float·READ capability를 확인하고 `getProperty<Float>`/`registerCallback`을 사용한다. 원래 sample monotonic timestamp와 수신 시각을 구분하고 기존 external freshness60초를 적용한다. unsupported/denied/no-value/invalid/disconnect/SDK error를 unavailable로 구분하며 오래된 값에 현재 시각을 붙이지 않는다.

최종 API34 AVD에서 **SYSTEM25°C**, 실제 GET/갱신 callback/reconnect PASS. 최종 계측의 원래 sample ns·수신 monotonic ms·age10ms를 JSON에 기록했다. bounded 앱 로그에서도 실제 GET/callback을 관측했다. OT01–08 + invalid/error/응답 순서10개 JVM 테스트가 통과했다. 날씨나 실내 온도 API를 추가하지 않았다.

## 4. Production VehicleContext

실제 외기 SYSTEM 값을 기존 `outsideTemperatureCelsius`에 합친다. 기존 앞좌석 왼쪽 목표는 VEHICLE_SDK22°C이며 driver 온도/실내 온도로 재해석하지 않는다. foreground/reconnect마다 session generation을 확인하고 background/disconnect에서 관측을 무효화·해제한다. Production의 SIMULATED fallback/host scenario 주입과 driver/lighting/preference 합성이 없다. 실제 계측에서 기존 자동 Care 미활성도 확인했다.

## 5. Comfort Preference

정차 설정에서 사용자가 직접 고른16–30°C를 USER_INPUT·명시 시각과 FUNCTIONAL_PREFERENCE 동의로 암호화 저장한다. 자동 학습/신원 추정이 없다. 실제 UI24°C 저장 →24.5°C 수정 → force-stop 복원 → 별도 삭제 PASS. Trip/기억 삭제와 선호 삭제 목적을 구분한다.

## 6. Stationary Comfort

정차 motion·현재 FULL_PARKED UX·foreground·연결·fresh 외기·fresh 앞좌석 왼쪽 목표·저장 선호·명시 요청을 모두 만족할 때만 값을 보여 준다. 제안은 사용자가 직접 조절하는 안내이며 CareEvent/ControlIntent/XP를 만들지 않는다. CF01–10 + 추가 케이스11개에서 moving/unknown/restricted/stale/무선호/driver 미합성/삭제를 검증했다. 실제 AVD 정차 표시와 Drive1km/h UX 제한에서 제거됨을 확인했다.

차량 write **호출 경로0**: setter·권한 우회 호출/ControlGateway 실행 등록이 제품 코드에 없음을 감사했다. 이것을 VHAL 런타임 쓰기 카운터 실측0으로 표현하지 않는다. Console의 속도/기어 변경은 앱 밖의 공식 AVD 시험 입력이다.

## 7. Existing HVAC Rule

`CareRules.kt`의 HvacRule과 기존 결정적 엔진·RuleEligibility는 기준 해시 그대로다. Production 자동 HVAC Care는 driver preference/occupancy 계약 미확인으로 계속 비활성이다. 새 정차 선호가 기존 driver preference로 주입되지 않는다. Low Beam P1도 활성화하지 않았다.

## 8. Local Memory Recall

기록 동의 아래 현재 완료 Trip만 UTC 월간 Trip 수·기록 시간 합·선택 회고 수로 계산한다. overflow는 확인 불가로 처리한다. 위치/감정/피로/연속 운전/운행 점수/LLM 추론을 만들지 않는다. 별도 기억 동의 + 항목 확인된 USER_INPUT 기억만 최근 preview에 연결하고, 원문은 사용자가 `기억 보기`를 눌렀을 때만 연다. 정책 잠금/화면 이탈/동의 철회/원본 삭제/회고 변경 시 원문 연결을 해제한다. 저장 summary cache가 없어 삭제 즉시 재투영된다.

MR01–10 JVM PASS. 실제10/120초 Trip의1회·2분16초·회고1개를 화면에서 확인했고, process reopen 후 명시 기억 열기와 삭제 후 Trip0/회상 제거/재시작 무복원을 확인했다.

## 9. Production UI

홈의 외기 센서 한 줄, 설정의 명시 선호/정차 안내, 기억 화면의 최근 기억/UTC 사실 요약만 기존 컴포넌트로 추가했다. 실제 SYSTEM/VEHICLE_SDK 값을 쓰고 개발용 timestamp/property/raw JSON/Keystore alias/stack trace를 제품 화면에 넣지 않았다. 공개 화면에 테스트 전용 시나리오 선택기를 만들지 않았다. 전면 디자인 변경/외부 캐릭터·에셋은 없다.

## 10. Demo Isolation

Demo는 기존 SIMULATED 경계·별도 ID/저장 경로·키를 유지한다. 실제 Vehicle SDK/외기 session/읽기 permission이 Demo APK에 없다. Production debug/release에는 Demo adapter/scenario/host selector/새 계측·테스트 키·고정 테스트 문구가 없다. 공유 Comfort도 Demo front-left 입력이 없으면 안내 불가이며 실제 연동처럼 채우지 않는다. 기존 Demo100회 JVM 회귀와 APK 검사는 수행했고 Phase8의8개 Demo UI 전체 재실행은 하지 않았다.

## 11. AVD Cases

Pleos Connect2.1.0/API34, user10,2560×1440/density160/font1.0. 원래 앱은 유지하고 회고/삭제 UI는 **동일 Production 소스**의 opt-in 별도 ID/UID/Keystore 검증 앱에서 수행했다. 일반 앱에서 실제 외기/원래 파일 migration·reopen을 별도 확인했다. [검증 앱 빌드](BUILD_AND_RUN.md).

| ID | 결과 | 실제 증거 / 범위 |
|---|---|---|
| P8.6-01 | PASS | 최초 실제 원래 파일 migration + Android Keystore schema3 fixture |
| P8.6-02 | PASS | force-stop 후 암호화 Trip·회고·기억·선호 복원 |
| P8.6-03 | PASS | 실제 UI 기록 삭제, fixture 암호화 파일에서 삭제·재오픈 |
| P8.6-04 | PASS | 실제 Production 외기 GET, SYSTEM25°C 홈 |
| P8.6-05 | PASS | 실제 callback sample timestamp 증가 |
| P8.6-06 | PASS | background 관측 제거 → foreground reconnect |
| P8.6-07 | PASS | 명시 선호 저장/수정/재시작/삭제 |
| P8.6-08 | PASS | Park0, 외기25/앞좌석 왼쪽22/선호24 수동 안내 |
| P8.6-09 | PASS | 실제 Drive1km/h UX 제한에서 입력·안내 제거; moving/unknown/stale 추가 JVM |
| P8.6-10 | PASS, 정적 감사 | read-only 호출·ControlGateway 배선 검사; VHAL counter NOT_MEASURED |
| P8.6-11 | PASS | 재시작 후 명시 기억 열기, preview 원문 부재 |
| P8.6-12 | PASS | UTC 이번 달 실제 저장1회/2분16초/회고1개 |
| P8.6-13 | PASS | 삭제 즉시 열린 기억 제거·집계0 |
| P8.6-14 | PASS, process restart | force-stop 재시작 후 삭제한 내용 무복원; 새 전체 AVD coldboot 인증 아님 |

최초 Console 비활성 외기 timeout, 재개 시 AVD 종료/no-device, 부팅 서비스 준비 전 activity 해석 실패, 읽기 경합에 따른 첫 UI 입력 실패도 로컬 근거에 남겼다. 실패를 이전 화면/합성 값으로 대체하지 않았다. AVD를 no-wipe/no-snapshot-load로 다시 부팅한 뒤 기존 격리 Trip을 유지하여 이어갔다. Console은 원래 Park/0으로 복원했고 원래 Production 앱 홈을 최종 확인했다.

최종 ProviderException 방어 후 외기 재계측에서도25초 초기화 timeout1회를 보존했다. 실패 직후 취소된 SDK 초기화가 관측되었지만 원인을 단정하지 않는다. 이후 공식 Console의 bridge/DDS ready를 명시 확인한 재계측은 같은 최종 APK에서 PASS였다. timeout을 늘리거나 제품/정책을 우회하지 않았다. 회고/삭제 UI의 검증 APK는 이 예외 방어 추가 직전 빌드이며, 이후 제품 변경은 ProviderException 분류1건뿐이다. UI/규칙/모델/정상 저장 흐름의 소스 해시가 같음을 확인하고 최종 일반 APK의 crypto/외기 재계측으로 보완했다.

검증 종료 후 이번에 만든 `io.booup.roadmate.acceptance` 앱만 제거해 중복 런처 아이콘을 정리했다. 원래 Production/Demo 앱·사용자 데이터·사용자 키는 유지했고 기본 Production 화면으로 돌아갔다. 증거는 로컬 보관소에 남겼다.

## 12. Tests

| 항목 | 실제 결과 |
|---|---|
| 이전 고유 ID | 492 전부 보존, 누락0 |
| 신규 | SEC15 + OT10 + CF11 + MR10 + observation ordering3 =49 |
| 고유 / 실행 | **541 / 565** |
| model / contracts / Demo / Production | 30 /375 /100 /60 |
| failure / error / skip | **0 /0 /0** |

JUnit XML의 classname/name tuple로 중복 제거했다. 테스트 이름/내용을 지우거나 기존 경계를 완화하지 않았다. 기존 저장소 테스트는 평문 저장 fallback 대신 실제 randomized AES 테스트 provider를 명시 주입하고 domain4 bytes 검증으로 맞췄다. 테스트 provider는 test/testSupport에만 있고 제품 APK에는 없다.

## 13. Build

JDK21.0.4, Wrapper8.10.2/AGP8.8.2/Kotlin2.0.0/Compose BOM2024.04.01, min28/compile·target35/build-tools35.0.1 유지. 새 dependency/backend/DB 없음.

실제 실행한 최종 전체 명령(Windows PowerShell, 프로젝트 루트):

~~~powershell
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_USER_HOME="$env:USERPROFILE\.android"
.\gradlew.bat --gradle-user-home .gradle-user-home --console=plain --offline --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT' '-Pandroid.overridePathCheck=true' :core:model:test :core:contracts:build :app:testDemoDebugUnitTest :app:testProductionDebugUnitTest :app:assembleDemoDebug :app:assembleProductionDebug :app:assembleProductionRelease :app:lintProductionDebug :app:assembleProductionDebugAndroidTest
~~~

**최종 BUILD SUCCESSFUL,4m36s,165 tasks(145 executed)**. ProviderException 방어 직전 전체 빌드도4m11s/165 tasks(60 executed)로 통과했다. 격리 acceptance APK는 같은 공통 옵션에 `-Pphase86IsolatedAcceptance :app:assembleProductionDebug :app:assembleProductionDebugAndroidTest`로 **성공1m,64 tasks executed**. 초기 sandbox Android 사용자 경로 실패, 테스트/컴파일 실패는 보존했으며 최종 성공으로 덮어쓰지 않았다.

실제 Android 계측은 `adb -s emulator-5554 shell am instrument --user 10 -w -e mode <security|actual-migration|security-reopen|outside> io.booup.roadmate.test/io.booup.roadmate.integration.Phase86Instrumentation`을 사용했다. UI는 별도 testOnly 관측 도구로 현재 창을 읽고 실제 클릭/CarIME 입력을 했다. 과거 ClosureInstrumentation/권한 우회/루트는 실행하지 않았다.

## 14. Lint

| 실행 | 결과 |
|---|---|
| 최종 offline 전체 빌드 | Error/Fatal0,Warning16(이전 online 분석 캐시와 동일),Information0 |
| 최종 online `:app:lintProductionDebug --rerun-tasks` | BUILD SUCCESSFUL3m4s,Error/Fatal0,Warning16,Information0 |
| online 기준선 대비 | **Warning 증가0**, 기존 DataExtractionRules1/GradleDependency12/AndroidGradlePluginVersion3 유지 |

앞선 offline 실행의 Warning1은 dependency 최신 버전 검사15개가 생략된 결과이며 해결로 세지 않는다. 앞선 online 실행도2m39s/Warning16으로 통과했다. 새 AutoboxingStateCreation Information1은 `mutableDoubleStateOf`로 실제 수정했다. lint ignore/baseline/경고 억제는 추가하지 않았다. API34 callback 호출의 compileSdk35 deprecation 경고는 별도로 남는다.

## 15. APK / Manifest

Demo debug·Production debug·Production release unsigned와 별도 test APK 빌드 성공. 공개 APK3종의 SHA256/크기는 JSON에 기록했다. Production uses-permission9개(기존 SDK binding·AndroidX 자동 permission 포함), Demo는 AndroidX 자동 permission1개만 있다. 새 제품 권한은 Production 외기 읽기1개뿐이다. DO/service/location/microphone/internet/control permission 추가 없음. 실제 DEX/merged Manifest에서 Demo/계측/테스트 키·고정 문구 격리를 확인했다. 릴리즈 서명·업로드는 하지 않았다.

## 16. Security Logging Review

새 암호화/키/Comfort/recall 코드에는 payload logger가 없고 crypto 오류는 cause/message 없이 enum으로 바꾼다. 제품45개 Kotlin 파일의 setter/권한 우회 부재를 확인했다. 실제 Roadmate tag 로그 tail20,000줄 범위에서 고정 회고/기억 문구·Production/Demo key alias가 발견되지 않았다. 최초 검사678,110bytes와 최종 재검사의 hash/count는 JSON에 보존했다. raw 로그·암호문·사용자 원문은 공개하지 않았다. 이 결과가 모든 OS/provider 로그·미래 입력의 무누출 보장을 뜻하지 않는다.

## 17. Baseline Regression

main/HEAD7cdcb68 유지, staged0, Git 변경 명령 실행 없음. 이전492 test ID 전부 유지. 원본 Master/Spec·과거 보고서 본문과 기존 Gate31행 A13/B4/C8/D6 보존. Phase8 P8-16 조건부·accessibility PARTIAL·user PENDING을 승격하지 않았다.

기준 해시와 비교한543개 역사 파일에서 최신 공개 안내 문서만 의도적으로 갱신했다. Phase8.5 matrix/source/JSON3개는 재개 시 CRLF→LF 차이만 관측되어 원복하지 않았고 본문 일치로 별도 기록했다. 원전/과거 본문 변경은 없다. CareRules/DeterministicCareEngine/RuleEligibility/TripTracker/ProgressEngine/ProgressCoordinator는 기준 bytes와 같다.

필수 회귀 수정1건: 더 최신 speed callback 뒤에 늦은 GET이 도착해 motion UNKNOWN → 입력 화면 닫힘을 재현했다. 기존 AaosMotionAdapter에 sample 시각 역전 제거를 추가하고 새 외기에도 적용했다. MotionClassifier·threshold·freshness·UNKNOWN 잠금/DO 정책은 변경하지 않았으며 새3개 회귀 테스트와 실제 입력 성공으로 확인했다.

## 18. External Gates Still Closed

NIGHT/Low Beam 활성화, Location, Region/실제 Postcard, STT/TTS/AudioFocus, Warning/Seat, Vehicle Control, Navi, DO, LLM/Backend/Cloud, 연속 운전 추정, Premium payment, 외부 API/IP는 계속 닫혀 있다. 계정·실차 가용성·최종 개인정보 문안·심사 승인도 별도다. API 문서 공개가 사용 승인을 대신하지 않는다.

## 19. Product Code Changes

| 영역 | 파일 / 책임 |
|---|---|
| 보안 | EncryptedEnvelope, AndroidStoreKeys, FileTripRepository, TripStoreCodec: 인증 암호화·키 경계·원자 migration·schema4 |
| 실제 외기 | OutsideTemperatureAdapter, AaosOutsideTemperatureAdapter/Session, PleosVehicleAdapter, Production RuntimeBindings/Manifest |
| 명시 선호/회상 | LocalExperienceModels, ComfortPreferenceRepository, LocalExperience: 로컬 규칙/coordinator/현재 저장 투영 |
| UI 배선 | RoadmateViewModel, LocalExperienceContent, PostDriveContent의 RecordSettingsContent, RoadmateApp의 HomeContent, UiCopy |
| 격리/테스트 | opt-in acceptance build, 별도 Phase86Instrumentation, testSupport 실제 AES provider, SEC/OT/CF/MR/ordering tests |
| 제한된 회귀 | AaosMotionAdapter와 ObservationPolicy의 오래된 응답 제거 |

신규 SDK 클래스를 추측하지 않았다. [API·권한·출처](SDK_VERIFICATION_MATRIX.md), [아키텍처](ARCHITECTURE.md), [현재 상태](IMPLEMENTATION_STATUS.md).

## 20. Known Limitations

- 실제 차량/다른 VHAL/전원 손실/장기 수집·성능 내구성은 미검증이다. AVD 키의 하드웨어/StrongBox 보안성을 주장하지 않는다.
- 실제 사용자 키 무효화는 하지 않았다. 누락/잘못된 키/invalidated는 격리 fixture/JVM으로 검증했다. 키 손실 시 복구 기능이 없다.
- AEAD가 과거 유효 파일 전체 replay·OS/root/process 침해·플래시 forensic secure erase를 방지하지 않는다. String/GC까지 완전 메모리 소거를 보장하지 않는다.
- Legacy migration 실패 시 평문 원본을 보존하고 보안 완료로 표시하지 않는다. 모든 파일시스템/전원 장애의 원자성·내구성 인증은 아니다.
- 최종 offline 경고 감소는 dependency 검사 생략이다. Online16개·deprecated API 컴파일 경고가 남는다.
- 확대 접근성 PARTIAL, 전체 로터리/스크린리더와 새 UI fontScale1.3 종합 검수는 미실행이다.
- 월간 요약은 UTC 시작월 기준 저장 Trip 사실이다. 경계에 걸친 운행을 월별 실제 주행 시간으로 나누지 않는다.
- API28 외기 UNSUPPORTED, 실제 unsupported/denied/stale/키 무효 전환 전체를 AVD에서 강제로 만들지는 않았다. 해당 경로는 JVM 검증이다.
- 기존 Phase8 전체 Demo/시각/성능 시나리오를 이번에 모두 재실행하지 않았다. 사용자 검수/최종 정책·계정·실차·법적 승인은 남는다.

## 21. Recommendation

승인된 네 영역 구현 검증은 **PASS**, 사용자 직접 확인을 위한 **PHASE8_6_IMPLEMENTATION_COMPLETE / USER_ACCEPTANCE_PENDING**으로 둔다. 사용자는 [검수 안내](USER_ACCEPTANCE_GUIDE.md)로 기본 Production 앱을 확인한다. **Tier2 implementation NOT_STARTED / Visual redesign NOT_STARTED / Submission Freeze NOT_STARTED.**
