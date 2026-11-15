# 검증 요약

## 2026-11-15 펫 이동 도우미

기존594개 테스트를 보존하고 신규32개를 추가했다. **626개 / 651회 결과**, model30 / contracts443 / Demo117 / Production61, failure/error/skip0. `:core:model:test :core:contracts:build :app:testDemoDebugUnitTest :app:testProductionDebugUnitTest :app:assembleDemoDebug :app:assembleProductionDebug :app:assembleProductionRelease :app:lintProductionDebug`를 실제 실행하여 성공했다. 변경 없는 Gradle `UP-TO-DATE` 작업을 포함한다. 휴식 세션 저장에 별도 명시 동의가 없으면 생성하지 않는 검증을 포함한 최종 일반 빌드는5분6초다.

최종 offline lint Error/Fatal0·Warning3 (`OldTargetApi`, `GradleDependency`, `DataExtractionRules` 각1)이다. 경고를 숨기는 baseline/suppression은 추가하지 않았다. 온라인 lint는 이번에 새로 실행하지 않았다.

휴식 도달·신선도/배경/공백/시계 역행·명시 확인·저장 복원·키 실패·schema1 호환과 검색 미확인/체중 경계/이동장/날짜를 JVM에서 검증했다. 실제 AVD 범위와 미검증 항목, APK 해시는 [이번 검증 JSON](verification/pet-mobility-artifacts.json)을 따른다. [사용 흐름과 공식 정보 출처](PET_MOBILITY_FEATURES.md).

최종 동일 소스의 격리 앱 UID에서 **Demo7개·Production4개 실제 UI 시나리오**를 완료했다. 별도 저장 동의, 타이머 시작/정지/휴식 확인, 실제 검색/조건 불일치/빈 결과, 실제 Keystore 암호화 파일과 프로세스 재시작 복원을 확인했다. Production은 Console Drive/30 입력과 앱 로그의 `motion=MOVING`·`motionSource=SYSTEM`을 함께 확인하고 상세창 제거·Park 복구를 검증했다. 전체15분 만료를 AVD에서 기다리지는 않았으며 만료 로직은 JVM에서 검증했다.

환경은 Pleos2.1.0/API34, 2560×1440/density160이다. 초기 부팅·설치·관찰 도구/Console 연결 실패 후 최종 완료 실행만 집계했다. 최종 AVD는 software renderer를 사용했고2048MB 요청에도 에뮬레이터가4096MB로 올려 적용했다. 저장된 AVD 설정을 변경하거나 데이터 초기화하지 않았다. 부팅 안정성·성능·실차·새 화면의 큰 글꼴/좁은 창·전체 로터리/스크린리더 접근성은 완료로 판정하지 않는다.

최종 일반 Demo·Production debug APK의 서명을 확인하고 기존 앱에 `adb install -r`로 교체 설치했다. 원래 앱 삭제/데이터 초기화는 하지 않았고 이번 실행의 검증용 별도 앱 두 개만 제거했다. 일반 Demo 재실행 화면에서 기존 기록·XP·프로필이 유지된 것을 확인했다. 원문 이름과 입력·스크린샷은 공개 검증 JSON에 포함하지 않는다.

아래 통합/펫 모드/Phase 실행 수치는 각 이전 실행의 기록이다.

최신 통합 검증은 [공모전 시연 보완 통합](MERGE_CONTEST_POLISH.md)과 [검증 JSON](verification/contest-polish-merge.json)을 따른다. main의 반려동물 작업과 개발자 브랜치를 함께 검증했으며, 아래 펫 모드·Phase8.6 단독 실행과 구분한다.

## 2026-11-14 반려동물 모드 추가 검증

