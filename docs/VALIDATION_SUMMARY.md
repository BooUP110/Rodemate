# 검증 요약

기준일: 2026-11-09. 아래 수치는 최근 구현 검증 시 실제 실행한 결과다. 문서 정리 자체로 앱 기능이나 승인 상태가 바뀌지 않으며 **USER_ACCEPTANCE_PENDING / 제출 준비 NOT YET**다. 원시 개발 로그/환경별 수집 자료는 공개 저장소에 포함하지 않는다.

## 빌드와 회귀

| 항목 | 결과 |
|---|---|
| JVM 테스트 | 기존484고유 보존 + 신규 UI8 = 492고유 / 516회 |
| 모듈별 실행 | model30 / contracts336 / Demo100 / Production50 |
| failure / error / skip | 0 / 0 / 0 |
| APK | Demo debug·Production debug·Production release unsigned 빌드 성공 |
| 최종 lint offline | Error/Fatal0, Warning16 |
| 최종 lint online | Error/Fatal0, Warning16 |
| 남은 경고 | DataExtractionRules1, GradleDependency12, AndroidGradlePluginVersion3 |

아이콘 누락은 수정했다. 백업/이전 정책과 의존성·AGP 버전 경고는 해결하지 않았으며 offline 모드가 경고 해결을 의미하지 않는다. 재실행 명령은 [빌드 및 실행](BUILD_AND_RUN.md)에 있다.

## 실제 AVD 확인

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
