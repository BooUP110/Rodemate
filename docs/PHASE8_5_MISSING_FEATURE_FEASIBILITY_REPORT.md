# Phase 8.5 — 누락 기능과 대안 공급자 타당성 조사

**Overall: RECOVERY_PLAN_READY**  
**RECOMMENDED_SUBMISSION_SCOPE: Option B — Enhanced Local/Platform MVP**

2026-11-09 KST 기준. 제출 승인·제품 복구 완료 판정이 아니라 다음 구현 범위를 선택할 수 있는 조사 완료 판정이다. 외부 Gate가 남아 있어도 외기 읽기와 로컬 기능의 복구 계획은 수립할 수 있다. 이번 단계에서는 제품 코드·UI·Manifest·의존성·저장 schema를 변경하지 않았다.

상세 자료: [기능 인벤토리/복구 표](PHASE8_5_FEATURE_RECOVERY_MATRIX.md), [API·권한·개인정보·비용](PHASE8_5_EXTERNAL_API_POLICY_MATRIX.md), [공식 출처 66개](PHASE8_5_SOURCE_REGISTER.md), [검증 JSON](verification/phase8-5-artifacts.json). 원시 probe/공식 다운로드 cache/실험 코드는 Git 제외된 `development/phase8-5/`와 `.tools/phase8-5/`에 보존했다. 이번에 명시된 신규 결과 문서만 `docs/`에 추가했다.

## 1. 기준과 현재 범위

개발 명세 v1.0의 F-01~F-14·P0/P1/P2를 기준으로 Master Plan의 의도를 대조했다. 원전은 정리 작업 후 `development/AI_Roadmate_Development_Spec_v1.0.md`, `development/AI_Roadmate_Master_Plan.md`에 실제 존재한다. Phase7 Closure/Resume·7.5 scope freeze/기능 Gate/SDK matrix/외부 질문·Phase8 report/JSON 및 현재 공개 문서/코드도 읽었다. 원본은 수정하지 않았다.

| 집계 | 분모와 현재 결과 |
|---|---|
| 원전 사용자 capability/UX 인벤토리 | 48그룹: ACTIVE10 / DISABLED4 / EXTERNAL3 / DEFERRED31 |
| 기존 Phase7.5 동결 표 | 기능+입력+정책31행: A13 / B4 / C8 / D6 **그대로 보존** |
| 이번 복구 후보 | 대안28개: Tier1 4 / Tier2 7 / Tier3 10 / Tier4 6 / Tier5 1 |

세 집계는 분모가 다르며 합산하지 않는다. ACTIVE도 특정 부분의 구현을 뜻한다. 예를 들어 케어 로그 저장 경계가 구현되어 있어도 Production의 실제 케어 입력은 아직 비활성이다. 메모리 카드는 사실 템플릿이며 LLM 요약이 아니다. 보유 컬렉션 목록과 실제 지역 엽서 발급, 보상 소유와 외형 적용을 나누었다.

우선순위는 F-01~06/08~10 P0, F-07 P0 목표(음성 unavailable 시 정차 입력/사전 음성 대체 허용), F-11 P1, F-12/13 P1/P2, F-14 P2다. 기능 P0/P1/P2와 Care 경고 등급 P1/P2는 서로 다르다.

## 2. 핵심 판단과 문서 충돌

