# Phase 8.5 원전 인벤토리 · 기능 복구 매트릭스

**조사 결과이며 제품 변경/완료 판정이 아니다.** [판정 보고서](PHASE8_5_MISSING_FEATURE_FEASIBILITY_REPORT.md), [외부 정책/의존성](PHASE8_5_EXTERNAL_API_POLICY_MATRIX.md), [출처](PHASE8_5_SOURCE_REGISTER.md).

## 집계와 원전 추적

원전의 독립적인 사용자 capability/UX를 48개로 묶었다. 동일 기능의 입력 API/정책 Gate를 추가 기능처럼 중복 집계하지 않는다. **ACTIVE10 / DISABLED4 / EXTERNAL3 / DEFERRED31**. ACTIVE는 아래 적힌 제한 범위만 구현되었다는 뜻이며, 항목 전체 완성·실차/Market 승인 비율이 아니다. EXTERNAL은 계정뿐 아니라 OEM 의미/입력 계약 미확정을 포함한다. `Master`만의 확장에 P0를 새로 부여하지 않는다.

기존 Phase7.5의 31개 기능+입력+정책 행은 다른 분모이며 **A13/B4/C8/D6를 그대로 보존**한다. 이번 후보 28개 또한 대안 단위이므로 위 두 집계와 더하지 않는다.

| ID | 원전 / 우선순위 | 의도·UX | Phase8 현재 상태 | 실제 구현 | 빠진 입력·사유 | Demo |
|---|---|---|---|---|---|---|
| I01 | F-01 P0 | 차량 상태와 출처 표시 | ACTIVE | Light/총거리/front-left 목표 온도 실제 읽기 | 전체 차량 신호 아님; 차종 capability | 예 |
| I02 | F-01 P0 | motion·주행 제한에 맞는 콘텐츠 | ACTIVE | AAOS motion/UX·unknown 잠금 | DO/실차 승인 별도 | 예 |
| I03 | F-02 P0 | 야간 미점등 P1 | DISABLED | 결정적 LightRule 존재 | 제품 NIGHT provider 없음; probe만 성공 | 예 |
| I04 | F-03 P0 | 외기·driver target 수집 | EXTERNAL | front-left target만 연결 | 외기 신규 연결 및 driver 위치 의미 필요 | 예 |
| I05 | F-03 P0 | 개인화 온도 제안 | DISABLED | HvacRule 존재 | outside/driver/confirmed preference/AC·defrost 부족 | 예 |
| I06 | F-03 P0 | 운전자 존재·개인 구분 | EXTERNAL | Seat 제거/미확인 | 과거 Boolean 의미 충돌; occupant≠identity | 예 |
| I07 | F-03 P0 / Master | 온도 선호 학습·수동 프리셋 | DEFERRED | 자동 학습·실사용 프리셋 없음 | 명시 확인/공유 차량/취소 UX 필요 | 제한적 합성 선호 |
| I08 | F-04 P0 | 연속 운전 의미와 시간 | EXTERNAL | Trip와 별도 계약 | 공식 reset/rest/gap 의미·provider 미확인 | 예 |
| I09 | F-04 P0 | 장거리 휴식 권고 | DISABLED | RestRule 존재 | confirmed continuous 입력 없음 | 예 |
| I10 | F-04 P0 / Master | 인근 휴게소 검색·실제 휴식 기록 | DEFERRED | 없음 | 위치/POI/방향/휴식의 명시 의미 필요 | 휴식 제안만 |
| I11 | F-05 P0 | 경고 감지·공식 설명 | DEFERRED | 엔진/공개 washer cap 진단 | 현재 cap UNSUPPORTED; 코드북 없음 | 예 |
| I12 | F-05 P0 / Master | 경고 이력·정비소 연결 | DEFERRED | 실제 warning history 없음 | 검증 payload/설명/POI/내비 필요 | 합성 경고 |
| I13 | F-06 P0 | 오리지널 2종·동일 케어 표현 | ACTIVE | 임시 2종/공통 Presenter | 최종 아트/명칭·음성 확정 전 | 예 |
| I14 | F-06/F-10 P0 / Master | 팩·테마·획득 외형 적용 | DEFERRED | 소유/보상 기록과 기본 선택기 | 보유 팩→표현 적용 전체 연결 없음 | 소유 기록 |
| I15 | F-06 P0 / Master | 감정·애니메이션·표현 확장 | DEFERRED | 정적 Canvas/텍스트 | 정책·성능 검증된 애니메이션 세트 없음 | 정적 |
| I16 | F-07 P0 목표 | 음성 인식·명시 승인 | DEFERRED | 텍스트/버튼 대체 | 실제 STT/ko-KR/녹음 동의 미구현 | 텍스트만 |
| I17 | F-07 P0 목표 | 음성 안내와 AudioFocus | DEFERRED | AudioAdapter 경계 | 재생/중단/focus 없음; Android TTS probe 실패 | 텍스트만 |
| I18 | F-07/F-13 / Master | Gleo 앱 기능 호출 | DEFERRED | 없음 | schema 등록·호출 정책/승인 필요 | 아니오 |
| I19 | F-08 P0 | 자동 Trip 시작·재개·종료 | ACTIVE | 실제 motion→Trip lifecycle | 거리/연속 운전 아님 | 예 |
| I20 | F-08 P0 | 로컬 동의·복원·삭제 | ACTIVE | schema3 앱 내부 저장 | 민감정보 앱 암호화 미구현 신규 검토사항 | 예 |
| I21 | F-08 P0 / Master | 출발·도착·거리·실제 경로 | DEFERRED | 실제 값 없는 항목 생략 | 위치/Trip 전용 거리 없음; total 차분 금지 | 합성 label |
| I22 | F-08 P0 | 발생한 케어 이벤트 기록 | ACTIVE | Trip care occurrence 저장/투영 경계 | Production 실제 경고·케어 입력은 비활성; 빈 로그 정상 | 합성 이벤트 |
| I23 | F-09 P0 | 확인된 사실의 기억 카드 | ACTIVE | 결정적 로컬 카드 | 지역·날씨·거리/생성형 AI 없음 | 예 |
| I24 | F-08/F-13 | 선택 회고·추가 기억 동의 | ACTIVE | 별도 동의/항목 확인·로컬 저장 | 대화에서 기억을 꺼내는 기능과 다름 | 예 |
| I25 | F-13 P1/P2 / Master | 장기 기억 검색·과거 대화 연결 | DEFERRED | 저장만 존재 | 검색/회상 정책·민감 표현·삭제 반영 필요 | 아니오 |
| I26 | F-09 P0 | 실제 최초 지역 방문 엽서 | DISABLED | 중복 방지/발급 엔진 존재 | 신뢰 위치→region evidence 없음 | 예 |
| I27 | F-09/F-10 P0 | 엽서·컬렉션 목록/보유 수 | ACTIVE | 정차 목록/무료 보유 기록 | 실제 지역 발급과 별개; Production 비어 있음 | 가상 지역 |
| I28 | F-09/F-13 / Master | 재방문 이야기·스티커·공유 앨범 | DEFERRED | 기본 카드/목록만 | 실제 지역/재방문 의미·공유 권한 필요 | 일부 카드 |
| I29 | F-11 P1 | Fog/격자·지역 탐험 진행 | DEFERRED | 발견 수 기반 목록 | 실제 위치 경로·grid/경계 데이터 없음 | 가상 목록 |
| I30 | F-11 P1 | 실제 지도·tile·polyline·pin | DEFERRED | 없음 | renderer≠tile≠route; 데이터 라이선스/성능 필요 | 아니오 |
| I31 | F-10 P0 | 안전한 무료 XP·미션·수령 | ACTIVE | 고정 XP/원장/로컬 시즌 | 예시 시즌이며 실제 상품/유료 운영 아님 | 예 |
| I32 | F-10 P0 / Master | 보상 꾸미기·배경 적용 | DEFERRED | 보상 소유 기록 | 적용·복원·권한 검사 미연결 | 일부 소유 |
| I33 | F-10 P0 / Master | 숨김 미션·세트·과거 시즌 아카이브 | DEFERRED | 기본 미션/소유 모델 | 전체 운영 콘텐츠/규칙 UX 없음 | 기본 시즌 |
| I34 | F-12 P1/P2 | HVAC·seat·window·light 제어 | DEFERRED | 미실행 intent 경계 | 권한/사용자 승인/Capability/결과 확인 없음 | 제어 없음 |
| I35 | F-13 P1/P2 | 컨텍스트 LLM 자유 대화·말투 | DEFERRED | 로컬 고정 대사 | 공급자/필터/동의/비밀 키 처리 미설계 | 아니오 |
| I36 | F-13 / Master | 월간 이야기·장기 요약 | DEFERRED | 개별 Trip 카드 | 기간 통계/기억 선택 UX 없음; LLM 필수 아님 | 아니오 |
| I37 | F-13 P1/P2 | 백엔드·클라우드 기억·동기화 | DEFERRED | 없음 | 인증/삭제 전파/보관/운영 비용 | 아니오 |
| I38 | F-14 P2 | iOS/Android 사진과 모바일 연결 | DEFERRED | 없음 | 사진 선택·쌍방 인증·EXIF·삭제 필요 | 아니오 |
| I39 | F-13/F-14 / Master | 개인/가족/아동 프로필·공유 차량 | DEFERRED | 없음 | 신원/세션/아동 동의; Seat는 인증 아님 | 아니오 |
| I40 | F-14 P2 | Premium 결제·entitlement | DEFERRED | Premium false | 결제 정책/스토어 SDK/영수증 확인 필요 | 실구매 없음 |
| I41 | F-14 / Master | 멀티 패스·공유 XP | DEFERRED | 없음 | 계정/동기화/공정성·어뷰징 방지 | 아니오 |
| I42 | F-14 / Master | 직접 팩 구매·RP·교환 상점 | DEFERRED | 없음 | 사업 정책/재화 원장/구매 복원 | 아니오 |
| I43 | F-14 P2 / Master | 실물 보상·가챠·제휴 | DEFERRED | 없음 | 사업 계약/배송 PII/확률·운영 | 아니오 |
| I44 | F-06/F-14 / Master | 외부 IP 라이선스 캐릭터 | DEFERRED | 오리지널 임시 UI만 | 본 세션 외부 캐릭터 금지; 장기 권리 계획만 유지 | 아니오 |
| I45 | Master 확장 | EV충전·타이어·와이퍼·정비 예약 | DEFERRED | 없음 | 신호별 공개 API·코드북·외부 예약 계약 | 아니오 |
| I46 | F-08 / Master | 출퇴근/여행 자동 분류·통계 | DEFERRED | 기본 기록만 | 사용자 정의/위치 최소화·오분류 설명 | 아니오 |
| I47 | F-04/F-11 / Master | 내비 경유지·동선상의 휴게소 | DEFERRED | NavigationAdapter 경계 | route geometry/방향/실제 실행 결과 필요 | 아니오 |
| I48 | F-06/F-07 / Master | 캐릭터 음색·호칭·음성 취향 | DEFERRED | 기본 텍스트 선택 | 녹음/voice 권리·사용자 확인 필요 | 텍스트 톤만 |

