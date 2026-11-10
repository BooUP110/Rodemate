# MVP 범위

현재 구현 범위는 실제 Production 기능과 Demo 체험을 구분한다. SDK/API 문서 존재, 계정 사용권, AVD 동작, 실차/Market 승인은 각각 별개다. **USER_ACCEPTANCE_PENDING**이며 전체 차량 Care가 활성화된 제품으로 설명하지 않는다.

| 영역 | Production 현재 상태 | Demo |
|---|---|---|
| 차량 상태 | 실제 하향등·누적 주행거리·앞좌석 왼쪽 공조 목표·AAOS 외기 온도 읽기 | 합성 값, 실제 exterior adapter/permission 없음 |
| motion / UX | AAOS 실제 관측, 서로 독립된 정책 입력 | 합성 motion/제한 |
| 하향등 P1 | 신뢰할 NIGHT 입력 부재로 자동 안내 비활성 | 합성 NIGHT와 안전 안내 |
| 기존 자동 온도 제안 | driver/confirmed preference·AC/defrost 등 필수 입력 부족으로 비활성 유지 | 기존 합성 제안 |
| 명시 정차 온도 안내 | 이 기기의 선호 설정/수정/삭제 + 실제 외기/front-left 설정을 명시 요청 시 안내. 제어 없음 | 동일 로컬 경계, 합성 입력 명시 |
| 휴식 제안 | 확인된 연속 운전 입력 부재로 비활성 | 합성 confirmed 연속 시간 |
| 실제 경고 Care | 현재 MVP 이후 범위 | 합성 분류 경고 표현 |
| 동반자 | 오리지널 임시2종과 텍스트/버튼 표현 | 선택·상호작용 시연 |
| Trip | motion 기반 시작·정차·재개·완료, 기본10초/120초 | 별도 합성 시나리오 |
| 기록·회고 | 명시 동의 후 로컬 저장·복원·삭제, 정차 후 선택 회고 | 합성 기록/회고 |
| 기억 카드 | 확인된 기록 사실과 사용자 이야기 | 가상 지역·거리 표기 가능, SIMULATED 명시 |
| 로컬 기억 회상 | 동의한 최근 기억 preview·명시 원문 열기·UTC 월간 사실 요약 | 합성 기록 기반으로 동일 개인정보/정차 경계 |
| 민감 저장 보호 | Android Keystore/AES-GCM envelope, legacy1/2/3 호환·도메인4 | 별도 UID/키/저장 경로 |
| 지역 엽서 | 실제 지역 증거 공급자 부재로 발급 비활성 | 가상 지역 엽서 |
| 시즌/팩 | 무료 로컬 예시·고정 XP·미션·보상 별도 수령 | 합성 시즌/추가 권한 체험 |
| 컬렉션 | 저장 기록 기반 정차 전용. 실제 지역 없으면 빈 상태 | 가상 지역 수집 |

## 현재 제외한 기능

실제 음성 STT/TTS·AudioFocus, 지도/경로/실제 위치, 차량 설정 제어, 외부 LLM/Cloud/백엔드/인증, 결제·Premium 구매·사진 동기화·실물 보상은 제공하지 않는다. SDK Adapter 인터페이스나 합성 성공 표시를 실제 기능 구현 완료로 세지 않는다.

## 정책과 후속 확인

- 모든 정차 상세 화면은 fresh motion·UX 허용 및 각 작업의 자격 조건을 요구한다. unknown이면 잠근다.
- DO는 미선언이다. 카테고리 후보 MOBILITY_SERVICES는 승인되지 않았으며 DO_EXTERNAL_REVIEW_REQUIRED 상태다.
- 외기 센서의 현재 AVD 일반 앱 읽기는 확인했다. 실차 정확도/다른 VHAL은 미검증이며 front-left를 driver target으로 승격하지 않는다. Seat 의미, NIGHT, continuous reset/휴식, warning 코드북, SDK 계정/심사는 추가 Gate다.
- 총거리 차분으로 Trip 거리를 만들거나, 시계로 NIGHT를 추정하거나, Trip 시간을 연속 운전 시간으로 바꾸지 않는다.
- 도담/솜솜은 교체 가능한 오리지널 임시 이름/디자인이다.
- 최종 개인정보 문안·실차·다른 VHAL/화면·Market 심사는 남아 있다.

시연 방법은 [시연 동선](SUBMISSION_DEMO_FLOW.md), 실제 검사 범위는 [검증 요약](VALIDATION_SUMMARY.md)을 참조한다.
