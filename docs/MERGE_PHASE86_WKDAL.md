# Phase8.6 / wkdal 통합 검토

- 기준 main: `2f1131c` — Phase8.6 secure storage and vehicle context features
- 병합 대상: `e9a8c89` — animate collab companions and share motion mockup
- 통합 브랜치: `integration/phase86-wkdal1433`
- 최종 커밋·푸시는 사용자가 수행한다.

## 통합한 동작

main의 Keystore/AES-GCM aggregate·schema1/2/3/4 호환·외기 관측·선호 온도·명시 회상·삭제/주행 제한을 유지한다. wkdal의 탐험 지도·엽서 도감·전용 패스·캐릭터 체험/애니메이션을 연결하고, 사용자의 선택에 따라 관측한 연속 주행 기반 휴식 권장과 정차 중 경고등 일회 조회도 활성화한다. 차량 제어는 추가하지 않는다.

콜라보 자료가 있을 때 게임·전용 패스를 활성화하는 기존 구조를 유지한다. 이미지/카탈로그가 없는 PC에서 대체 자료를 만들거나 활성화 조건을 바꾸지 않는다. 현재 이 PC에는 해당 Git 제외 자료가 없으므로 실제 콜라보 아트/애니메이션 검수는 남는다.

## 충돌 및 추가 조정

8개 충돌 파일에서 보안·Comfort·Recall 연결과 게임/캐릭터 연결을 함께 유지했다. Gradle8.13 배포에 남아 있던 8.10.2 체크섬을 올바른 8.13 값으로 교정했다. 검증용 앱과 내부 콜라보 옵션의 동시 사용은 차단했다.

wkdal의 평문 게임 상태는 별도 Demo 키의 암호화 sidecar로 이행한다. 기록 삭제/동의 철회 시 여행 연결 정보를 제거하고 진행 초기화도 반영한다. 원본 데이터와 키를 지우는 방식으로 병합 문제를 해결하지 않는다.

## 통합 검증

- JVM: **551개 고유 테스트 / 575회 실행**, 실패·오류·skip **0/0/0**. 기존 Phase8.6 테스트와 wkdal 테스트 및 병합 회귀 검증을 포함한다.
- Demo debug, Production debug/release, Production debug 계측 APK 빌드 성공. online 실행과 최종 offline 재실행 모두 성공했다.
- 최종 Production debug lint: 오류 **0**, 경고 **3** (`OldTargetApi`, `GradleDependency`, `DataExtractionRules`). 도구 버전이 달라 병합 전 lint 수치와 직접 비교하지 않는다.
- 실제 Pleos AVD에서 Production 저장의 Trip/Memory/진행도 개수가 설치 전후 동일함을 확인했다. 기존 파일은 이미 암호화된 상태였으며 삭제하지 않았다.
- 별도 합성 보안 fixture에서 Keystore 재오픈·평문 미노출·변조 거부·키 누락 시 교체 금지·암호화 기록 삭제를 확인했다. 이번 fixture는 이미 암호화되어 있었고, legacy 호환/migration 회귀는 JVM 테스트로 확인했다.
- 실제 외기 SYSTEM 25°C, callback timestamp 갱신, background 무효화 및 재연결을 확인했다. 정차 검증에서 자동 Care 발생이 없었다는 결과를 휴식 권장 기능의 비활성화로 해석하지 않는다. 장시간 주행 임계값의 실제 AVD/실차 검수는 별도다.
- 화면 검증: Production 외기·경고등 조회 버튼/응답·선호 설정·기록/기억 설정, Demo 탐험 지도·최근 별도 기억 화면을 확인했다. 설정을 저장하거나 사용자 기록을 삭제하는 UI 조작은 하지 않았다.
- 경고등 조회는 현재 AVD에서 “경고등 정보를 확인할 수 없어요.”로 응답했다. 조회 UI/실패 처리는 확인했지만 실제 경고등 목록 수신 성공으로 기록하지 않는다. 이를 해결하기 위해 권한이나 SDK 경계를 임의로 확대하지 않았다.
- 콜라보 원본 자료 부재로 전용 패스 아트·추가 캐릭터 애니메이션의 실제 화면 검수는 남는다. 관련 게임 동작은 JVM 회귀로 검증했다.

[기계 판독 결과](verification/phase86-wkdal-merge.json)를 함께 기록한다. 사용자 인수·실차·콜라보 로컬 자료 검수는 별도다.