## 기능별 복구 대안

Tier1 제출 전 권장 / Tier2 여유 시·선행 Gate 통과 후 / Tier3 외부 Gate / Tier4 post-MVP / Tier5 제외. Feasibility는 기능 전체의 검토 판정이며 아티팩트 다운로드를 제품 활성화로 세지 않는다. `RECOVERABLE_NOW`도 새 구현과 테스트가 남은 상태다.

점수는 **차량 고유성 V / 정책 위험 R / 시연 효과 D**, 각1–5의 엔지니어 판단이다. 높은 R은 우선 구현 신호가 아니다. 위험·확인 비용을 제외한 점수 합으로 자동 순위를 만들지 않는다. Privacy P0–P7은 정책 문서의 전송/보관 프로파일을 전부 포함한다.

| ID | Feature | Feasibility | Tier | V/R/D | 권장 판단 |
|---|---|---|---|---|---|
| R01 | NIGHT→Light Care | INSUFFICIENT_EVIDENCE | 2 | 5/4/5 | reader 후보 성립. timestamp 계약/전환 검증 전 P1 활성화 금지 |
| R02 | 외기 온도 read | RECOVERABLE_NOW | 1 | 5/1/4 | 먼저 read-only 연결; freshness/error/capability/누수 검증. HVAC 자동 활성화 금지 |
| R03 | 명시 선호·정차 comfort 안내 | RECOVERABLE_NOW | 1 | 4/2/4 | 사용자가 정차 중 요청한 일반 조절 안내로 별도 규칙 정의; 기존 자동 HvacRule 조건을 삭제하지 않음 |
| R04 | Seat / driverPresent | OEM_DEPENDENT | 3 | 5/4/2 | 반전·임의 참값 금지; Q1 재현 결과와 sample 계약 요청 |
| R05 | Android 실제 위치 | INSUFFICIENT_EVIDENCE | 2 | 4/3/5 | 사용자 범위 합의 후 일반 앱 foreground probe; mock/age/accuracy/권한 취소 포함 |
| R06 | 로컬 region / 실제 엽서 | INSUFFICIENT_EVIDENCE | 2 | 3/2/5 | 시군구/시도 단위 고정부터; WGS84↔UTM-K 검증·경계 모호하면 미발급 |
| R07 | 외부 reverse geocoding | RECOVERABLE_WITH_EXTERNAL_SERVICE | 3 | 2/3/5 | 지역명 생성은 허용된 응답만; 캐시권 미확정 상태로 offline data라고 주장 금지 |
| R08 | 정차 탐험 도식/앨범 | RECOVERABLE_NOW | 2 | 2/1/4 | 확인된 방문만 색칠; 지역 0건은 빈 상태. 지도 복구 완료로 계산하지 않음 |
| R09 | 실제 지도·tile·polyline | POST_MVP_COMPLEXITY | 4 | 2/3/5 | POM Kotlin2.2.10/coroutines1.10.2 대 현재 Kotlin2.0.0; compile/AGP/API34 회귀 전 채택 금지 |
| R10 | POI / 휴게소 검색 | RECOVERABLE_WITH_EXTERNAL_SERVICE | 3 | 4/3/4 | 휴식 필요 판단→장소 검색→동선 필터→경유지 승인 네 단계 분리 |
| R11 | 앱 관측 운전 추정 | RECOVERABLE_NOW | 2 | 5/3/5 | APP_OBSERVED_DRIVING_ESTIMATE 신설; Trip 시간/total km 차분 전용 금지, gap 시 unknown·동의한 reset만 |
| R12 | 권위 있는 연속 운전 시간 | OEM_DEPENDENT | 3 | 5/3/3 | Q5 공식 정의가 없으면 confirmed continuous로 승격 금지 |
| R13 | STT | INSUFFICIENT_EVIDENCE | 3 | 3/4/4 | service 존재만 PASS 금지; ON_DEVICE 문서와 실제 계정/모델 구분 |
| R14 | Pleos / Android TTS | RECOVERABLE_WITH_PLEOS_ACCOUNT | 3 | 3/3/5 | 계정 제공 상태/라이선스 확인 후 SDK probe; Android TTS가 즉시 대체된다고 주장 금지 |
| R15 | 사전 음성 + AudioFocus | RECOVERABLE_NOW | 2 | 3/2/5 | 150–200ms pre-roll 후보; focus 거절 시 텍스트, 보류된 음성 뒤늦게 재생 금지 |
| R16 | 외부 LLM | RECOVERABLE_WITH_EXTERNAL_SERVICE | 4 | 2/5/5 | 선택 정차 회고 표현만; SafetyText/우선순위/XP/차량 제어 금지 |
| R17 | Backend / cloud | POST_MVP_COMPLEXITY | 4 | 1/4/3 | 이번 제출 안에 서버 추가 비추천; 필요 확정 시만 별도 범위 |
| R18 | Warning / codebook | OEM_DEPENDENT | 3 | 5/5/5 | aggregate getter가 public이어도 private cap/listener 경로를 대안으로 사용 금지 |
| R19 | 차량 제어 | OEM_DEPENDENT | 3 | 5/5/5 | ControlGateway disabled 유지; 승인 버튼을 성공으로 표시 금지 |
| R20 | NaviHelper 경유지 | RECOVERABLE_WITH_POLICY_APPROVAL | 3 | 5/4/4 | State/Navi 신청 불필요 안내를 인정하되 자동 경유지 추가 허용으로 해석 금지 |
| R21 | 사진 / 모바일 Companion | POST_MVP_COMPLEXITY | 4 | 1/4/4 | 선택된 사진만; 차에서 전체 photo library/메타데이터 권한 요구 금지 |
| R22 | Premium / RP / 결제 | POST_MVP_COMPLEXITY | 4 | 1/5/3 | 이번 제출은 무료; safety/우선순위는 결제와 항상 동일 |
| R23 | 외부 IP 캐릭터 | NOT_RECOMMENDED | 5 | 1/5/3 | 상용 IP 추가 제외. 도담/솜솜 임시 에셋 확정만 별도 |
| R24 | DO / 주행 Care | RECOVERABLE_WITH_POLICY_APPROVAL | 3 | 5/5/5 | 정차 MVP 유지; 짧은 운전 관련 안내만 구체 UX로 승인 요청, 임의 DO 선언 금지 |
| R25 | 로컬 기억 회상·기간 요약 | RECOVERABLE_NOW | 1 | 2/2/4 | 날조 없는 한 건 회상/월간 사실 요약; 민감한 원문 자동 낭독 제외 |
| R26 | 무료 보상 외형 적용 | RECOVERABLE_NOW | 2 | 1/1/4 | 보유→미리보기→적용→복원; 제어/안전/XP를 외형에 연결 금지 |
| R27 | 민감 로컬 저장 암호화 | RECOVERABLE_NOW | 1 | 3/2/2 | 암호화 envelope/기존 schema migration/원자적 저장·rollback을 먼저 설계; 데이터 삭제 우회 금지 |
| R28 | Cloud 한국어 STT/TTS | RECOVERABLE_WITH_EXTERNAL_SERVICE | 4 | 2/4/4 | cloud를 숨긴 SDK 대체로 추가 금지; 별도 scope/동의/요금 승인 후 |

