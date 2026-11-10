# 단계와 완료 기준

현재 승인된 작업은 **Phase8.6 Enhanced Local/Platform MVP**까지다. 사용자 직접 인수는 PENDING이며 다음 단계를 자동 시작하지 않는다. 원본 명세의 단계와 후속 세부 작업 번호를 구분하며, 이 문서는 최신 운영 상태만 정리한다.

| 단계 | 범위와 선행 조건 | 완료 기준 / 현재 상태 |
|---|---|---|
| 0–6 기반 | 모델/Adapter/결정적 Care/UI/Trip/회고/무료 로컬 진행, Demo 분리 | 기존 구현 보존. 모의 데이터가 실제 연동 완료의 증거가 되지 않음 |
| 7–7.5 실제 환경 / Gate | 공식 Pleos Vehicle, AAOS motion·UX, SDK 계약과 외부 정책 점검 | 기존 Gate31행 A13/B4/C8/D6 보존. 계정·실차·Market/DO 승인은 별도 |
| 8 MVP hardening | 실제10/120초 Trip, 주행 중 상세 차단, 삭제/재시작, 기본 UI | 기존492고유/516회. cold boot P8-16 조건부, 확대 접근성 PARTIAL, 사용자 PENDING |
| 8.5 타당성 조사 | 기능별 공식 API/정책/대안 검토 | RECOVERY_PLAN_READY. 조사만으로 제품 Gate를 열지 않음 |
| 8.6 Gate0 보안 | 실제 Android Keystore와 표준 AEAD, 기존 파일 비파괴 migration | SEC01–12, encrypted reopen/tamper/key failure/deletion, 실제 AVD 키와 migration 확인 후에만 회복 기능 진행 |
| 8.6 R02 | public AAOS 외기 읽기, normal permission, 실제 sample timestamp | OT01–08, 실제 GET/callback/reconnect, source SYSTEM, unavailable/stale 숨기기 |
| 8.6 R03 | Gate0 + 실제 외기/앞좌석 왼쪽 목표 + 명시 USER_INPUT | CF01–10, 저장/수정/삭제/reopen, motion/UX/freshness 차단, 제어 경로 없음 |
| 8.6 R25 | Gate0 + 기록 동의/추가 기억 동의/항목 확인 | MR01–10, 현재 완료 Trip 사실만 요약, 명시 기억 열기, 삭제 즉시 반영/reopen |
| 사용자 검수 | Phase8.6 APK를 Pleos Emulator에서 직접 확인 | [검수 안내](USER_ACCEPTANCE_GUIDE.md). 자동 테스트가 PASS를 부여하지 않음 |
| Tier2 / Visual / 제출 Freeze | 별도 범위 합의와 미해결 Gate 검토 | **NOT_STARTED** |

공통 회귀 기준: 이전492개 테스트 ID 유지, failure/error/skip0, Demo debug·Production debug/release 빌드, online/offline lint 실제 결과 기록, Production에 Demo/계측 코드 미포함, 권한 증가는 외기 읽기1개에 한정한다. 에뮬레이터 성공을 실차/심사 승인으로 확대하지 않는다. 실행 명령은 [빌드 및 실행](BUILD_AND_RUN.md)에 있다.
