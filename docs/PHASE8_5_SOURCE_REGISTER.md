# Phase 8.5 공식 출처 및 증거 경계

조회일: 2026-11-09 KST. 문서 존재/아티팩트 다운로드/개인 계정 사용권/일반 앱 실행/의미 검증/심사 승인은 서로 다른 증거다. 이번에 로그인·약관 동의·키 생성·API 유료 호출·외부 문의 발송을 하지 않았다.

원전: 개발 명세 v1.0을 MVP 우선순위 기준으로, Master Plan을 의도/장기 확장 기준으로 읽었다. 두 원전 및 이전 Phase7/7.5/8 기록은 Git 제외된 `development/`에 보존되어 있다. 제출 문서만 있는 checkout에서 이 경로가 없을 수 있으며 파일을 새로 지어내지 않는다. 기존 공개 검증 요약은 [VALIDATION_SUMMARY](VALIDATION_SUMMARY.md), 현재 범위는 [MVP_SCOPE](MVP_SCOPE.md)를 참조한다.

출처별 아래 문장은 관측 범위의 요약이다. 실패한 URL이나 검색 결과를 API 호출 성공 근거로 쓰지 않았다. Gemini 공식 문서는 web reader가 text/markdown을 처리하지 못해 공개 HTTPS 원문을 내려받아 읽었다. 원문 cache는 로컬 `development/phase8-5/source-cache/`이며 hash는 [검증 JSON](verification/phase8-5-artifacts.json)에 있다. iOS 사진 본문·국토부 본문·도로공사 PDF는 미검증 경계로 남겼다.

<a id="S01"></a>

## S01 — AAOS 표준 우선 / SDK 정책

[AAOS 표준 우선 / SDK 정책](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/pleos-connect-sdk-api) — 동일 기능은 AAOS 우선; 외기 온도 예시. 허가된 Android API가 곧 OEM 우회라는 해석은 부정확하다.

<a id="S02"></a>

## S02 — 플랫폼 접근 구조

[플랫폼 접근 구조](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/platform-api-architecture) — 공식 플랫폼 API 경유; 직접 하드웨어 접근 금지, 네트워크 장애 설계.

<a id="S03"></a>

## S03 — 호환성과 가용성

[호환성과 가용성](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/api-compatibility-availability) — 차종/옵션/내비 탑재 여부에 따라 지원 다름. full compatibility 문구는 계정 승인 증거가 아니다.

<a id="S04"></a>

## S04 — SDK 신청 / 계정