### R01 — NIGHT→Light Care

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-02 P0 |
| Current State | P1 disabled; 기존 입력 Gate 미확정 |
| AAOS Alternative | NIGHT_MODE Boolean/global/ON_CHANGE |
| Pleos Alternative | 공개 Light 상태만으로 야간을 증명할 수 없음 |
| Android Alternative | 표준 CarPropertyManager |
| External API / Local Data | 위치+천문/일몰은 DERIVED 별도 의미 |
| Backend Need | 불필요 |
| Permission | CAR_EXTERIOR_ENVIRONMENT normal |
| Policy Constraint | 저조도 의미·DO 별도 심사 |
| AVD Verification | 일반 UID get=false / initial callback만 |
| Real Vehicle Verification | 암소/명소·tunnel·차종·재연결 필수 |
| Privacy / Data | P0 — 정책 문서 프로파일 참조 |
| Network | 로컬 가능 |
| Cost | API 종량 없음 |
| Product / Contest Value | 차량 Care 복구의 중심 |
| Feasibility | INSUFFICIENT_EVIDENCE |
| Priority Tier | 2 |
| V / R / D | 5/4/5 |
| Recommendation | reader 후보 성립. timestamp 계약/전환 검증 전 P1 활성화 금지 |
| Official Evidence | [S01](PHASE8_5_SOURCE_REGISTER.md#S01), [S33](PHASE8_5_SOURCE_REGISTER.md#S33), [S34](PHASE8_5_SOURCE_REGISTER.md#S34), [S45](PHASE8_5_SOURCE_REGISTER.md#S45) |
| Product Value | High |
| Contest Value | High |
| Vehicle Specificity | High |
| Implementation Cost | Medium |
| Policy Risk | High |
| Demo Impact | High |

### R02 — 외기 온도 read

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-01/F-03 P0 |
| Current State | 제품 provider 없음 |
| AAOS Alternative | ENV_OUTSIDE_TEMPERATURE Float°C/global |
| Pleos Alternative | Vehicle HVAC와 표준 AAOS 조합 |
| Android Alternative | CarPropertyManager 공개 읽기 |
| External API / Local Data | 날씨 forecast는 차량 센서 대체 불가 |
| Backend Need | 불필요 |
| Permission | android.car.permission.CAR_EXTERIOR_ENVIRONMENT normal |
| Policy Constraint | 최소 권한/출처 표시; 배포 별도 |
| AVD Verification | 25°C get+fresh callbacks PASS |
| Real Vehicle Verification | 정확도/차종/단위 재검증 |
| Privacy / Data | P0 — 정책 문서 프로파일 참조 |
| Network | 불필요 |
| Cost | API 종량 없음 |
| Product / Contest Value | 실제 차량 컨텍스트 즉시 보강 |
| Feasibility | RECOVERABLE_NOW |
| Priority Tier | 1 |
| V / R / D | 5/1/4 |
| Recommendation | 먼저 read-only 연결; freshness/error/capability/누수 검증. HVAC 자동 활성화 금지 |
| Official Evidence | [S01](PHASE8_5_SOURCE_REGISTER.md#S01), [S33](PHASE8_5_SOURCE_REGISTER.md#S33), [S34](PHASE8_5_SOURCE_REGISTER.md#S34) |
| Product Value | High |
| Contest Value | High |
| Vehicle Specificity | High |
| Implementation Cost | Low |
| Policy Risk | Low |
| Demo Impact | High |

### R03 — 명시 선호·정차 comfort 안내

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-03 P0 의도 재설계 |
| Current State | 기존 HVAC disabled |
| AAOS Alternative | 외기와 front-left target 관측 |
| Pleos Alternative | 좌석 운전자 의미 미확인 유지 |
| Android Alternative | 정차 수동 선호 확인 가능 |
| External API / Local Data | 불필요 |
| Backend Need | 불필요 |
| Permission | 외기 read 이외 없음 |
| Policy Constraint | 주행/unknown 닫기; driver identity 주장 금지 |
| AVD Verification | 새 UX/규칙 NOT_RUN |
| Real Vehicle Verification | LHD/RHD 영역 라벨 검토 |
| Privacy / Data | P0 — 정책 문서 프로파일 참조 |
| Network | 불필요 |
| Cost | 로컬 |
| Product / Contest Value | 제어 없이 개인화 가치 |
| Feasibility | RECOVERABLE_NOW |
| Priority Tier | 1 |
| V / R / D | 4/2/4 |
| Recommendation | 사용자가 정차 중 요청한 일반 조절 안내로 별도 규칙 정의; 기존 자동 HvacRule 조건을 삭제하지 않음 |
| Official Evidence | [S01](PHASE8_5_SOURCE_REGISTER.md#S01), [S08](PHASE8_5_SOURCE_REGISTER.md#S08), [S12](PHASE8_5_SOURCE_REGISTER.md#S12) |
| Product Value | High |
| Contest Value | High |
| Vehicle Specificity | High |
| Implementation Cost | Medium |
| Policy Risk | Low |
| Demo Impact | High |

### R04 — Seat / driverPresent

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-03 P0 |
| Current State | 의미 충돌로 제거 |
| AAOS Alternative | SEAT_OCCUPANCY/INFO_DRIVER_SEAT 후보; API34 별도 |
| Pleos Alternative | Seat Boolean 과거 Console 반대 |
| Android Alternative | foreground/ignition/motion 대체 불가 |
| External API / Local Data | 사용자 선택은 USER_INPUT일 뿐 occupancy 아님 |
| Backend Need | 불필요 |
| Permission | Pleos pleos.car.permission.CAR_SEATS; AAOS 버전별 권한 미확정 |
| Policy Constraint | Vehicle permission 사전 승인 |
| AVD Verification | 과거 의미 충돌 보존; 신규 읽기 없음 |
| Real Vehicle Verification | OEM 의미/권역/좌석 배열 필수 |
| Privacy / Data | P0 — 정책 문서 프로파일 참조 |
| Network | 불필요 |
| Cost | 종량 없음/승인 비용 UNKNOWN |
| Product / Contest Value | 자동 개인화 선행 입력 |
| Feasibility | OEM_DEPENDENT |
| Priority Tier | 3 |
| V / R / D | 5/4/2 |
| Recommendation | 반전·임의 참값 금지; Q1 재현 결과와 sample 계약 요청 |
| Official Evidence | [S10](PHASE8_5_SOURCE_REGISTER.md#S10), [S32](PHASE8_5_SOURCE_REGISTER.md#S32), [S33](PHASE8_5_SOURCE_REGISTER.md#S33) |
| Product Value | High |
| Contest Value | Medium |
| Vehicle Specificity | High |
| Implementation Cost | High |
| Policy Risk | High |
| Demo Impact | Low |

### R05 — Android 실제 위치

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-09/F-11 입력 |
| Current State | 없음 |
| AAOS Alternative | 표준 위치 경로 우선 검토 |
| Pleos Alternative | Fused1.1.3와 대안; GMS 아님 |
| Android Alternative | LocationManager foreground fresh fix |
| External API / Local Data | 불필요 |
| Backend Need | 불필요 |
| Permission | fine/coarse dangerous; background 이번 제외 |
| Policy Constraint | 목적/위치 동의·약관·차종 접근 정책 |
| AVD Verification | gps/fused/passive 설치만; fix NOT_TESTED |
| Real Vehicle Verification | 실내·GPS 단절·차종/정확도 테스트 |
| Privacy / Data | P1 — 정책 문서 프로파일 참조 |
| Network | provider에 따라 상이; offline 보장 안 됨 |
| Cost | 플랫폼 API 종량 없음 |
| Product / Contest Value | 엽서·지역의 가장 큰 선행 조건 |
| Feasibility | INSUFFICIENT_EVIDENCE |
| Priority Tier | 2 |
| V / R / D | 4/3/5 |
| Recommendation | 사용자 범위 합의 후 일반 앱 foreground probe; mock/age/accuracy/권한 취소 포함 |
| Official Evidence | [S01](PHASE8_5_SOURCE_REGISTER.md#S01), [S02](PHASE8_5_SOURCE_REGISTER.md#S02), [S11](PHASE8_5_SOURCE_REGISTER.md#S11), [S13](PHASE8_5_SOURCE_REGISTER.md#S13), [S35](PHASE8_5_SOURCE_REGISTER.md#S35) |
| Product Value | High |
| Contest Value | High |
| Vehicle Specificity | High |
| Implementation Cost | Medium |
| Policy Risk | Medium |
| Demo Impact | High |

### R06 — 로컬 region / 실제 엽서

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-09 P0 |
| Current State | 실제 발급 disabled |
| AAOS Alternative | 위치 입력 필요 |
| Pleos Alternative | Fused optional |
| Android Alternative | 순수 Kotlin point-in-polygon/경계 매핑 후보 |
| External API / Local Data | 허가된 고정 경계 파일 |
| Backend Need | 불필요 |
| Permission | 위치 권한은 R05; 데이터 권리 별도 |
| Policy Constraint | 동의/CRS/데이터 버전·삭제 |
| AVD Verification | 지역 resolver 신규 NOT_RUN |
| Real Vehicle Verification | 경계/정확도/중복·국경 검증 |
| Privacy / Data | P1 — 정책 문서 프로파일 참조 |
| Network | 데이터 포함 후 offline 가능 |
| Cost | 경계 권리/전처리 비용 UNKNOWN |
| Product / Contest Value | 개인정보 외부 전송 없이 정체성 회복 |
| Feasibility | INSUFFICIENT_EVIDENCE |
| Priority Tier | 2 |
| V / R / D | 3/2/5 |
| Recommendation | 시군구/시도 단위 고정부터; WGS84↔UTM-K 검증·경계 모호하면 미발급 |
| Official Evidence | [S50](PHASE8_5_SOURCE_REGISTER.md#S50), [S51](PHASE8_5_SOURCE_REGISTER.md#S51) |
| Product Value | High |
| Contest Value | High |
| Vehicle Specificity | Medium |
| Implementation Cost | Medium |
| Policy Risk | Low |
| Demo Impact | High |

### R07 — 외부 reverse geocoding

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-09 P0 |
| Current State | 없음 |
| AAOS Alternative | 위치 원천 별도 |
| Pleos Alternative | Fused는 주소 서비스 아님 |
| Android Alternative | HTTPS client 후보 |
| External API / Local Data | Kakao coord2regioncode REST |
| Backend Need | REST key 보호 proxy 권장 |
| Permission | INTERNET + 위치 권한/동의 |
| Policy Constraint | 자동차 임베디드/캐시/재배포 조건 확인 |
| AVD Verification | 문서만; 실호출 NOT_RUN |
| Real Vehicle Verification | 한국 행정구역 정확성 실측 필요 |
| Privacy / Data | P2 — 정책 문서 프로파일 참조 |
| Network | 필요; 실패 시 지역 미상 |
| Cost | 일100k 조건/초과0.5원·재조회 |
| Product / Contest Value | 로컬 경계 확보보다 빠를 수 있음 |
| Feasibility | RECOVERABLE_WITH_EXTERNAL_SERVICE |
| Priority Tier | 3 |
| V / R / D | 2/3/5 |
| Recommendation | 지역명 생성은 허용된 응답만; 캐시권 미확정 상태로 offline data라고 주장 금지 |
| Official Evidence | [S40](PHASE8_5_SOURCE_REGISTER.md#S40), [S41](PHASE8_5_SOURCE_REGISTER.md#S41), [S42](PHASE8_5_SOURCE_REGISTER.md#S42) |
| Product Value | High |
| Contest Value | Medium |
| Vehicle Specificity | Low |
| Implementation Cost | Medium |
| Policy Risk | Medium |
| Demo Impact | High |

### R08 — 정차 탐험 도식/앨범

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-09/F-11 의도 일부 |
| Current State | 목록/발견 수 있음 |
| AAOS Alternative | 새 차량 API 불필요 |
| Pleos Alternative | 불필요 |
| Android Alternative | 기존 Compose Canvas |
| External API / Local Data | 오리지널 도식; 실제 지도 아님 |
| Backend Need | 불필요 |
| Permission | 없음 |
| Policy Constraint | 정차 한정; 허위 방문/지도 라벨 금지 |
| AVD Verification | 새 표현 NOT_RUN |
| Real Vehicle Verification | 차량별 UI 가독성 |
| Privacy / Data | P0 — 정책 문서 프로파일 참조 |
| Network | 불필요 |
| Cost | 로컬 |
| Product / Contest Value | 빈 컬렉션과 지도의 중간 가치 |
| Feasibility | RECOVERABLE_NOW |
| Priority Tier | 2 |
| V / R / D | 2/1/4 |
| Recommendation | 확인된 방문만 색칠; 지역 0건은 빈 상태. 지도 복구 완료로 계산하지 않음 |
| Official Evidence | [S08](PHASE8_5_SOURCE_REGISTER.md#S08), [S15](PHASE8_5_SOURCE_REGISTER.md#S15) |
| Product Value | Medium |
| Contest Value | Medium |
| Vehicle Specificity | Low |
| Implementation Cost | Low |
| Policy Risk | Low |
| Demo Impact | High |

### R09 — 실제 지도·tile·polyline

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-11 P1 |
| Current State | 없음 |
| AAOS Alternative | 위치/route 별도 |
| Pleos Alternative | Navi custom screen은 외부 renderer와 다름 |
| Android Alternative | MapLibre13.7.1 후보 |
| External API / Local Data | tile/style/font/POI 별도 라이선스 |
| Backend Need | tile hosting/cache optional |
| Permission | AAR가 location/network 권한 병합; 전부 감사 필요 |
| Policy Constraint | 귀속/캐시·주행 pan/zoom 제한 |
| AVD Verification | POM/AAR만; 렌더 NOT_RUN |
| Real Vehicle Verification | GPU/메모리/다중 화면 테스트 |
| Privacy / Data | P3 — 정책 문서 프로파일 참조 |
| Network | offline tile 허가 시만 |
| Cost | renderer BSD; tile는 별도 |
| Product / Contest Value | 시각 임팩트 높으나 현재 toolchain 갈등 |
| Feasibility | POST_MVP_COMPLEXITY |
| Priority Tier | 4 |
| V / R / D | 2/3/5 |
| Recommendation | POM Kotlin2.2.10/coroutines1.10.2 대 현재 Kotlin2.0.0; compile/AGP/API34 회귀 전 채택 금지 |
| Official Evidence | [S46](PHASE8_5_SOURCE_REGISTER.md#S46), [S47](PHASE8_5_SOURCE_REGISTER.md#S47), [S48](PHASE8_5_SOURCE_REGISTER.md#S48) |
| Product Value | Medium |
| Contest Value | High |
| Vehicle Specificity | Low |
| Implementation Cost | High |
| Policy Risk | Medium |
| Demo Impact | High |

### R10 — POI / 휴게소 검색

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-04/F-11 |
| Current State | 없음 |
| AAOS Alternative | 차량 데이터는 후보 위치 입력 |
| Pleos Alternative | Navi는 검색 DB가 아님 |
| Android Alternative | 공식 HTTPS REST |
| External API / Local Data | Kakao keyword/category; 허가된 휴게소 CSV 후보 |
| Backend Need | secret proxy/쿼터 제한 |
| Permission | INTERNET/위치; 내비 permission 별도 |
| Policy Constraint | 방향·진입로·영업 정보 확정 못하면 추천 보류 |
| AVD Verification | 검색/경로 테스트 NOT_RUN |
| Real Vehicle Verification | 반대 차선/폐점/접근성 검증 |
| Privacy / Data | P2 — 정책 문서 프로파일 참조 |
| Network | 캐시 허가 시만 제한 offline |
| Cost | Kakao 일100k 조건/초과2원·재조회 |
| Product / Contest Value | 휴식 제안의 행동 가능성 |
| Feasibility | RECOVERABLE_WITH_EXTERNAL_SERVICE |
| Priority Tier | 3 |
| V / R / D | 4/3/4 |
| Recommendation | 휴식 필요 판단→장소 검색→동선 필터→경유지 승인 네 단계 분리 |
| Official Evidence | [S20](PHASE8_5_SOURCE_REGISTER.md#S20), [S40](PHASE8_5_SOURCE_REGISTER.md#S40), [S41](PHASE8_5_SOURCE_REGISTER.md#S41), [S42](PHASE8_5_SOURCE_REGISTER.md#S42) |
| Product Value | High |
| Contest Value | High |
| Vehicle Specificity | High |
| Implementation Cost | High |
| Policy Risk | Medium |
| Demo Impact | High |

### R11 — 앱 관측 운전 추정

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-04 P0 검증 가능한 앱 타이머 허용 |
| Current State | 현재 Trip만; Rest disabled |
| AAOS Alternative | fresh speed/gear/UX |
| Pleos Alternative | 공식 continuous와 별도 |
| Android Alternative | 독립 elapsed clock/coverage 모델 |
| External API / Local Data | 불필요 |
| Backend Need | 불필요 |
| Permission | 기존 motion 읽기 |
| Policy Constraint | 운전자 동일/실제 휴식/피로 진단 주장 금지 |
| AVD Verification | 새 모델 NOT_RUN |
| Real Vehicle Verification | 전원·프로세스·운전자 교체 한계 |
| Privacy / Data | P0 — 정책 문서 프로파일 참조 |
| Network | 불필요 |
| Cost | 로컬 |
| Product / Contest Value | 안전한 휴식 안내의 현실적 복구 |
| Feasibility | RECOVERABLE_NOW |
| Priority Tier | 2 |
| V / R / D | 5/3/5 |
| Recommendation | APP_OBSERVED_DRIVING_ESTIMATE 신설; Trip 시간/total km 차분 전용 금지, gap 시 unknown·동의한 reset만 |
| Official Evidence | [S33](PHASE8_5_SOURCE_REGISTER.md#S33), [S34](PHASE8_5_SOURCE_REGISTER.md#S34) |
| Product Value | High |
| Contest Value | High |
| Vehicle Specificity | High |
| Implementation Cost | Medium |
| Policy Risk | Medium |
| Demo Impact | High |

### R12 — 권위 있는 연속 운전 시간

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-04 P0 |
| Current State | 공개 Trip time cap unsupported 과거 |
| AAOS Alternative | 임의 표준 total-time 대체 불가 |
| Pleos Alternative | Odometer Trip time≠휴식 reset 보증 |
| Android Alternative | 앱 추정은 R11 다른 의미 |
| External API / Local Data | 외부 서버도 누락 센서 복원 못함 |
| Backend Need | 해결책 아님 |
| Permission | 공개 CAR_DRIVE 범위 재확인 |
| Policy Constraint | reset/gap/driver 의미 계약 |
| AVD Verification | 새 authoritative probe 없음 |
| Real Vehicle Verification | OEM 신호 의미 필수 |
| Privacy / Data | P0 — 정책 문서 프로파일 참조 |
| Network | 신호 자체 로컬 |
| Cost | UNKNOWN |
| Product / Contest Value | 완전한 장거리 케어 |
| Feasibility | OEM_DEPENDENT |
| Priority Tier | 3 |
| V / R / D | 5/3/3 |
| Recommendation | Q5 공식 정의가 없으면 confirmed continuous로 승격 금지 |
| Official Evidence | [S03](PHASE8_5_SOURCE_REGISTER.md#S03), [S05](PHASE8_5_SOURCE_REGISTER.md#S05), [S32](PHASE8_5_SOURCE_REGISTER.md#S32) |
| Product Value | High |
| Contest Value | High |
| Vehicle Specificity | High |
| Implementation Cost | High |
| Policy Risk | Medium |
| Demo Impact | Medium |

### R13 — STT

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-07 P0 목표 |
| Current State | 텍스트 대체 |
| AAOS Alternative | AAOS 전용 범용 STT 보장 없음 |
| Pleos Alternative | STT3.3.1 on-device/server/hybrid |
| Android Alternative | SpeechRecognizer |
| External API / Local Data | Cloud STT V2 후보 |
| Backend Need | cloud 선택 시 필요 |
| Permission | Android RECORD_AUDIO dangerous; Pleos STT_SERVICE 별도 |
| Policy Constraint | 명시 push-to-talk/목적 동의; 상시 녹음 제외 |
| AVD Verification | recognizer=true / on-device=false; 녹음 NOT_RUN |
| Real Vehicle Verification | ko-KR·잡음·종료/취소/프라이버시 |
| Privacy / Data | P4 — 정책 문서 프로파일 참조 |
| Network | Android engine은 remote 가능 |
| Cost | 엔진 조건 UNKNOWN / cloud P4 |
| Product / Contest Value | 상호작용 개선 |
| Feasibility | INSUFFICIENT_EVIDENCE |
| Priority Tier | 3 |
| V / R / D | 3/4/4 |
| Recommendation | service 존재만 PASS 금지; ON_DEVICE 문서와 실제 계정/모델 구분 |
| Official Evidence | [S04](PHASE8_5_SOURCE_REGISTER.md#S04), [S29](PHASE8_5_SOURCE_REGISTER.md#S29), [S30](PHASE8_5_SOURCE_REGISTER.md#S30), [S31](PHASE8_5_SOURCE_REGISTER.md#S31), [S36](PHASE8_5_SOURCE_REGISTER.md#S36), [S57](PHASE8_5_SOURCE_REGISTER.md#S57) |
| Product Value | High |
| Contest Value | High |
| Vehicle Specificity | Medium |
| Implementation Cost | High |
| Policy Risk | High |
| Demo Impact | High |

### R14 — Pleos / Android TTS

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-07 P0 목표 |
| Current State | 재생 없음 |
| AAOS Alternative | OS engine 가용성별 |
| Pleos Alternative | TTS3.3.1 ON_DEVICE는 등록 불필요 안내 |
| Android Alternative | TextToSpeech 표준 |
| External API / Local Data | Cloud TTS/사전 음성 대안 |
| Backend Need | 서버 TTS 선택 시 필요 |
| Permission | Pleos TTS_SERVICE; AAR INTERNET 병합 |
| Policy Constraint | focus·주행 정책 및 SDK 계정 구분 |
| AVD Verification | Android init=-1 / engine=[]; Pleos NOT_RUN |
| Real Vehicle Verification | ko-KR/첫 음절/음량/차종 |
| Privacy / Data | P4 — 정책 문서 프로파일 참조 |
| Network | Pleos on-device만 문서 offline; 실증 없음 |
| Cost | SDK/engine 요금 UNKNOWN |
| Product / Contest Value | 캐릭터 동행감 |
| Feasibility | RECOVERABLE_WITH_PLEOS_ACCOUNT |
| Priority Tier | 3 |
| V / R / D | 3/3/5 |
| Recommendation | 계정 제공 상태/라이선스 확인 후 SDK probe; Android TTS가 즉시 대체된다고 주장 금지 |
| Official Evidence | [S04](PHASE8_5_SOURCE_REGISTER.md#S04), [S26](PHASE8_5_SOURCE_REGISTER.md#S26), [S27](PHASE8_5_SOURCE_REGISTER.md#S27), [S28](PHASE8_5_SOURCE_REGISTER.md#S28), [S37](PHASE8_5_SOURCE_REGISTER.md#S37) |
| Product Value | High |
| Contest Value | High |
| Vehicle Specificity | Medium |
| Implementation Cost | High |
| Policy Risk | Medium |
| Demo Impact | High |

### R15 — 사전 음성 + AudioFocus

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-07 명세 대체 허용 |
| Current State | 텍스트만 |
| AAOS Alternative | AAOS focus matrix |
| Pleos Alternative | Audio guide/suspend 정책 |
| Android Alternative | AudioManager/AudioFocusRequest + 로컬 재생 |
| External API / Local Data | 권리 확보한 고정 한국어 녹음 |
| Backend Need | 불필요 |
| Permission | 통상 재생에 RECORD_AUDIO 불필요; 시스템 SAFETY 권한 가정 금지 |
| Policy Constraint | 정차 한정 시작; focus loss·suspend·출력 제한 준수 |
| AVD Verification | 재생/focus NOT_RUN(비간섭 조사) |
| Real Vehicle Verification | 음질/duck/긴급 음성/재개 실측 |
| Privacy / Data | P0 — 정책 문서 프로파일 참조 |
| Network | 불필요 |
| Cost | 녹음 권리 비용/제작 시간 |
| Product / Contest Value | 네트워크 없이 음성 MVP |
| Feasibility | RECOVERABLE_NOW |
| Priority Tier | 2 |
| V / R / D | 3/2/5 |
| Recommendation | 150–200ms pre-roll 후보; focus 거절 시 텍스트, 보류된 음성 뒤늦게 재생 금지 |
| Official Evidence | [S16](PHASE8_5_SOURCE_REGISTER.md#S16), [S17](PHASE8_5_SOURCE_REGISTER.md#S17), [S18](PHASE8_5_SOURCE_REGISTER.md#S18), [S38](PHASE8_5_SOURCE_REGISTER.md#S38), [S39](PHASE8_5_SOURCE_REGISTER.md#S39) |
| Product Value | High |
| Contest Value | High |
| Vehicle Specificity | Medium |
| Implementation Cost | Medium |
| Policy Risk | Low |
| Demo Impact | High |

### R16 — 외부 LLM

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-13 P1/P2 |
| Current State | 없음 |
| AAOS Alternative | 차량 판단/제어 맡기지 않음 |
| Pleos Alternative | LLM SDK 종료; Gleo는 별개 |
| Android Alternative | 일반 HTTPS를 쓸 수 있다는 것과 허용은 별개 |
| External API / Local Data | Gemini3.5 Flash-Lite stable 후보 |
| Backend Need | secret proxy/필터/삭제/쿼터 필요 |
| Permission | INTERNET; 데이터별 별도 동의 |
| Policy Constraint | 외부 AI 허용·콘텐츠 필터/신고/국외 처리 미확정 |
| AVD Verification | API 호출/한국어 품질 NOT_RUN |
| Real Vehicle Verification | 연결/전원/응답 경쟁 테스트 |
| Privacy / Data | P5 — 정책 문서 프로파일 참조 |
| Network | 필요; 결정적 로컬 fallback |
| Cost | 입력$0.30/출력$2.50 per1M tokens·재조회 |
| Product / Contest Value | 자유 대화/회고 풍부하나 제출 위험 증가 |
| Feasibility | RECOVERABLE_WITH_EXTERNAL_SERVICE |
| Priority Tier | 4 |
| V / R / D | 2/5/5 |
| Recommendation | 선택 정차 회고 표현만; SafetyText/우선순위/XP/차량 제어 금지 |
| Official Evidence | [S06](PHASE8_5_SOURCE_REGISTER.md#S06), [S14](PHASE8_5_SOURCE_REGISTER.md#S14), [S19](PHASE8_5_SOURCE_REGISTER.md#S19), [S52](PHASE8_5_SOURCE_REGISTER.md#S52), [S53](PHASE8_5_SOURCE_REGISTER.md#S53), [S54](PHASE8_5_SOURCE_REGISTER.md#S54), [S55](PHASE8_5_SOURCE_REGISTER.md#S55) |
| Product Value | High |
| Contest Value | Medium |
| Vehicle Specificity | Low |
| Implementation Cost | High |
| Policy Risk | High |
| Demo Impact | High |

### R17 — Backend / cloud

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-13/F-14 |
| Current State | 없음 |
| AAOS Alternative | 차량 API 권한 생성 못함 |
| Pleos Alternative | 서버 음성 credentials와 계정 구분 |
| Android Alternative | TLS client/foreground 범위 |
| External API / Local Data | cloud provider 미선정 |
| Backend Need | 새 운영 시스템 필요 |
| Permission | INTERNET; auth/삭제 동의 |
| Policy Constraint | 서버/리전/처리자/심사계정·가용성 확인 |
| AVD Verification | 서버/인증 NOT_RUN |
| Real Vehicle Verification | 망 단절/재시도/소유권 변경 |
| Privacy / Data | P6 — 정책 문서 프로파일 참조 |
| Network | 필요; 로컬 Trip 불변 |
| Cost | hosting/log/egress/DB/운영 전부 별도 |
| Product / Contest Value | 동기화 가능하지만 일정 확대 |
| Feasibility | POST_MVP_COMPLEXITY |
| Priority Tier | 4 |
| V / R / D | 1/4/3 |
| Recommendation | 이번 제출 안에 서버 추가 비추천; 필요 확정 시만 별도 범위 |
| Official Evidence | [S11](PHASE8_5_SOURCE_REGISTER.md#S11), [S12](PHASE8_5_SOURCE_REGISTER.md#S12), [S13](PHASE8_5_SOURCE_REGISTER.md#S13), [S53](PHASE8_5_SOURCE_REGISTER.md#S53) |
| Product Value | Medium |
| Contest Value | Low |
| Vehicle Specificity | Low |
| Implementation Cost | High |
| Policy Risk | High |
| Demo Impact | Medium |

### R18 — Warning / codebook

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-05 P0 |
| Current State | 실제 제외/공개 washer unsupported |
| AAOS Alternative | 표준 property마다 권한 다름 |
| Pleos Alternative | 공개 개별 Safety subset만 후보 |
| Android Alternative | private/privileged 우회 제외 |
| External API / Local Data | 제조사 설명/서비스 POI 별도 |
| Backend Need | 필수 아님 |
| Permission | CAR_SAFETY 사전 승인; API별 정확 RequiresPermission 확인 |
| Policy Constraint | OEM 경고 priority/문구·심사 |
| AVD Verification | 과거 cap unsupported; 신규 payload 없음 |
| Real Vehicle Verification | 실차 fault/회복/정확 코드 필수 |
| Privacy / Data | P0 — 정책 문서 프로파일 참조 |
| Network | 기본 로컬 |
| Cost | OEM 계약 UNKNOWN |
| Product / Contest Value | 핵심 Care지만 잘못된 경고 위험 |
| Feasibility | OEM_DEPENDENT |
| Priority Tier | 3 |
| V / R / D | 5/5/5 |
| Recommendation | aggregate getter가 public이어도 private cap/listener 경로를 대안으로 사용 금지 |
| Official Evidence | [S03](PHASE8_5_SOURCE_REGISTER.md#S03), [S10](PHASE8_5_SOURCE_REGISTER.md#S10), [S32](PHASE8_5_SOURCE_REGISTER.md#S32), [S33](PHASE8_5_SOURCE_REGISTER.md#S33) |
| Product Value | High |
| Contest Value | High |
| Vehicle Specificity | High |
| Implementation Cost | High |
| Policy Risk | High |
| Demo Impact | High |

### R19 — 차량 제어

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-12 P1/P2 |
| Current State | 미실행 intent만 |
| AAOS Alternative | 표준 write도 권한별 OEM 제한 |
| Pleos Alternative | Control SDK 준비 중 공개 안내 |
| Android Alternative | UI/LLM이 직접 set 금지 |
| External API / Local Data | 외부 API로 차량 write 우회 불가 |
| Backend Need | 권한 해결책 아님 |
| Permission | CONTROL_CAR_* exact API별 미확정 |
| Policy Constraint | 사용자 승인+권한+capability+결과 확인+차량 정책 |
| AVD Verification | 제어 probe 안 함 |
| Real Vehicle Verification | 실차 interlock/비정상 결과 필수 |
| Privacy / Data | P0 — 정책 문서 프로파일 참조 |
| Network | 플랫폼 경유 |
| Cost | 계약/요금 UNKNOWN |
| Product / Contest Value | 편의 높으나 안전 리스크 최대 |
| Feasibility | OEM_DEPENDENT |
| Priority Tier | 3 |
| V / R / D | 5/5/5 |
| Recommendation | ControlGateway disabled 유지; 승인 버튼을 성공으로 표시 금지 |
| Official Evidence | [S04](PHASE8_5_SOURCE_REGISTER.md#S04), [S10](PHASE8_5_SOURCE_REGISTER.md#S10), [S32](PHASE8_5_SOURCE_REGISTER.md#S32) |
| Product Value | High |
| Contest Value | High |
| Vehicle Specificity | High |
| Implementation Cost | High |
| Policy Risk | High |
| Demo Impact | High |

### R20 — NaviHelper 경유지

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-04/F-11 |
| Current State | 미연동 |
| AAOS Alternative | 독립 경로 계산 제공 아님 |
| Pleos Alternative | Navi3.0.5 문서+artifact 확보 |
| Android Alternative | NavigationAdapter 경계 유지 |
| External API / Local Data | POI/route geometry 권리 별도 |
| Backend Need | 외부 검색 선택 시만 |
| Permission | pleos.car.permission.NAVI_ROUTE / NAVI_ROUTE_SEARCH / NAVI_CUSTOM_MAP 기능별 |
| Policy Constraint | 목적지 전송/경유지 변경도 명시 승인 |
| AVD Verification | nav package 설치만; SDK bind NOT_RUN |
| Real Vehicle Verification | 차량 내비 유무·route/result 확인 |
| Privacy / Data | P1 — 정책 문서 프로파일 참조 |
| Network | 내장 내비도 map/network 조건별 |
| Cost | SDK license/요금 UNKNOWN |
| Product / Contest Value | 휴식 장소로 이어지는 행동 |
| Feasibility | RECOVERABLE_WITH_POLICY_APPROVAL |
| Priority Tier | 3 |
| V / R / D | 5/4/4 |
| Recommendation | State/Navi 신청 불필요 안내를 인정하되 자동 경유지 추가 허용으로 해석 금지 |
| Official Evidence | [S03](PHASE8_5_SOURCE_REGISTER.md#S03), [S04](PHASE8_5_SOURCE_REGISTER.md#S04), [S20](PHASE8_5_SOURCE_REGISTER.md#S20), [S21](PHASE8_5_SOURCE_REGISTER.md#S21), [S22](PHASE8_5_SOURCE_REGISTER.md#S22) |
| Product Value | High |
| Contest Value | High |
| Vehicle Specificity | High |
| Implementation Cost | High |
| Policy Risk | High |
| Demo Impact | High |

### R21 — 사진 / 모바일 Companion

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-14 P2 |
| Current State | 없음 |
| AAOS Alternative | 차량이 phone gallery 소유하지 않음 |
| Pleos Alternative | 범용 사진 sync SDK 확인 못함 |
| Android Alternative | Android phone picker / iOS picker 후보 |
| External API / Local Data | 수동 웹 업로드/QR 연결 대안 |
| Backend Need | 교차 기기 전송/소유권 인증 필요 |
| Permission | 선택 URI 권한; 차량 location/mic와 별개 |
| Policy Constraint | 정차 QR·EXIF 제거·가족 사진 동의 |
| AVD Verification | picker/동기화 NOT_RUN |
| Real Vehicle Verification | 차량 공유/로그아웃·전원 |
| Privacy / Data | P7 — 정책 문서 프로파일 참조 |
| Network | 전송 단계 필요; 선택 local preview 가능 |
| Cost | storage/egress/개발 비용 |
| Product / Contest Value | 기억 확장이나 원전 MVP 제외 |
| Feasibility | POST_MVP_COMPLEXITY |
| Priority Tier | 4 |
| V / R / D | 1/4/4 |
| Recommendation | 선택된 사진만; 차에서 전체 photo library/메타데이터 권한 요구 금지 |
| Official Evidence | [S08](PHASE8_5_SOURCE_REGISTER.md#S08), [S11](PHASE8_5_SOURCE_REGISTER.md#S11), [S61](PHASE8_5_SOURCE_REGISTER.md#S61), [S62](PHASE8_5_SOURCE_REGISTER.md#S62), [S63](PHASE8_5_SOURCE_REGISTER.md#S63) |
| Product Value | Medium |
| Contest Value | Medium |
| Vehicle Specificity | Low |
| Implementation Cost | High |
| Policy Risk | High |
| Demo Impact | High |

### R22 — Premium / RP / 결제

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-14 P2 |
| Current State | 무료 트랙만 |
| AAOS Alternative | billing 서비스 보장 없음 |
| Pleos Alternative | Pleos 결제 SDK 좌표 확인 못함 |
| Android Alternative | Google Play Billing 당연 가용 가정 금지 |
| External API / Local Data | 스토어 entitlement/결제 계약 미확정 |
| Backend Need | 구매 검증/환불/복구 필요 |
| Permission | 결제 API/permission UNKNOWN |
| Policy Constraint | 수익 모델·재화·유료/IP/심사 별도 |
| AVD Verification | 실구매/환불 NOT_RUN |
| Real Vehicle Verification | 중복/오프라인/공유 차량 |
| Privacy / Data | P6 — 정책 문서 프로파일 참조 |
| Network | 결제/검증 필요 |
| Cost | 수수료/세금/정책 CURRENT_PRICE_RECHECK_REQUIRED |
| Product / Contest Value | 사업성은 있으나 Care 범위와 독립 |
| Feasibility | POST_MVP_COMPLEXITY |
| Priority Tier | 4 |
| V / R / D | 1/5/3 |
| Recommendation | 이번 제출은 무료; safety/우선순위는 결제와 항상 동일 |
| Official Evidence | [S04](PHASE8_5_SOURCE_REGISTER.md#S04), [S11](PHASE8_5_SOURCE_REGISTER.md#S11), [S14](PHASE8_5_SOURCE_REGISTER.md#S14) |
| Product Value | Medium |
| Contest Value | Low |
| Vehicle Specificity | Low |
| Implementation Cost | High |
| Policy Risk | High |
| Demo Impact | Medium |

### R23 — 외부 IP 캐릭터

| 축 | 조사 결과 |
|---|---|
| Original Requirement | Master 장기 비전 |
| Current State | 오리지널만 |
| AAOS Alternative | 관련 없음 |
| Pleos Alternative | 등록 시 권리 증빙 |
| Android Alternative | 교체 가능한 original asset |
| External API / Local Data | 권리자 계약 없으면 불가 |
| Backend Need | 불필요 |
| Permission | 저작권/음성/상표 권리 필요 |
| Policy Constraint | 사용자 명시 금지 및 IP 심사 |
| AVD Verification | 미사용 |
| Real Vehicle Verification | 해당 없음 |
| Privacy / Data | P0 — 정책 문서 프로파일 참조 |
| Network | 불필요 |
| Cost | license 비용 UNKNOWN |
| Product / Contest Value | 독창적 세계관 우선 |
| Feasibility | NOT_RECOMMENDED |
| Priority Tier | 5 |
| V / R / D | 1/5/3 |
| Recommendation | 상용 IP 추가 제외. 도담/솜솜 임시 에셋 확정만 별도 |
| Official Evidence | [S14](PHASE8_5_SOURCE_REGISTER.md#S14) |
| Product Value | Low |
| Contest Value | Low |
| Vehicle Specificity | Low |
| Implementation Cost | High |
| Policy Risk | High |
| Demo Impact | Medium |

### R24 — DO / 주행 Care

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-01/F-02~07 공통 |
| Current State | DO 미선언/주행 차단 |
| AAOS Alternative | CarUxRestrictions 권위 |
| Pleos Alternative | Mobility 후보 MEDIUM·미승인 |
| Android Alternative | OS 차단 우회 금지 |
| External API / Local Data | 외부 음성/서버도 우회 아님 |
| Backend Need | 해결책 아님 |
| Permission | DO metadata는 permission 승인 대체 아님 |
| Policy Constraint | 카테고리/최소 발화·동작 사전 검토 |
| AVD Verification | 기존 flags80 전환은 과거 증거 |
| Real Vehicle Verification | OEM/지역별 실차 심사 |
| Privacy / Data | P0 — 정책 문서 프로파일 참조 |
| Network | 독립 |
| Cost | 심사 비용 UNKNOWN |
| Product / Contest Value | 핵심 차량 Care 제출 가치 |
| Feasibility | RECOVERABLE_WITH_POLICY_APPROVAL |
| Priority Tier | 3 |
| V / R / D | 5/5/5 |
| Recommendation | 정차 MVP 유지; 짧은 운전 관련 안내만 구체 UX로 승인 요청, 임의 DO 선언 금지 |
| Official Evidence | [S07](PHASE8_5_SOURCE_REGISTER.md#S07), [S08](PHASE8_5_SOURCE_REGISTER.md#S08), [S09](PHASE8_5_SOURCE_REGISTER.md#S09), [S10](PHASE8_5_SOURCE_REGISTER.md#S10), [S15](PHASE8_5_SOURCE_REGISTER.md#S15) |
| Product Value | High |
| Contest Value | High |
| Vehicle Specificity | High |
| Implementation Cost | High |
| Policy Risk | High |
| Demo Impact | High |

### R25 — 로컬 기억 회상·기간 요약

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-13 의도 일부 |
| Current State | 저장된 기억만 있음 |
| AAOS Alternative | 차량 API 불필요 |
| Pleos Alternative | 불필요 |
| Android Alternative | 동의한 데이터의 결정적 선택/템플릿 |
| External API / Local Data | LLM 선택 사항 |
| Backend Need | 불필요 |
| Permission | 새 권한 없음 |
| Policy Constraint | 별도 기억 동의·삭제/취소 반영·정차 |
| AVD Verification | 새 검색/기간 집계 NOT_RUN |
| Real Vehicle Verification | 시간대/시계 변경·다중 사용자 |
| Privacy / Data | P0 — 정책 문서 프로파일 참조 |
| Network | 불필요 |
| Cost | 로컬 |
| Product / Contest Value | 서비스 기억 의도를 적은 비용으로 복구 |
| Feasibility | RECOVERABLE_NOW |
| Priority Tier | 1 |
| V / R / D | 2/2/4 |
| Recommendation | 날조 없는 한 건 회상/월간 사실 요약; 민감한 원문 자동 낭독 제외 |
| Official Evidence | [S11](PHASE8_5_SOURCE_REGISTER.md#S11), [S12](PHASE8_5_SOURCE_REGISTER.md#S12) |
| Product Value | High |
| Contest Value | Medium |
| Vehicle Specificity | Low |
| Implementation Cost | Medium |
| Policy Risk | Low |
| Demo Impact | High |

### R26 — 무료 보상 외형 적용

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-06/F-10 P0 |
| Current State | 소유 기록/기본 캐릭터 선택 |
| AAOS Alternative | 차량 API 불필요 |
| Pleos Alternative | 불필요 |
| Android Alternative | 기존 Presenter/로컬 entitlement |
| External API / Local Data | 오리지널 theme/pack |
| Backend Need | 불필요 |
| Permission | 없음 |
| Policy Constraint | 정차 설정; Care 동일 |
| AVD Verification | 새 apply/restore NOT_RUN |
| Real Vehicle Verification | 가독성·리소스·상태 복원 |
| Privacy / Data | P0 — 정책 문서 프로파일 참조 |
| Network | 불필요 |
| Cost | 로컬/에셋 제작 |
| Product / Contest Value | 시즌 보상 효용 상승 |
| Feasibility | RECOVERABLE_NOW |
| Priority Tier | 2 |
| V / R / D | 1/1/4 |
| Recommendation | 보유→미리보기→적용→복원; 제어/안전/XP를 외형에 연결 금지 |
| Official Evidence | [S14](PHASE8_5_SOURCE_REGISTER.md#S14), [S15](PHASE8_5_SOURCE_REGISTER.md#S15) |
| Product Value | Medium |
| Contest Value | Medium |
| Vehicle Specificity | Low |
| Implementation Cost | Low |
| Policy Risk | Low |
| Demo Impact | High |

### R27 — 민감 로컬 저장 암호화

| 축 | 조사 결과 |
|---|---|
| Original Requirement | 제출 보안 선행 조건 |
| Current State | schema3 binary 평문 |
| AAOS Alternative | Android Keystore 가능 |
| Pleos Alternative | 보안 가이드 필수 기준 |
| Android Alternative | 앱 수준 authenticated encryption 후보 |
| External API / Local Data | 불필요 |
| Backend Need | 불필요 |
| Permission | 기존 앱 내부 저장; 새 권한 가정 안 함 |
| Policy Constraint | 내부·staging·사본 모두 보호/복구 정책 |
| AVD Verification | 현재 코드 검사만; crypto NOT_RUN |
| Real Vehicle Verification | 키 무효화/전원 손실/재설치 테스트 |
| Privacy / Data | P0 — 정책 문서 프로파일 참조 |
| Network | 불필요 |
| Cost | 로컬 구현/회귀 비용 |
| Product / Contest Value | 기존 회고 기능의 제출 적합성 |
| Feasibility | RECOVERABLE_NOW |
| Priority Tier | 1 |
| V / R / D | 3/2/2 |
| Recommendation | 암호화 envelope/기존 schema migration/원자적 저장·rollback을 먼저 설계; 데이터 삭제 우회 금지 |
| Official Evidence | [S11](PHASE8_5_SOURCE_REGISTER.md#S11) |
| Product Value | High |
| Contest Value | High |
| Vehicle Specificity | Medium |
| Implementation Cost | Medium |
| Policy Risk | Low |
| Demo Impact | Low |

### R28 — Cloud 한국어 STT/TTS

| 축 | 조사 결과 |
|---|---|
| Original Requirement | F-07 대안 |
| Current State | 없음 |
| AAOS Alternative | 공개 mic/focus 경계 유지 |
| Pleos Alternative | on-device 대안과 별도 |
| Android Alternative | HTTPS audio 전송·재생 |
| External API / Local Data | Google Cloud STT V2 / TTS v1 |
| Backend Need | 서버 credential 보호·요청 수 제한 |
| Permission | INTERNET/RECORD_AUDIO(STT)/focus(TTS) |
| Policy Constraint | 국외 처리/녹음/텍스트 동의·보관/voice 권리 |
| AVD Verification | ko-KR 문서만; 실호출 NOT_RUN |
| Real Vehicle Verification | 차내잡음·망·지연·취소 |
| Privacy / Data | P4 — 정책 문서 프로파일 참조 |
| Network | 필요; 실패하면 텍스트 |
| Cost | STT$0.016/분·TTS$4/1M 문자 기본 조건·재조회 |
| Product / Contest Value | 음성 공백 복구 가능, 운영비 증가 |
| Feasibility | RECOVERABLE_WITH_EXTERNAL_SERVICE |
| Priority Tier | 4 |
| V / R / D | 2/4/4 |
| Recommendation | cloud를 숨긴 SDK 대체로 추가 금지; 별도 scope/동의/요금 승인 후 |
| Official Evidence | [S57](PHASE8_5_SOURCE_REGISTER.md#S57), [S58](PHASE8_5_SOURCE_REGISTER.md#S58), [S59](PHASE8_5_SOURCE_REGISTER.md#S59), [S60](PHASE8_5_SOURCE_REGISTER.md#S60) |
| Product Value | High |
| Contest Value | Medium |
| Vehicle Specificity | Low |
| Implementation Cost | High |
| Policy Risk | High |
| Demo Impact | High |
