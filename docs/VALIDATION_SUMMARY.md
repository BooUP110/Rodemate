# 검증 요약

기준일: 2026-11-10, Phase8.6. 아래 수치는 실제 실행 결과이며 **PHASE8_6_IMPLEMENTATION_COMPLETE / USER_ACCEPTANCE_PENDING / 제출 준비 NOT YET**다. 원시 개발 로그/환경별 수집 자료는 공개 저장소에 포함하지 않는다. [전체 결과](PHASE8_6_ENHANCED_LOCAL_PLATFORM_REPORT.md)와 [검증 JSON](verification/phase8-6-artifacts.json)에 실행별 근거를 기록했다.

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