[SDK 신청 / 계정](https://document.pleos.ai/docs/pleos-only/create-my-project) — State/Navi 신청 없이 가능 안내와 Fused/TTS/STT/Control 준비 중 안내가 공존. 개인 계정 화면은 미접근.

<a id="S05"></a>

## S05 — SDK 릴리즈

[SDK 릴리즈](https://document.pleos.ai/docs/pleos-only/release-note/sdk-release-note) — Vehicle2.3.2/Navi3.0.5/Fused1.1.3/TTS·STT3.3.1. 릴리즈 노트만으로 Seat 의미 수정 확인 불가.

<a id="S06"></a>

## S06 — Emulator 2.1.0 릴리즈

[Emulator 2.1.0 릴리즈](https://document.pleos.ai/docs/pleos-only/release-note/emulator-release-note) — 2026-10-19, LLM SDK 및 CRN 제공 종료. Gleo 탑재와 범용 LLM SDK 가용성은 다르다.

<a id="S07"></a>

## S07 — 앱 카테고리

[앱 카테고리](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/app-category) — Mobility 후보를 설명하는 근거. 본 앱 승인/DO를 보장하지 않음.

<a id="S08"></a>

## S08 — 지역별 주행 제한

[지역별 주행 제한](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/driving-restiction-policy) — DO 미선언 주행 차단; 지역별 정책. 정차 콘텐츠에는 fail-closed 유지.

<a id="S09"></a>

## S09 — 주행 제한 구현

[주행 제한 구현](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide) — CarUxRestrictions 현재 값과 변화 관측. DO 선언 자체가 심사 승인 아님.

<a id="S10"></a>

## S10 — 성능 / Vehicle permission 심사

[성능 / Vehicle permission 심사](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-functionality-performance-stability) — Vehicle SDK permission은 Market 사전 협의·승인 필요. 일반 API 기술 성공과 배포 심사 구분.

<a id="S11"></a>

## S11 — 보안

[보안](https://document.pleos.ai/docs/pleos-only/app-registration-guide/app-security-guide) — 민감정보는 내부/임시 저장도 앱 수준 암호화, Keystore, HTTPS/TLS1.2+, 최소 권한. non-GAS 명시.

<a id="S12"></a>

## S12 — 계정 / 개인정보 / 외부 전송

[계정 / 개인정보 / 외부 전송](https://document.pleos.ai/docs/pleos-only/vehicle-app-planning-guide/security-privacy) — 차량 공유·운전자 변경·PII 최소화·가명화. 외부 API 일괄 승인 문서 아님.

<a id="S13"></a>

## S13 — 제출 메타데이터

[제출 메타데이터](https://document.pleos.ai/docs/pleos-only/distribute/app-submission-guide) — 개인정보처리방침 URL, 위치/GPS 사용 및 조건부 위치약관, permission/프로젝트 정보 확인.

<a id="S14"></a>

## S14 — 콘텐츠 / IP / AI 콘텐츠

[콘텐츠 / IP / AI 콘텐츠](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-content-safety) — 저작권 증빙 및 AI/UGC 필터·신고 체계 요구. 특정 결제 SDK 제공을 보장하지 않음.

<a id="S15"></a>

## S15 — 운전자 주의 분산 심사

[운전자 주의 분산 심사](https://document.pleos.ai/docs/pleos-only/distribute/app-review-guide/review-driver-distraction-elements) — 가이드 준수 후에도 추가 위험으로 거절 가능. 본 앱 주행 허가로 해석하지 않음.

<a id="S16"></a>

## S16 — Audio Focus

[Audio Focus](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/audio-focus-development) — 차량 focus matrix, 요청/손실/duck 처리. 일반 앱의 임의 SAFETY/EMERGENCY 채널 사용 근거 아님.

<a id="S17"></a>

## S17 — Suspend Audio Focus

[Suspend Audio Focus](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/audio-focus-development-guide/suspend-mode-audio-focus) — Suspend 전 정지·반납, suspend 중 요청 금지, resume 시 재평가.

<a id="S18"></a>

## S18 — 음성 선행 묵음

[음성 선행 묵음](https://document.pleos.ai/docs/pleos-only/vehicle-app-developer-guide/voice-guidance-preroll-silence-guide) — TTS/사전 녹음 모두 150–200ms 권장; focus/출력 준비 대체 아님.

<a id="S19"></a>

## S19 — Gleo 통합

[Gleo 통합](https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts) — 등록한 앱 기능 schema와 action/callback. 범용 자유 대화 LLM inference API와 다름.

<a id="S20"></a>

## S20 — NaviHelper 도입

[NaviHelper 도입](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro) — 내비 앱을 통한 경로/경유지 연계; 지도 엔진·POI 검색 데이터 공급과 다름.

<a id="S21"></a>

## S21 — NaviHelper 좌표

[NaviHelper 좌표](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk) — ai.pleos.playground:NaviHelper:3.0.5와 공식 Maven repo.

<a id="S22"></a>

## S22 — NaviHelper 권한

[NaviHelper 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions) — NAVI_ROUTE / NAVI_ROUTE_SEARCH / NAVI_CUSTOM_MAP을 기능별 분리.

<a id="S23"></a>

## S23 — FusedLocation 도입

[FusedLocation 도입](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/intro) — 지원 여부·last known·updates. last known은 fresh fix 아님.

<a id="S24"></a>

## S24 — FusedLocation 좌표

[FusedLocation 좌표](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup) — ai.pleos.playground:FusedLocation:1.1.3.

<a id="S25"></a>

## S25 — FusedLocation 권한

[FusedLocation 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/FusedLocation/declaring-permissions) — pleos.car.permission.FUSED_LOCATION dangerous. Google Play Services Fused와 다른 서비스.

<a id="S26"></a>

## S26 — Pleos TTS

[Pleos TTS](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro) — ON_DEVICE와 server/hybrid 구분, server credentials. ON_DEVICE 등록 불필요와 SDK 계정 가용성은 별개.

<a id="S27"></a>

## S27 — Pleos TTS 좌표

[Pleos TTS 좌표](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/importing-sdk) — ai.pleos.playground:TextToSpeech:3.3.1.

<a id="S28"></a>

## S28 — Pleos TTS 권한

[Pleos TTS 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/TextToSpeech/declaring-permissions) — pleos.car.permission.TTS_SERVICE.

<a id="S29"></a>

## S29 — Pleos STT

[Pleos STT](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro) — ON_DEVICE/SERVER/HYBRID, 텍스트 결과일 뿐 실행 권한 아님.

<a id="S30"></a>

## S30 — Pleos STT 좌표

[Pleos STT 좌표](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/importing-sdk) — ai.pleos.playground:SpeechToText:3.3.1.

<a id="S31"></a>

## S31 — Pleos STT 권한

[Pleos STT 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/SpeechToText/declaring-permissions) — pleos.car.permission.STT_SERVICE.

<a id="S32"></a>

## S32 — Vehicle 공개 권한

[Vehicle 공개 권한](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/Vehicle/vehicle-permission) — CAR_* read와 CONTROL_CAR_* write 구분. private capability/aggregate listener는 후보에서 제외.

<a id="S33"></a>

## S33 — Android 차량 property

[Android 차량 property](https://developer.android.com/reference/android/car/VehiclePropertyIds) — NIGHT_MODE/ENV_OUTSIDE_TEMPERATURE global read; EXTERIOR_ENVIRONMENT normal. 최신 Seat 관련 권한을 API34에 소급하지 않음.

<a id="S34"></a>

## S34 — CarPropertyManager callback

[CarPropertyManager callback](https://developer.android.com/reference/android/car/hardware/property/CarPropertyManager) — 등록 후 초기 current-value 이벤트와 실제 변화 이벤트 구분. ON_CHANGE 오래된 update timestamp를 곧바로 불량 센서로 단정하지 않음.

<a id="S35"></a>

## S35 — Android LocationManager

[Android LocationManager](https://developer.android.com/reference/android/location/LocationManager) — 공개 Android 위치 API; fine/coarse·provider·취소/갱신. background는 별도 범위.

<a id="S36"></a>

## S36 — Android SpeechRecognizer

[Android SpeechRecognizer](https://developer.android.com/reference/android/speech/SpeechRecognizer) — RECORD_AUDIO, main thread/destroy, 원격 전송 가능, 상시 청취 용도 아님. 설치 여부/언어/오프라인 별도.

<a id="S37"></a>

## S37 — Android TTS

[Android TTS](https://developer.android.com/reference/android/speech/tts/TextToSpeech) — 비동기 초기화, engine·언어 지원 판별. 특정 AVD에서의 init ERROR가 모든 차량 미지원 근거 아님.

<a id="S38"></a>

## S38 — Android Audio Focus

[Android Audio Focus](https://developer.android.com/media/optimize/audio-focus) — API26 AudioFocusRequest; Android15에서 target35+ top app/FGS 요건. 현재 API34 관측과 미래 런타임 조건 분리.

<a id="S39"></a>

## S39 — AAOS Audio Focus

[AAOS Audio Focus](https://source.android.com/docs/automotive/audio/audio-focus) — AAOS 다중 focus/interaction matrix. 휴대폰 단일 focus 설명을 차량 정책으로 그대로 전용하지 않음.

<a id="S40"></a>

## S40 — Kakao 로컬 REST

[Kakao 로컬 REST](https://developers.kakao.com/docs/ko/local/dev-guide) — 한국 행정구역 reverse geocoding / keyword·category POI. 문서 contract만 확인, 실제 검색 품질 미측정.

<a id="S41"></a>

## S41 — Kakao 쿼터 / 요금

[Kakao 쿼터 / 요금](https://developers.kakao.com/docs/ko/getting-started/quota) — 월3M 공통, 해당 geocode/POI 일100k, 무료 첫 활성 앱 조건. 초과 geocode0.5원/POI2원; 사용 전 재확인.

<a id="S42"></a>

## S42 — Kakao 이용 정책

[Kakao 이용 정책](https://developers.kakao.com/docs/ko/kakaomap/common) — 활성화/쿼터 조건. 오프라인 데이터 재배포·영구 캐시·자동차 임베디드 허용 범위는 별도 확인 필요.

<a id="S43"></a>

## S43 — 날씨 / 일출일몰 API

[날씨 / 일출일몰 API](https://open-meteo.com/en/docs) — forecast REST; 지역 예보와 차량 실제 센서 구분. sunrise/sunset도 관측 저조도 아님.

<a id="S44"></a>

## S44 — Open-Meteo 서비스 요금

[Open-Meteo 서비스 요금](https://open-meteo.com/en/pricing) — 무료 평가/비상업과 상업 구독 구분; 서비스 약관과 데이터 귀속 조건 재확인.

<a id="S45"></a>

## S45 — 천문 계산 한계

[천문 계산 한계](https://gml.noaa.gov/grad/solcalc/calcdetails.html) — Meeus 기반 계산 참고; NOAA calculator 유지보수 종료, 대기/위도에 따른 오차. 인증된 안전 센서가 아님.

<a id="S46"></a>

## S46 — MapLibre Android release

[MapLibre Android release](https://github.com/maplibre/maplibre-native/releases/tag/android-v13.7.1) — 13.7.1 release 및 Maven POM/AAR 직접 확인. API34 렌더링은 미실행.

<a id="S47"></a>

## S47 — MapLibre license

[MapLibre license](https://raw.githubusercontent.com/maplibre/maplibre-native/android-v13.7.1/LICENSE.md) — BSD-2-Clause; 실제 tile/style/font/POI 데이터 권리는 별도.

<a id="S48"></a>

## S48 — OSM 데이터 저작권

[OSM 데이터 저작권](https://www.openstreetmap.org/copyright) — ODbL/귀속. 자유 데이터와 공용 서버 무제한 이용은 다름.

<a id="S49"></a>

## S49 — Nominatim 공용 서버 정책

[Nominatim 공용 서버 정책](https://operations.osmfoundation.org/policies/nominatim/) — 1 req/s 상한·식별 UA·귀속/캐시·provider 교체. autocomplete/체계적 grid reverse/POI 수집 금지; 본 MVP 경로 추적용 비추천.

<a id="S50"></a>

## S50 — SGIS 경계 / 좌표계

[SGIS 경계 / 좌표계](https://sgis.mods.go.kr/developer/html/newOpenApi/api/dataApi/addressBoundary.html) — OpenAPI3 경계·reverse geo; WGS84 전용 endpoint와 UTM-K 구분. 배포 가능한 경계 파일과 라이선스 확보는 미완료.

<a id="S51"></a>

## S51 — 국토부 행정구역도 데이터

[국토부 행정구역도 데이터](https://www.data.go.kr/data/15059008/openapi.do) — 공식 검색 색인의 API 목록/무료/출처표시 1유형. 본문 Markdown 렌더 실패; 실제 파일·CRS·오프라인 패키징 권리는 미검증.

<a id="S52"></a>

## S52 — Gemini 모델

[Gemini 모델](https://ai.google.dev/gemini-api/docs/models) — 공식 Markdown HTTP 읽기 성공. 3.5 Flash-Lite Stable 표기; 사용자 계정 inference 성공은 미검증.

<a id="S53"></a>

## S53 — Gemini 키

[Gemini 키](https://ai.google.dev/gemini-api/docs/api-key) — 모바일 client에 secret 노출 금지, backend proxy 권고. 키 생성/호출 안 함.

<a id="S54"></a>

## S54 — Gemini 가격

[Gemini 가격](https://ai.google.dev/gemini-api/docs/pricing) — 3.5 Flash-Lite standard text 입력$0.30/output$2.50 per1M token. 2.5 Flash deprecation 안내 확인. 실행 모델 최종 고정 전 재조회.

<a id="S55"></a>

## S55 — Gemini 데이터 약관

[Gemini 데이터 약관](https://ai.google.dev/gemini-api/terms) — 무료 학습/인적 검토 가능·개인정보 전송 금지 안내와 유료 학습 제외/제한 보관 구분. 보관 일수/리전 계약 확정 전 민감 회고 전송 금지.

<a id="S56"></a>

## S56 — On-device ML Kit Prompt

[On-device ML Kit Prompt](https://developers.google.com/ml-kit/genai/prompt/android) — Beta/AICore·기기 조건. non-GAS Pleos API34 AVD에서 동작 미확인; SDK 좌표/버전은 채택하지 않음.

<a id="S57"></a>

## S57 — Cloud STT 언어

[Cloud STT 언어](https://docs.cloud.google.com/speech-to-text/docs/speech-to-text-supported-languages) — V2 ko-KR 모델/리전별 지원. 차량 잡음 정확도·지연은 미측정.

<a id="S58"></a>

## S58 — Cloud TTS 언어

[Cloud TTS 언어](https://docs.cloud.google.com/text-to-speech/docs/list-voices-and-types) — ko-KR Standard/Neural2/Chirp voice 목록. 캐릭터/IP voice 이용권 별도.

<a id="S59"></a>

## S59 — Cloud STT 비용

[Cloud STT 비용](https://cloud.google.com/speech-to-text/pricing) — V2 standard 첫500k분 $0.016/분, V1 무료량과 혼용 금지. 리전·계약·로그 옵션 재확인.

<a id="S60"></a>

## S60 — Cloud TTS 비용

[Cloud TTS 비용](https://cloud.google.com/text-to-speech/pricing) — Standard 4M문자 무료 후$4/1M; Neural2 1M 후$16/1M. billing 계정 필요, 지역/과금 재확인.

<a id="S61"></a>

## S61 — Android Photo Picker

[Android Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker) — 사용자가 고른 사진만 읽는 휴대폰 경로; 차량에서 휴대폰 갤러리에 바로 접근하는 API 아님.

<a id="S62"></a>

## S62 — iOS 사진 선택

[iOS 사진 선택](https://developer.apple.com/documentation/photokit/selecting-photos-and-videos-in-ios) — 공식 페이지 JS 제한으로 본문 미검증. PHPicker/PhotosPicker 정확 OS·메타데이터 조건은 UNKNOWN으로 남김.

<a id="S63"></a>

## S63 — 제한 입력 기기 OAuth

[제한 입력 기기 OAuth](https://developers.google.com/identity/protocols/oauth2/limited-input-device) — 외부 기기 코드 인증 패턴 참고; 모든 공급자 지원/QR 주행 허용을 뜻하지 않음.

<a id="S64"></a>

## S64 — 공모전 개발 환경

[공모전 개발 환경](https://appdevchallenge.auto-hmg.io/guide/development) — 공식 SDK/에뮬레이터 안내. 외부 LLM 일괄 허용 조항 확인 못함.

<a id="S65"></a>

## S65 — 공모전 FAQ

[공모전 FAQ](https://appdevchallenge.auto-hmg.io/faq) — 공식 지원/평가 참고. 외부 AI 사용 승인을 부여하지 않음.

<a id="S66"></a>

## S66 — 공모전 참가 규정

[공모전 참가 규정](https://appdevchallenge.auto-hmg.io/guide/rules) — 2026-11-15 접수 마감 표기, 후속 PT 전 고도화 허용. 시점에 민감하므로 제출 전 재확인.

## 로컬 증거

- 일반 UID 독립 testOnly probe: config/get/callback, Android STT availability, Android TTS 초기화. 위치 fix/녹음/음성 재생/focus 요청/차량 쓰기는 하지 않았다.
- 신규 POM/AAR 5종: 공식 공개 저장소에서 직접 다운로드, minSdk/manifest permission/POM license 확인. 제품 dependency에 추가하지 않았다.
- 보존: 조사 시작 시 tracked151 파일과 역사530 파일(원전 포함)의 SHA256을 고정했다. 결과는 신규 JSON에서 다시 비교한다.
- 기존 Phase8 기록은 고유492/실행516, coldboot 조건부·접근성 PARTIAL·사용자 인수 PENDING이다. 이번 probe를 과거 테스트 재실행으로 세지 않는다.