1. **외기 온도는 현 AVD에서 일반 앱으로 읽을 수 있다.** 공식 Pleos 정책도 동일 기능의 AAOS 표준 API를 우선한다. 기존의 ‘SDK Gate 때문에 불가능’이라는 포괄 해석을 좁혀야 한다. 제품 연결 전이므로 과거 disabled 상태는 바꾸지 않는다. [S01](PHASE8_5_SOURCE_REGISTER.md#S01).
2. **NIGHT는 읽기 가능성과 신뢰 가능한 Care 입력 사이에 검증이 남아 있다.** 시계 기반 밤을 넣거나 오래된 event timestamp를 지금으로 바꾸는 해결은 하지 않는다.
3. **Android 표준 음성은 즉시 대체재가 아니다.** recognizer는 발견했지만 on-device=false이고 TTS는 init ERROR다. Pleos SDK on-device 모드·계정·한국어 모델은 따로 검증해야 한다.
4. **개인화와 기억은 센서/클라우드 없이도 일부 회복 가능하다.** 정차 중 명시 선호 기반 일반 안내와 동의한 로컬 기억의 결정적 회상은 후보가 된다. 기존 자동 HVAC 조건을 몰래 완화하거나 LLM 기능으로 표시하지 않는다.
5. **현재 민감 회고 저장에는 앱 수준 암호화가 없다.** 공식 보안 기준과의 갭을 새 제출 선행 조건으로 기록했다. 로컬/sandbox/noBackup만으로 암호화 완료라고 볼 수 없다. [보안 분석](PHASE8_5_EXTERNAL_API_POLICY_MATRIX.md#신규-보안-발견--현재-저장소를-제출-완료로-볼-수-없는-이유).

Master Plan은 Light/HVAC/Rest/Warning의 서비스 중심성을 강조하지만, 개발 명세는 SDK Gate와 명시적인 제한 Demo를 허용한다. 따라서 현재 비활성을 영구적인 서비스 방향 변경으로 해석하지 않고 복구 후보로 남긴다. Master의 RP/유료/사진 확장은 개발 명세 F-14의 MVP 제외보다 우선하지 않는다. 개발 명세 F-04는 ‘검증 가능한 앱 타이머’를 허용하지만 현재 구현은 Trip와 confirmed continuous를 분리한다. 다음에는 **앱 관측 추정 모델을 별도로 검증**할 수 있으며 Trip duration을 기존 confirmed 필드로 복사해서는 안 된다.

## 3. NIGHT / 야간 판단 — R01

AAOS `NIGHT_MODE`(287310855, global0, Boolean, ON_CHANGE)는 공식 공개 읽기 API이며 `CAR_EXTERIOR_ENVIRONMENT`는 normal 권한이다. API34 public SDK로 컴파일한 독립 앱에서 config/get/callback 등록까지 성공했다. SDK 경로는 차량 조명 상태 자체를 주는 것이지 새로운 공개 ambient/night API를 확인한 것은 아니다. [S33](PHASE8_5_SOURCE_REGISTER.md#S33).

현재 관측은 false 한 값과 등록 직후 초기 callback 하나다. raw timestamp의 age가 약4,206초였다. ON_CHANGE 값은 변경이 없으면 오래될 수 있고 초기 이벤트는 전환 증거가 아니다. sensor eventUpdatedAt, 현재 연결/읽기 확인 시점, 수신 시각을 구분하는 계약이 필요하다. cached 값의 raw timestamp를 바꾸거나 자동으로 fresh라고 판정하지 않는다. 어두운 cabin/night-mode 의미와 기존 P1의 환경 의미, true↔false 전환, unavailable/error, 재연결을 검증해야 한다. [S34](PHASE8_5_SOURCE_REGISTER.md#S34).

대안은 신뢰할 위치+날짜+일출/일몰의 **DERIVED_ASTRONOMICAL_NIGHT**다. 로컬 계산은 네트워크 없이 가능하지만 위치가 필요하며 터널/날씨/실제 조도를 표현하지 못한다. NOAA 참고 계산기는 유지보수가 종료되었고 근사 오차도 명시한다. 외부 일몰 API는 좌표 전송/캐시/망 실패가 추가된다. 어느 쪽도 18~06시 단순 시계나 실제 저조도 센서로 둔갑시키지 않는다. **Care 입력 판정 INSUFFICIENT_EVIDENCE / Tier2**, 주행 발화는 별도 DO Gate다. [S43–45](PHASE8_5_SOURCE_REGISTER.md#S43).

## 4. Outside temperature / HVAC — R02·R03

`ENV_OUTSIDE_TEMPERATURE`(291505923, global0, Float°C, CONTINUOUS, read-only)를 일반 UID에서 읽었다. 현재 AVD는 config1–2Hz를 제공했고 get25°C와 서로 다른 timestamp의 callback2개를 받았다. 초기 age90ms, callback age120ms/9ms다. 차량 값을 변경하지 않았다. **read provider: RECOVERABLE_NOW / Tier1**. 실차 센서 정확도·다른 VHAL 지원은 별도다.

이것만으로 기존 HVAC P2를 활성화할 수 없다. front-left 목표 값은 운전자 목표라고 확정되지 않았고 driverPresent/confirmed preference, hot 조건의 AC/defrost 등도 부족하다. 날씨 API 온도는 차량 센서 온도와 다르다.

driverPresent는 원전의 모든 정차 일반 조언에 필수라고 단정할 근거가 부족하다. **정차+사용자 명시 요청+확인된 선호**의 ‘직접 조절해 보세요’ 안내를 별도 규칙/입력 모델로 제안한다. occupant/운전자 인증·자동 학습·제어 성공은 주장하지 않는다. 기존 HvacRule의 조건을 삭제하는 수정은 이번에도 다음 범위 합의 전에도 하지 않는다. 명시 수동 선호는 학습된 취향과 다르게 USER_INPUT으로 표시한다.

## 5. Location — R05

표준 Android `LocationManager`는 허용 가능한 대안 후보이며 Pleos FusedLocation과 별개다. AVD에 gps/fused/passive provider와 위치 켜짐은 확인했지만, permission 요청·현재 위치·좌표 기록·callback은 실행하지 않았다. Google Play Services Fused가 필요하다고 가정하지 않는다. [S23–25](PHASE8_5_SOURCE_REGISTER.md#S23), [S35](PHASE8_5_SOURCE_REGISTER.md#S35).

다음 검증은 일반 앱의 foreground fine/coarse·거절/취소·provider disabled·freshness/accuracy/mock·구독 해제다. background 상주 서비스는 범위에서 제외한다. **INSUFFICIENT_EVIDENCE / Tier2**. 위치 API 존재만으로 실제 엽서 발급 가능하다고 결론 내리지 않는다. 계정 승인 전까지 Pleos Fused 경로를 연결하지 않는다.

## 6. Region / Postcard — R06·R07

가능한 경로는 ①검증된 위치+라이선스가 확정된 로컬 행정경계+point-in-polygon ②외부 reverse geocoding이다. 로컬안은 외부 좌표 전송 없이 동작하지만 기준연도/행정동·법정동/시군구 단위·CRS·경계 정확도가 필요하다. SGIS는 WGS84 전용 endpoint와 UTM-K를 구분하며 경계 API도 존재한다. 실제 배포 파일·오프라인 재배포 권리는 확보하지 않았다. [S50–51](PHASE8_5_SOURCE_REGISTER.md#S50).

Kakao는 한국 region code 계약을 문서로 확인했지만 호출·정확도는 검증하지 않았다. fresh location/동의/자동차 사용·캐시 조건이 선행된다. **로컬 Tier2 조건부 / 외부 서비스 Tier3**. last known·수동 지역 선택·mock·Demo 좌표는 실제 방문 증거가 아니다. regionId+카탈로그 버전·경계 모호성·첫 방문 중복·삭제/보상 유지 정책을 검증한 뒤 발급한다. [S40–42](PHASE8_5_SOURCE_REGISTER.md#S40).

## 7. Map / Exploration — R08·R09

각 대안을 구분했다: 실제 tile 지도 / 저장 route polyline / 지역별 색칠 / 격자 fog / 앨범·도식. Canvas 도식은 추가 SDK 없이 가능하지만 ‘실제 지도’로 표시하지 않는다. 실제 방문이 0이면 그 사실을 유지한다. grid fog와 polyline도 위치 경로 증거가 필요하다.

MapLibre13.7.1 POM/AAR·BSD license/minSdk23은 확인했다. tile/style/font/POI 사용권은 별도이고 POM의 Kotlin2.2.10/coroutines1.10.2는 현재2.0.0 툴체인과 통합 검증이 필요하다. renderer만 넣어 지도가 완성되는 것이 아니다. **도식 Tier2 / 실제 지도 Tier4**. 공개 OSM/Nominatim 서버를 무제한 무료 경로 서비스로 사용하지 않는다. [S46–49](PHASE8_5_SOURCE_REGISTER.md#S46).

## 8. POI / 휴게소 — R10

휴식 권고 판단, 인근 장소 검색, 동선/진행 방향에 맞는 후보 필터, 사용자가 승인한 경유지 추가를 분리한다. Kakao keyword/category REST 문서는 확인했지만 실검색/휴게소 품질은 미측정이다. 정적 허가 CSV는 가능한 대안이나 이번에 버전·방향·폐점 정보를 갖춘 파일은 확보하지 못했다. 도로공사 안내 PDF 접근도 timeout이라 API 계약 확인으로 세지 않았다.

직선 거리만 가까운 반대 차선/진입 불가 장소를 ‘다음 휴게소’로 추천하지 않는다. 경로 정보가 없으면 ‘인근 장소 검색’ 수준으로 한정한다. **외부 서비스/내비 Gate / Tier3**. 장소가 없어도 Rest 판단·로컬 기록을 막지 않는다.

## 9. Continuous driving — R11·R12

공식 Trip time의 reset/실제 휴식/driver 변경 의미는 미확정이고 과거 AVD capability는 unsupported다. 서버를 붙여도 누락된 차량 의미가 생기지 않는다. **권위 신호는 OEM_DEPENDENT / Tier3**.

별도 앱 관측 추정은 기술적으로 가능하고 개발 명세가 허용한다. coverage가 있는 fresh motion의 elapsed time을 기록하되 gap·프로세스 중단·suspend·시계 변경·운전자 교체·수동 reset을 명확히 모델링한다. ‘앱 관측 운전 추정’이며 피로 진단/운전자 전체 연속 운전/실제 휴식 확증이 아니다. Trip 종료120초를 실제 휴식 reset으로 사용하지 않고 total odometer 차분도 쓰지 않는다. **RECOVERABLE_NOW / Tier2**, RestRule에 기존 confirmed 값으로 주입하지 않는 새 평가 계약이 선행된다.

## 10. STT — R13

Android SpeechRecognizer availability=true, on-device availability=false다. 마이크 feature와 서비스 발견은 한국어 인식·권한·오프라인 성공이 아니다. 실제 녹음을 하지 않았다. 공개 Android 경로는 RECORD_AUDIO와 명시 push-to-talk·취소·정차 제한이 필요하며 원격 엔진일 수 있다. [S36](PHASE8_5_SOURCE_REGISTER.md#S36).

Pleos STT3.3.1 AAR는 받았지만 ON_DEVICE 모델/계정/초기화/ko-KR는 미검증이다. Cloud STT V2에는 ko-KR가 있지만 데이터 전송·비용·차내 잡음 측정이 추가된다. 결과 텍스트는 intent 후보일 뿐 사용자 승인/차량 제어 권한이 아니다. **표준 경로 INSUFFICIENT_EVIDENCE / Tier3**, cloud는 Tier4다.

## 11. TTS — R14

AVD 서비스 query에는 TTS 컴포넌트가 있었으나 일반 앱 TextToSpeech 초기화 결과는 **ERROR(-1), engines=[]**였다. ko-KR 품질·latency·합성·재생은 미검증이다. 이를 모든 Pleos 차량의 Android TTS 미지원으로 확대하지 않는다. [S37](PHASE8_5_SOURCE_REGISTER.md#S37).

Pleos3.3.1 ON_DEVICE는 네트워크/앱 등록이 불필요하다는 문서가 있지만 계정 가용성·모델과 별개다. 서버/hybrid는 credentials/전송·약관이 추가된다. **Pleos 경로 Tier3**. 본 MVP의 네트워크 없는 대안은 권리를 확보한 고정 한국어 음원이며, 동적 TTS 구현 완료로 표시하지 않는다.

## 12. AudioFocus — R15

사전 음원에도 focus 요청/거절/손실/duck/반납·suspend 대응이 필요하다. 현재 read-only 조사에서는 다른 오디오를 방해할 수 있는 요청/재생을 하지 않았다. API26 AudioFocusRequest는 현재 min28에서 사용 가능하지만 AAOS focus matrix와 Pleos 정책이 우선이다. Android15 런타임의 target35+ foreground 조건도 향후 범위다. [S16–18](PHASE8_5_SOURCE_REGISTER.md#S16), [S38–39](PHASE8_5_SOURCE_REGISTER.md#S38).

오리지널 안내 음원에 150–200ms pre-roll 후보를 적용하고 첫 음절·거절·loss/suspend·취소 후 지연 재생을 검증한다. 앱 Care P1을 system SAFETY/EMERGENCY usage로 승격하지 않는다. **RECOVERABLE_NOW / Tier2**, 우선 정차 중 명시 재생만 권장한다.

## 13. LLM — R16

Pleos AVD2.1.0 LLM SDK는 제공 종료다. Gleo는 앱 기능 schema/action 통합이지 자유 대화 inference API가 아니다. 외부 후보로 Gemini3.5 Flash-Lite의 공식 Stable 표기·가격·키/데이터 약관을 조사했으며 실제 호출/한국어 평가를 하지 않았다. 개인 회고를 무료 API에 보내는 안은 약관상 개인정보 제한과 충돌할 수 있다. 유료도 무보관이 아니다. [S06](PHASE8_5_SOURCE_REGISTER.md#S06), [S19](PHASE8_5_SOURCE_REGISTER.md#S19), [S52–55](PHASE8_5_SOURCE_REGISTER.md#S52).

LLM이 담당할 수 있는 후보는 정차 시 선택한 회고의 말투/요약뿐이다. SafetyText/경고 우선순위/차량 원인·방문·거리 사실/XP/제어 승인·결과는 담당할 수 없다. secret proxy·필터/신고·삭제 전파·timeout을 별도 설계해야 한다. 온디바이스 LLM은 Beta/기기·모델·non-GAS 가용성 미확정으로 즉시 대안이 아니다. **외부 서비스로 가능 / Tier4**, 제출 필수 범위에서 제외한다.

## 14. Backend / Cloud — R17

클라우드는 장기 기억/사진/결제/LLM secret 보호에는 도움이 되지만 데이터 동의·인증·차량 소유권 변경·탈퇴/삭제 전파·심사 접근/가용성·운영비를 추가한다. 외부 서버를 다룬 보안 조건은 확인했으나 본 앱의 서버·LLM이 승인되었다는 근거는 없다. 지금은 **POST_MVP_COMPLEXITY / Tier4**. 로컬 핵심 경로를 온라인 인증/응답 대기로 바꾸지 않는다.

## 15. Warning — R18

기존 공개 washer capability unsupported와 private aggregate capability/listener 경계는 그대로다. 표준 AAOS도 property별 공개/privileged 권한이 달라 엔진 경고·타이어 등 모든 warning의 대체재라고 묶을 수 없다. 개별 public API+지원 capability+실제 payload+OEM 코드북+허가된 문구가 필요하다. 원인/심각도를 임의 해석하지 않는다. **OEM_DEPENDENT / Tier3**, 현 Production 제외를 유지한다.

## 16. Seat — R04

과거 Console/SDK/sample Boolean 불일치를 뒤집어 보정하지 않았다. 새 Seat 호출도 하지 않았다. occupancy는 사람의 존재이지 인증된 driver identity가 아니며 INFO_DRIVER_SEAT도 위치 정보일 뿐이다. 최신 AAOS Seat permission 설명을 API34에 그대로 적용할 수 없다. 공식 의미/영역·대상 차종·샘플 재현·account/permission이 필요하다. **OEM_DEPENDENT / Tier3**. 정차 수동 comfort 경로는 Seat를 대체 증명하는 안이 아니다.

## 17. Control — R19

HVAC/seat/window/light write는 사용자 승인·정확한 API/permission·Capability·interlock·실제 결과 확인을 모두 통과해야 한다. SDK public 문서/AVD에 기능이 있어도 제어 승인으로 간주하지 않는다. ControlGateway 비활성·수락 시 NOT_EXECUTED 표시를 유지한다. LLM/백엔드는 write 경계를 우회할 수 없다. **OEM_DEPENDENT / Tier3**, 차량 쓰기는 이번에 0회다.

## 18. NaviHelper — R20

3.0.5 POM/AAR는 확보했고 공개 안내상 별도 신청 없이 사용 가능한 SDK다. 다만 내장 내비 지원·권한별 read/route request/waypoint·실제 result는 검증하지 않았다. 지도 렌더러/POI 데이터베이스/경로 알고리즘 제공으로 확대하지 않는다. **정책·차종 Gate / Tier3**. 경유지 추가는 명시 승인 및 실제 결과 확인 대상이다.

## 19. Photos / mobile companion — R21

Android phone Photo Picker, iOS 선택 picker, 정차 중 QR pairing/수동 웹 업로드가 후보지만 차량이 휴대폰 전체 갤러리를 직접 읽는 기능은 아니다. iOS 공식 페이지 본문은 JS 제한으로 OS/메타데이터 세부를 검증하지 못했다. 선택 URI/EXIF 위치·얼굴·가족/아동·thumbnail/cache·로그아웃/삭제를 설계해야 한다. QR 인증 패턴이 주행 중 QR 사용 허가를 뜻하지 않는다. **Tier4**, 원전 MVP 제외 유지.

## 20. Premium / payment / IP — R22·R23

무료 시즌 XP/미션/수령과 실제 Premium entitlement·결제·RP·멀티패스·실물/확률형 보상은 분리한다. Pleos billing SDK의 정확 좌표/계약을 확인하지 못했으며 non-GAS에서 Google Play Billing을 당연 가용으로 간주하지 않는다. 매출·환불·복원·서버 검증은 **Tier4**다. 무료/유료·캐릭터 여부는 안전 결과에 영향을 주지 않는다.

외부 IP는 사용자 지시대로 사용하지 않는다. 장기 비전의 라이선스 방향은 기록하되 이번 후보는 **NOT_RECOMMENDED / Tier5**다. 오리지널 캐릭터의 최종 에셋·음성 권리는 별도 확인한다. 보유 무료 팩을 표현에 적용하는 것은 Tier2다.

## 21. DO / in-drive Care — R24

카테고리는 기존 Mobility 후보/MEDIUM/NOT MARKET APPROVED다. DO 선언은 기술 스위치일 뿐 승인 결과가 아니다. 짧은 운전 관련 안내도 실제 안전 효용/발화 빈도·방해 수준·UX 상태·지역/차종을 포함해 검토 받아야 한다. 외부 API/음성/백그라운드로 플랫폼 차단을 우회하지 않는다. **RECOVERABLE_WITH_POLICY_APPROVAL / Tier3**, 정차/UX 미확인 콘텐츠 잠금 유지.

## 22. 외부 API 허용성 결론

외부 서버·일반 API·LLM은 보안/콘텐츠 정책의 적용 대상이며 일괄 허용 또는 일괄 금지로 단정할 수 없다. 위치에는 별도 목적/약관·동의·실제 provider 검증이 필요하다. unresolved는 Roadmate의 DO·카테고리·Vehicle permission, account6종, 외부 AI/음성·국외 처리·지도/POI 자동차 라이선스, 개인정보 최종 문안이다. [정책별 구분/질문](PHASE8_5_EXTERNAL_API_POLICY_MATRIX.md#허용성-판단)을 따르며 문의 발송·계정 접속·약관 동의는 하지 않았다.

## 23. 이번 실제 AVD probe / 명령과 결과

환경: Pleos Connect2.1.0/API34, emulator-5554, 사용자10. 별도 package `io.booup.roadmate.feasibilityprobe`, testOnly=true, min28/target34. SDK Class stub/쉘 UID 채택 없이 공개 Car API를 일반 앱 context로 사용했다. 제품 permission은 변경하지 않았다.

실행한 조사/빌드/계측 명령의 본체는 다음과 같다. `adb`는 설치된 platform-tools의 adb.exe다. 영문/한글 경로를 안전하게 인자로 전달했다.

```powershell
python -X utf8 development/phase8-5/environment_probe.py
.\gradlew.bat --project-dir .tools/phase8-5/probe --gradle-user-home .gradle-user-home --console=plain --offline --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=COMPAT' -Pandroid.overridePathCheck=true assembleDebug
adb -s emulator-5554 install -t .tools/phase8-5/probe/build/outputs/apk/debug/RoadmateFeasibilityProbe-debug.apk
adb -s emulator-5554 shell am instrument --user 10 -w io.booup.roadmate.feasibilityprobe/.ReadOnlyProbe
python development/phase8-5/artifact_review.py
python -X utf8 development/phase8-5/write_research.py
```

environment probe는 baseline 존재 시 재실행을 거부하므로 **재사용용 반복 테스트 명령이 아니다**. 첫 APK 빌드57초 성공 후 설치는 `INSTALL_FAILED_MISSING_SHARED_LIBRARY android.car`로 실패했다. AVD shared library 목록과 기존 제품 Manifest를 읽고, 별도 probe가 불필요하게 요구한 uses-library runtime 항목만 제거했다. 기존 제품과 같은 compile classpath/플랫폼 API 방식으로 최종 빌드22초 성공(5실행/27 up-to-date), 새 testOnly APK 설치·계측 exit0을 확인했다. 초기 실패를 지우거나 제품을 우회 수정하지 않았다.

| 항목 | 이번 관측 | 범위 한계 |
|---|---|---|
| normal exterior permission | GRANTED, Car connected | shell permission identity 사용 안 함 |
| NIGHT | config/get false·초기 callback1 | true/false 전환/차량 Care/P1 UI 미검증 |
| 외기 | get25°C·fresh callback2 | 현재 AVD 하나; 실차 정확도 미검증 |
| 위치 | gps/fused/passive·enabled | fresh fix/권한/좌표 요청 안 함 |
| STT | availability true / on-device false | 녹음/ko-KR 미실행 |
| Android TTS | init callback ERROR(-1), engines=[] | 합성/재생 미실행; Pleos TTS SDK는 미실행 |
| 신규 artifact | Pleos4 + MapLibre1 POM/AAR HTTP200 | 계정·SDK runtime·통합 compile/permission 심사 미검증 |
| 제품 빌드/JVM/기존 E2E | NOT_RUN | 기존516회를 이번 PASS로 재보고하지 않음 |

AVD를 재시작/초기화하거나 Console 입력·앱 동의/사용자 기록/설정·글자 크기를 변경하지 않았다. 음성 재생/focus 요청·녹음·위치 fix·Seat·Warning·차량 write·root/private API 호출은 없었다. 독립 probe 설치만 새 package를 추가했다.

## 24. 복구 Tier

| Tier | 후보 | 결정 |
|---|---|---|
| 1 — 제출 전 권장 | R02 외기 read, R03 정차 명시 선호 안내, R25 로컬 기억 회상/기간 요약, R27 민감 저장 암호화 | 다음 구현 범위 협의의 기본 후보. 신규 권한/규칙/보안 migration은 별도 구현 Phase에서 |
| 2 — 시간/선행 검증 통과 시 | R01 NIGHT, R05 표준 위치, R06 로컬 지역, R08 도식, R11 앱 관측 추정, R15 사전 음성+focus, R26 무료 외형 적용 | 기술 실험 성공·의미/권리/정책 Gate 통과 전 활성화 금지 |
| 3 — 외부 Gate | R04 Seat, R07 외부 geocode, R10 POI, R12 authoritative continuous, R13 STT, R14 Pleos TTS, R18 Warning, R19 Control, R20 Navi, R24 DO | 계정/공식 계약/권한/정책/실차 중 필요한 Gate부터 |
| 4 — post-MVP | R09 실제 지도, R16 LLM, R17 backend, R21 사진/mobile, R22 결제, R28 cloud 음성 | 현재 제출 필수 범위에서 제외 |
| 5 — 제외 | R23 외부 IP | 오리지널 사용 지시 유지 |

## 25–26. 옵션 비교와 선택

| 판단 축 | A Current Conservative MVP | B Enhanced Local/Platform MVP | C Connected AI MVP |
|---|---|---|---|
| 차량 고유성 | 실제 read/Trip는 있으나 주요 Care 비활성 | 검증된 외기와 명시 comfort, 후속 NIGHT/추정 운전 연구로 보강 | 서버 표현만으로 차량 고유성 증가 보장 안 됨 |
| 기술 활용 | 기존 검증 경계 유지 | 표준 AAOS+deterministic/local 강화 | LLM/검색/음성 가능하지만 여러 계정·망 Gate |
| 시연 효과 | 안정적인 정차 기록/시즌·별도 Demo | 실제 센서/기억 연결을 사실대로 보여줄 수 있음 | 자유 대화는 크지만 latency·실패·개인정보 리스크 |
| 정책 위험 | 새 권한 적으나 암호화/DO/최종 에셋 미해결 | read-only/정차 경계로 관리 가능; 보안 보완 포함 | 외부 AI/음성/좌표/국외·콘텐츠·심사 조건 증가 |
| 남은 작업 | 최소, 서비스 원형 공백 큼 | 1차 소규모 후보+각각 회귀; 범위 확대 통제 가능 | 서버·키·인증·provider 계약/관측/장애 운영까지 큼 |
| AVD 검증 | 기존 증거 재사용 | 외기는 이미 일반 앱 관측 성공, 나머지는 Gate별 | 실제 계정/키·한국어 품질부터 미확정 |
| 권장 | 미선택 | **단일 추천** | 미선택 |

B안의 확정 기본 후보는 외기/정차 명시 선호/로컬 기억/저장 보안이다. NIGHT·위치·지역·앱 운전 추정·사전 음성은 **검증 통과 시에만 다음 작은 단위로 추가**한다. C의 cloud를 섞어 ‘B’라고 부르지 않는다. 공식 참가 규정상 마감은 11월15일로 표기되어 있어, 남은 시점에 외부 운영 체계 추가보다 차량 맥락·정차 경험을 좁혀 완성하는 편이 타당하다는 엔지니어 판단이다. 일정은 제출 전 재조회한다. [S66](PHASE8_5_SOURCE_REGISTER.md#S66).

## 27–30. Visual Design 이전 결정과 유지할 비활성

Visual Design 전에 확정할 것은 ①실제 차량 값/추정/사용자 입력 출처 라벨 ②외기 카드 ③명시 comfort를 기존 자동 Care와 구분하는 정차 경로 ④회상 가능한 기억의 범위 ⑤획득한 외형 적용 범위 ⑥음성 지원/실패 UX ⑦지역 데이터 없을 때 컬렉션의 빈 상태다. 실제 화면은 바꾸지 않았다.

| 복구가 승인되면 | 정보구조 영향 | 비활성 유지 조건 |
|---|---|---|
| 외기 read | 동행 홈의 실제 값 한 항목 추가 | capability/permission/error/stale는 미확인 표시 |
| 정차 comfort | 정차 설정/명시 요청·선호 확인 | 자동 HVAC/driver 인증/차량 제어로 표현 금지 |
| 로컬 회상/기간 요약 | 정차 Memory의 선택 기억·기간 | 미동의/삭제 기억 제외, LLM 요약 표기 금지 |
| NIGHT | 홈 최소 Care 표현/문구 검토 | 의미·전환·timestamp와 DO 전까지 실제 P1 비활성 |
| 위치/region | Memory/Collection에 실제 장소·최초 방문 | fresh fix/경계·권리 없으면 실제 엽서 미발급 |
| 앱 운전 추정 | Trip 시간과 다른 출처/coverage 라벨 | 주행 허가 전 주행 알림 금지; 피로/휴식 확증 금지 |
| 사전 음성 | 정차 음성 on/off·재생 상태 | focus 실패·제한/주행·취소 시 무음/텍스트 |
| 외형 | 정차 Collection→소유 확인→미리보기→적용 | 미보유/유료 미검증 팩 차단; 안전 동일 |

원래 Light/HVAC/Rest, 실제 지역 엽서의 Production disabled는 구현/검증 전 유지한다. Warning·Seat·Control·실제 음성·LLM·결제·사진도 구현된 것처럼 노출하지 않는다. Pleos/OEM에 필요한 확인은 Seat 의미, authoritative continuous, public warning codebook/차종, Vehicle/Control permission, SDK 계정/음성 모델, 내비 차종, DO/카테고리다. 표준 외기 read처럼 일반 앱에서 입증한 항목까지 일괄 계정 승인 대기로 돌리지는 않는다.

## 31–32. 제품과 Phase8 기준선 보존

조사 시작 시 tracked151 및 원전/과거 문서·검증530 파일 SHA256을 고정했다. 신규 문서 생성 후 **151/151, 530/530 동일**이다. branch main/HEAD7cdcb68은 읽기 전용으로 확인했으며 저장소 명령에만 safe.directory를 지정하고 global config는 바꾸지 않았다. git add/commit/push/branch 전환은 실행하지 않았다.

원전 hash: Development Spec `3c0ac4ce21d256545f76cc88bfaa94e673b8770193912352817a3bc5fff514cf`, Master Plan `b654d8c1531467ae4109cf24d00d6e6e660a32a57388299e44a1d08e05eb4eee`.

기존492 unique /516 executions/failure0/error0/skip0은 **역사적 결과**다. P8-16은 sync/설치 경로 회복 후 조건부 PASS이고 접근성은 PARTIAL, 사용자 인수는 PENDING이다. 사용자의 AVD 실행/사용 보고를 전체 사용자 인수·Market·DO·실차 PASS로 확대하지 않았다. 새 조사로 해당 기록을 덮어쓰지 않았다.

## 33. 다음 구현 범위 — 합의할 구체안

다음 단계 제안은 **B안 1차: 저장 보안 + 외기 read + 정차 명시 선호 안내 + 로컬 기억 회상**이다. 이것을 Phase1 등 과거 번호로 다시 시작하지 않으며 새 구현 Phase의 범위로 합의한다.

1. **R27 저장 보안:** 기존 동의/삭제·schema 데이터 보존을 전제로 Android Keystore/envelope 설계, 암호화 migration·키 무효화·손상/원자 교체 실패 검증. 평문 staging/로그 노출 없이 기존 모델 의미 유지. 사용자 데이터 삭제로 migration 성공을 가장하지 않는다.
2. **R02 외기 read:** production 전용 read adapter·permission/Capability/error/freshness·구독 해제 구현. Demo SIMULATED 분리, 실제 AVD callback/재연결·거절/unsupported·merged Manifest와 Release 격리 검증. 값은 F-03의 일부 입력일 뿐이다.
3. **R03 정차 명시 comfort:** 별도 사용자 선호 모델과 정차 요청 규칙을 먼저 승인 가능한 문구로 정의. stationary+UX allowed가 아니면 차단, front-left 라벨 유지, driverPresent를 만들지 않음. LLM/control 없는 ‘직접 조절’ 안내. 기존 자동 HvacRule 불변 회귀.
4. **R25 로컬 기억 회상:** 동의한 기억 한 건의 선택적 회상과 제한된 기간 사실 요약. 실제 존재하는 기록만, 별도 동의·삭제/generation·정차 gate 검증. 민감 원문 자동 낭독·자유 대화·새 cloud 없음.

각 단위는 관련 JVM 테스트와 실제 AVD E2E를 실행하고 명령/실패를 기록한 뒤 제품 빌드·lint·기존 핵심 회귀를 검증한다. **지금은 이 구현을 시작하지 않았다.** 1차 완료와 사용자 검수 후 Tier2에서 NIGHT 전환/시간 계약, foreground 위치 probe, licensed 지역 resolver, 앱 관측 운전 추정 중 하나를 선택한다. 외부 계정/정책 승인 대기 기능은 계속 닫아 둔다.