- JVM: 기존564개 모두 보존 + 신규29개, 총593개 / 618회. model30 / contracts411 / Demo116 / Production61, 실패·오류·건너뜀0.
- `:core:model:test :core:contracts:build :app:testDemoDebugUnitTest :app:testProductionDebugUnitTest :app:assembleDemoDebug :app:assembleProductionDebug :app:assembleProductionRelease :app:lintProductionDebug` 실행 성공. 기존 Wrapper/JDK21/오프라인 캐시 사용.
- 이번 Production offline lint: Error/Fatal0, Warning3 (`OldTargetApi`, `GradleDependency`, `DataExtractionRules` 각1). 이 실행에서 온라인 lint를 새로 수행하지 않았다. 아래 과거 AGP/Phase 경고 개수와 직접 비교하지 않는다.
- Demo 전체 흐름은 기존 방문 시연으로 만든 **SIMULATED Trip**을 사용했다. 별도 검증 앱 UID에서 프로필 동의, 모드 전환, 기존 시즌 접근, 장소 수정, 엽서 저장/원문 열기, 보상 수령/프레임 적용, 재시작 복원, 원본 Trip 삭제 전파, 펫 데이터 독립 삭제를 확인했다. 원래 앱 데이터는 초기화하지 않았다.
- Production AVD에서 실제 SDK/AAOS 입력에 따른 정차 등록·암호화 저장/재시작 복원, Drive/30km/h 상세 잠금, Park 복귀를 확인했다. 완료 Trip→펫 엽서 전체 흐름은 이번에 Production에서 새로 재실행하지 않았다.
- 2560×1440/density160에서 글자1.3배의 동행 수첩·패스를 열고 캡처를 확인했다. 해당 캡처 영역의 텍스트 잘림은 없었다. 더 좁은 창·전체 로터리/스크린리더 접근성은 미검증이다. 확대 설정은1.0으로 복원했다.
- 최종 Demo·Production debug는 기존 앱에 `adb install -r`로 설치했다. 기존 앱 삭제/데이터 초기화는 하지 않았고 이번 검증용 별도 앱 두 개만 제거했다. APK 해시, 실행 명령과 한계는 [펫 모드 검증 JSON](verification/pet-travel-artifacts.json)을 따른다. 실차·전체 접근성·사용자 인수는 별도다.

아래는 과거 Phase8.6 검증 기록이다.

`main` + `wkdal1433` 병합 후 재검증은 [통합 검토 기록](MERGE_PHASE86_WKDAL.md)에 별도로 기록한다. 아래 Phase 결과를 통합 결과로 재사용하지 않는다.

기준일: 2026-11-10, Phase8.6. 아래 수치는 Phase8.6 구현 완료 시점의 실제 실행 결과다. 원시 개발 로그/환경별 수집 자료는 공개 저장소에 포함하지 않는다. [전체 결과](PHASE8_6_ENHANCED_LOCAL_PLATFORM_REPORT.md)와 [검증 JSON](verification/phase8-6-artifacts.json)에 실행별 근거를 기록했다.

## 빌드와 회귀

| 항목 | 결과 |
|---|---|
| JVM 테스트 | 기존492고유 전부 보존 + 신규49 = 541고유 / 565회 |
| 모듈별 실행 | model30 / contracts375 / Demo100 / Production60 |
| failure / error / skip | 0 / 0 / 0 |
| APK | Demo debug·Production debug·Production release unsigned 빌드 성공 |
| 최종 lint offline | Error/Fatal0, Warning16, Information0; 이전 online 분석 캐시와 같은 경고 |
| 최종 lint online | Error/Fatal0, Warning16 |
| 남은 경고 | DataExtractionRules1, GradleDependency12, AndroidGradlePluginVersion3 |

Online Warning16은 기존 대비 증가0이며 DataExtractionRules1·GradleDependency12·AndroidGradlePluginVersion3을 유지한다. 앞선 offline 실행에서는 버전 검사 생략으로 Warning1만 나왔으며15개 해결로 세지 않는다. 최종 offline은 online 분석 캐시와 같은16개다. 새 Compose 상태의 AutoboxingStateCreation Information1은 mutableDoubleStateOf로 실제 수정했다. 기존 아이콘 수정도 유지한다. 백업/이전 정책과 의존성·AGP 버전 경고를 숨기거나 baseline/ignore로 제거하지 않았다. API34용 registerCallback 등의 compileSdk35 deprecation 컴파일 경고는 lint 집계와 별도다. 재실행 명령은 [빌드 및 실행](BUILD_AND_RUN.md)에 있다.

## 실제 AVD 확인

