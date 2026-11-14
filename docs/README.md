# 문서 안내

현재 제품을 이해하고 빌드·시연·검수하는 데 필요한 문서입니다. 검증 기준은 Phase8.6 / 2026-11-10이며, 현재 저장소는 공모전 제출본입니다.

| 문서 | 내용 |
|---|---|
| [공모전 시연 보완 통합](MERGE_CONTEST_POLISH.md) | 효과음·콜라보 예시·release 구성과 main의 홈·시즌·반려동물 작업 병합 및 검증 |
| [MVP 범위](MVP_SCOPE.md) | 실제 활성 기능, 비활성·Demo·후속 범위 구분 |
| [빌드 및 실행](BUILD_AND_RUN.md) | 필요한 버전, Wrapper, JVM 테스트, APK 설치·실행 |
| [아키텍처](ARCHITECTURE.md) | 모듈, 데이터 흐름, SDK/정책/저장 경계 |
| [안전 및 개인정보](SAFETY_AND_PRIVACY.md) | 정차 제한, 차량 제어 경계, 동의·보존·삭제 |
| [검증 요약](VALIDATION_SUMMARY.md) | 실제 실행한 검사 결과와 미검증 범위 |
| [사용자 검수](USER_ACCEPTANCE_GUIDE.md) | Production/Demo 직접 확인 체크리스트 |
| [선택형 반려동물 동행](PET_TRAVEL_MODE.md) | 추가 모드·펫 패스·장소 수첩·엽서·메모와 삭제 범위 |
| [시연 동선](SUBMISSION_DEMO_FLOW.md) | 실제 차량 읽기와 합성 시연을 구분하는 진행 순서 |
| [현재 구현 상태](IMPLEMENTATION_STATUS.md) | 네 회복 영역의 구현/검증/미구현 구분 |
| [단계와 완료 기준](DEVELOPMENT_PHASES.md) | 보안 선행 Gate와 후속 작업 조건 |
| [SDK 검증표](SDK_VERIFICATION_MATRIX.md) | 실제 API·권한·Capability와 닫힌 Gate |
| [로컬 저장 보안](PHASE8_6_STORAGE_SECURITY.md) | Keystore·AES-GCM·키 실패·위협 범위 |
| [저장 migration](PHASE8_6_STORAGE_MIGRATION.md) | legacy1/2/3, 원자 교체와 비파괴 실패 처리 |
| [Phase8.6 결과](PHASE8_6_ENHANCED_LOCAL_PLATFORM_REPORT.md) | 실제 실행 결과, 회귀, 알려진 한계와 근거 JSON |

전체 단계별 개발 기록·내부 문의 초안·환경 조사·원시 로그/스크린샷은 로컬 개발 보관소로 분리했으며 이 저장소의 빌드 입력이 아닙니다. 공개 문서의 한계/미승인 상태는 유지합니다.