Phase8.6에서는 실제 Android Keystore fixture/기존 평문 파일 migration/force-stop reopen, SYSTEM 외기 GET·callback·reconnect와 정차 선호/회상/삭제 UI를 확인했다. 원래 앱 데이터는 보존했고, 삭제·회고 UI는 같은 Production 소스를 별도 UID로 빌드한 검증 앱에서 수행했다. 실제10/120초로 만든 Trip에 고정 테스트 문구를 입력해 암호문 평문 부재, 재시작 후 명시 기억 열기, 삭제 후 집계0·무복원을 확인했다.

P8.6-01~14 검증은 통과했다. P8.6-10 차량 write 부재는 read-only 호출/배선 감사로 확인했으며 VHAL 쓰기 카운터를 실측한 것은 아니다. P8.6-09는 실제 Drive1km/h의 UX 제한이며 moving/unknown/stale는 추가 JVM 검증이다. P8.6-14는 cold process restart이고 이번 Phase가 새 전체 AVD coldboot 인증을 부여하지 않는다. 테스트 중 종료된 AVD는 no-wipe/no-snapshot-load로 다시 부팅하고 기존 테스트 Trip을 유지했다.

최초 외기 계측의 Console 비활성 timeout, 부팅 서비스 준비 전 activity 해석 실패, 실제 UI 입력 중 늦은 speed GET으로 UNKNOWN이 된 실패를 보존했다. Console 활성·부팅 완료를 확인했고, 마지막 문제는 최신 sample보다 오래된 GET을 버리도록 고쳤다. 정책·freshness·UNKNOWN 잠금 기준을 완화하지 않았다.

아래는 **이전 Phase8에서 실행한 범위**이며 이번에 모든 시나리오를 다시 실행했다고 주장하지 않는다.

Pleos Connect2.1.0/API34, 2560×1440/density160에서 Production15개 UI 시나리오와 조건부 콜드 부팅1개, Demo8개를 실행했다.

| 범위 | 실제 확인 |
|---|---|
| Production | 실행·차량 읽기 준비·정차 콘텐츠·주행 제한·기본10/120초 Trip·짧은 정차/동일 ID 재개 |
| 기록/회고 | 동의 후 카드·실제 CarIME 입력/저장/수정·force-stop 복원·삭제 후 무복원 |
| 정책 전환 | 입력 중 IME/상세 제거, 확인창 중 제한 시 삭제 미실행 |
| 시즌/컬렉션 | 무료 로컬 시즌·실제 지역 없는 빈 컬렉션 |
| Demo | Light/HVAC/Rest/Warning/Interaction/Postcard/Season/실제 UI 보상 수령 |
| APK 격리 | Production의 합성 fixture/host 입력/계측 도구 미포함, Demo의 실제 Vehicle SDK 미포함 |

콜드 부팅은 설치 파일 sync 후 no-wipe 재연결을 확인한 조건부 결과다. 최근 설치 후 APK 경로 유실, 부팅 후 런처 준비 지연 사례가 있었으며 모든 재부팅의 설치 유지/정상 실행을 보장하지 않는다. 설치/실행 응답과 실제 화면을 함께 확인해야 한다.

## 접근성과 미검증 범위

기본 글자 크기의 홈에서 관측된 버튼은 48dp 이상이며 주요 텍스트/배경4쌍의 대비는 4.5 이상이다. 모든 색조합과 전체 화면의 접근성 인증이 아니다.

실제 앱 사용자에게 fontScale1.3이 적용된 것을 확인했지만 AVD의 창 가시성 메타데이터 문제로 확대·터치 종합 검증은 **PARTIAL**이다. 전체 로터리/스크린리더, 다른 해상도/기기, 실제 차량/다른 VHAL, 장기 수집/전원 손실 내구성은 미검증이다.

MOBILITY_SERVICES 후보·DO 외부 검토, SDK 계정 사용권, 미확인 차량 입력 계약 및 최종 법적 문안은 별도 Gate다. AVD/JVM 성공을 실차 안전성이나 Market 승인으로 확대하지 않는다.

## 다음 확인

사용자가 [검수 안내](USER_ACCEPTANCE_GUIDE.md)를 따라 실제 앱을 확인한다. 발견 문제는 재현→수정→회귀/재빌드→사용자 재검수로 처리하며 자동 테스트가 USER_ACCEPTANCE_PASS를 부여하지 않는다.
