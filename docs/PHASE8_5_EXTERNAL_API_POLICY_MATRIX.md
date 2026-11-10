# Phase 8.5 — 외부 API · 권한 · 보안 · 의존성 검토

조회일: 2026-11-09 KST. **제품 연동은 하지 않았다.** 아래는 도입 후보에 대한 조사이며 법적 판단·사용자 계정 승인·Market 승인 결과가 아니다. 출처 ID는 [공식 출처 목록](PHASE8_5_SOURCE_REGISTER.md)에 연결된다.

## 허용성 판단

| 대상 | 공식 자료에서 확인한 범위 | 아직 확인되지 않은 범위 | 이번 결정 |
|---|---|---|---|
| AAOS 표준 API | 같은 기능은 AAOS 우선; 플랫폼 API 경유 | 개별 차종 지원·민감 권한·실차 의미 | 표준 API를 일괄적인 OEM 우회로 보지 않는다. 일반 앱 권한과 capability로 검증 |
| 일반 외부 서버/API | 보안 가이드는 외부 통신의 HTTPS/TLS·신뢰 대상·최소 데이터 조건을 규정 | Roadmate의 특정 서버·용도·국외 처리·제출 시 계정 제공 조건 | 일괄 금지 근거도 일괄 승인 근거도 확보하지 못함. 구체 서비스별 확인 필요 |
| 외부 LLM | AI 콘텐츠 필터·신고 체계가 심사 항목; AVD2.1.0 LLM SDK 종료 | 외부 생성형 AI의 본 앱 사용 승인·리전·보관·미성년자 처리 | 정차 표현 보조만 후보; 주행 안전/제어 의사결정에 사용 금지 |
| 위치 | 표준 위치 API와 Pleos Fused API 문서; 제출 시 위치/GPS·위치약관 필드 | 계정/차종별 이용권·일반 앱 fresh fix·자동차 임베디드 지도 이용권 | fresh fix와 동의·약관을 모두 갖추기 전 실제 방문/엽서 발급 금지 |
| Vehicle SDK | 필요한 permission은 Market 사전 협의·승인 후 선언 | 본 앱 read/control permission 승인 | 기존 AVD read 성공이 배포 승인을 뜻하지 않음 |
| DO/주행 | 지역별 UX 제한과 앱별 심사; 미선언 앱 Drive 차단 | Mobility 분류·최소 Care 발화/상호작용 허용 | DO 미선언 및 정차 콘텐츠 잠금 유지 |

근거: [S01](PHASE8_5_SOURCE_REGISTER.md#S01), [S02](PHASE8_5_SOURCE_REGISTER.md#S02), [S08](PHASE8_5_SOURCE_REGISTER.md#S08), [S10](PHASE8_5_SOURCE_REGISTER.md#S10), [S11](PHASE8_5_SOURCE_REGISTER.md#S11), [S13](PHASE8_5_SOURCE_REGISTER.md#S13), [S14](PHASE8_5_SOURCE_REGISTER.md#S14), [S64–66](PHASE8_5_SOURCE_REGISTER.md#S64).

“인터넷이 되므로 허용”, “금지 문구를 못 찾았으므로 허용”, “문서가 있으므로 계정 사용 가능”으로 결론 내리지 않았다. 공개 문서상 State/Navi는 별도 신청 없이 사용 가능하지만 Fused/TTS/STT/Control은 준비 중 안내다. 동시에 TTS/STT ON_DEVICE는 앱 등록 없이 사용 가능하다고 설명한다. **SDK 배포, 앱 등록, 계정 entitlement, 서비스 모드라는 서로 다른 조건**으로 기록하며 개인 계정 상태를 추정하지 않는다. [S04](PHASE8_5_SOURCE_REGISTER.md#S04), [S26](PHASE8_5_SOURCE_REGISTER.md#S26), [S29](PHASE8_5_SOURCE_REGISTER.md#S29).

## 실제 확인한 패키지 / 의존성

Pleos 공식 repository: `https://nexus-playground.pleos.ai/repository/maven-releases/`. MapLibre는 Maven Central이다. 5개 POM과 5개 AAR를 공개 HTTPS로 다운로드하고 SHA256·Manifest를 읽었다. 다운로드 성공은 인증/계약/permission 승인 증거가 아니다. 처음 sandbox 네트워크가 WinError10013으로 실패한 기록도 별도 보존했다. 제품 Gradle에는 추가하지 않았다.

| 후보 | 정확한 artifact/version | license | AAR minSdk | API34 / 현재 프로젝트 호환성 | 상태 |
|---|---|---|---|---|---|
| NaviHelper | `ai.pleos.playground:NaviHelper:3.0.5` | POM license 없음; 계약 UNKNOWN | 23 | 플랫폼 API34용 공식 SDK 후보; bind/경로 미실행 | 다운로드만 VERIFIED |
| Pleos Fused | `ai.pleos.playground:FusedLocation:1.1.3` | POM license 없음; 계약 UNKNOWN | 23 | AVD service 있음; 일반 앱 fix/권한/계정 미검증 | 다운로드만 VERIFIED |
| Pleos TTS | `ai.pleos.playground:TextToSpeech:3.3.1` | POM license 없음; 계약 UNKNOWN | 23 | SDK 초기화/ko-KR/실제 재생 미실행 | 다운로드만 VERIFIED |
| Pleos STT | `ai.pleos.playground:SpeechToText:3.3.1` | POM license 없음; 계약 UNKNOWN | 23 | SDK 인식/취소/ko-KR 미실행 | 다운로드만 VERIFIED |
| MapLibre Android | `org.maplibre.gl:android-sdk:13.7.1` | BSD-2-Clause; 데이터 별도 | 23 | 현재 Kotlin2.0.0 대 POM stdlib2.2.10/coroutines1.10.2. API34·AGP/NDK·렌더·Compose 통합 미검증 | 채택 보류 |
| AAOS read | `android.car` 플랫폼 SDK / `useLibrary("android.car")` | Android 플랫폼·SDK 조건 | 프로젝트28 유지; 해당 property는 API34 public jar에서 컴파일 | 별도 probe compile34/target34 성공; 제품 compile/target35 불변 | get/callback만 실행 |
| LocationManager / SpeechRecognizer / TextToSpeech / AudioManager | Android framework, Maven artifact N/A | Android 플랫폼·엔진 조건 | API별로 다름; 프로젝트28 유지 | API34에 API 존재. 서비스/모델/언어·실행은 별도 | 위치·녹음·focus NOT_RUN, TTS ERROR |
| REST: Kakao / Open-Meteo / Gemini / Cloud Speech | Android Maven artifact N/A. 공식 HTTPS REST 사용 후보 | 공급자 서비스 약관 | Android HTTPS 사용 가능성과 차량 승인 별도 | 앱 HTTP 구현/키/백엔드/API 실호출 미구현 | 문서만 확인 |
| 로컬 천문 계산/행정경계 매핑 | 아직 선택한 라이브러리 없음; artifact/version N/A | 알고리즘 참고와 데이터/코드 재배포 권리 별도 | 순수 Kotlin 후보; 구현 없음 | JVM 검증 가능하지만 아직 NOT_RUN | 이름/좌표를 추측해 추가하지 않음 |
| 모바일 사진 | Android Photo Picker/플랫폼, iOS PhotoKit 후보 | 플랫폼/선택 데이터 권리 | Android fallback 문서상 ACTION_OPEN_DOCUMENT API19; iOS 정확 요건 UNKNOWN | 차량에서 phone gallery 자동 접근 아님 | 별도 모바일 범위 |
| Google Play Services 위치 / Play Billing | **의존성 선택 안 함** | 해당 서비스/계약 별도 | UNKNOWN | Pleos non-GAS이고 GMS 가용성 증거 없음 | 기본 대안으로 가정하지 않음 |
| 온디바이스 LLM | ML Kit Prompt 문서상 Beta 후보; artifact/version 채택 안 함 | 모델/기기 조건 별도 | UNKNOWN | AICore/모델·RAM·지원 하드웨어·한국어 품질 미확인 | post-MVP, 즉시 복구 대안 아님 |

minSdk23은 **AAR 내부 Manifest 관측**이다. Pleos 차량 플랫폼의 공식 지원 최소 버전이나 통합 성공을 23으로 선언한 것이 아니다. 프로젝트 min28·compile/target35·AGP8.8.2·Kotlin2.0.0·JDK21.0.4·Wrapper8.10.2는 그대로다.

새 artifact의 병합 권한도 중요하다. Fused AAR에는 `ai.pleos.playground.service.location.fused.FUSED_LOCATION_BINDING`, TTS/STT AAR에는 `android.permission.INTERNET`이 있다. 이 binding 권한을 앱이 임의로 대체 선언하지 않는다. MapLibre AAR에는 INTERNET/ACCESS_NETWORK_STATE/ACCESS_WIFI_STATE/ACCESS_COARSE_LOCATION/ACCESS_FINE_LOCATION이 있어, 위치 기능을 안 쓴다는 이유만으로 최소 권한이라고 간주할 수 없다. 향후 통합 시 최종 merged Manifest를 검증한다. 원본 hash와 permission 목록: [새 검증 JSON](verification/phase8-5-artifacts.json).

근거: [S21–31](PHASE8_5_SOURCE_REGISTER.md#S21), [S33–38](PHASE8_5_SOURCE_REGISTER.md#S33), [S46–47](PHASE8_5_SOURCE_REGISTER.md#S46), [S56](PHASE8_5_SOURCE_REGISTER.md#S56).

## 권한을 분리해야 하는 경계

| 기능 | 확인된 권한/조건 | 사용하지 않는 우회 |
|---|---|---|
| NIGHT/외기 | `android.car.permission.CAR_EXTERIOR_ENVIRONMENT`, normal; property config/area/status 별도 | root/shell identity/privileged Vehicle API |
| Android 위치 | foreground fine/coarse runtime permission; 정밀도·현재 위치 필요성 각각 설명 | background 상주 수집·location toggle 강제 변경 |
| Pleos Fused | `pleos.car.permission.FUSED_LOCATION`, dangerous + SDK binding/계정/지원 여부 | Google Fused와 혼동·privacy popup으로 권한 획득 간주 |
| Android STT | `android.permission.RECORD_AUDIO`, runtime; recognition service/queries | STT 결과를 차량 제어 승인으로 취급·상시 청취 |
| Pleos STT/TTS | `pleos.car.permission.STT_SERVICE` / `pleos.car.permission.TTS_SERVICE`; account/mode 별도 | ON_DEVICE 문서만으로 모든 계정/모델 가능 선언 |
| Navi | `pleos.car.permission.NAVI_ROUTE`, `NAVI_ROUTE_SEARCH`, `NAVI_CUSTOM_MAP` 기능별 | 목적지 읽기 승인을 경유지 쓰기 승인으로 확대 |
| Seat | Pleos `pleos.car.permission.CAR_SEATS`는 API별 annotation 재검증 필요. AAOS 최신 READ_CAR_SEATS 설명을 API34에 소급 불가 | Console와 반대 Boolean을 코드에서 반전 |
| Safety | 공개 개별 API의 RequiresPermission과 capability; Vehicle permission 심사 | private aggregate capability/listener·privileged 표준 신호 |
| Control | 정확한 write API·permission·capability·사용자 승인·actual result 모두 필요 | LLM/API 서버가 SDK write 직접 호출 |
| 인터넷 | `android.permission.INTERNET`은 통신 기술 권한 | 데이터 외부 전송/LLM/개인정보 동의를 대체 |
| AudioFocus | API26+ 공개 request/abandon + 정확한 AudioAttributes | 앱의 P1 등급을 system SAFETY/EMERGENCY usage 권한으로 간주 |

현재 제품에는 신규 exterior/location/mic/internet/Seat/control/DO를 추가하지 않았다. probe의 normal exterior permission은 **별도 testOnly package에만** 있다.

## 전송/계정/보관 프로파일

복구 표의 각 후보는 아래 P0–P7 중 하나를 지정한다. 이는 향후 채택 시의 제한 설계이며 현재 전송 사실이 아니다. **이번 조사에서 차량 위치 좌표·음성·회고·차량 이력을 외부 API에 보내지 않았다.** 공개 문서·라이브러리 다운로드만 수행했다.

| Profile / 대상 | location sent? | voice sent? | reflection sent? | vehicle state sent? | account / key | backend | retention / 삭제 |
|---|---|---|---|---|---|---|---|
| P0 로컬 읽기/계산/기억/사전 음성 | 아니오 | 아니오 | 아니오 | 아니오 | 불필요 | 불필요 | 동의 범위 로컬; 기억/Trip/진행도 독립 보존 정책 유지. 민감 저장 암호화 보완 필요 |
| P1 위치 provider·로컬 지역/내비 | provider/내비 동작에 따라 가능; 로컬 resolver는 전송 없음 | 아니오 | 아니오 | provider telemetry UNKNOWN, 최소화 | Android 별도 개발자 key 없음; Pleos 계정 조건 별도 | 로컬 resolver 불필요 | raw route 기본 미저장; age/accuracy/region만 최소화. provider 보관 UNKNOWN |
| P2 Kakao 주소/POI | 좌표·검색어/반경 전송 | 아니오 | 아니오 | 속도/기어/VIN 전송 불필요 | 개발자 앱/REST key 필요; 운전자 Kakao 로그인은 본 검색 경로에 불필요 | key 보호/쿼터 proxy 권장 | 서버 좌표 로그 금지; provider 보관/캐시/재배포 기간 UNKNOWN, 계약 전 영구 cache 금지 |
| P3 지도 tile/style/font | tile/bbox 요청에서 지역 추론 가능; local tile면 전송 없음 | 아니오 | 아니오 | 불필요 | MapLibre renderer key 없음; tile provider 조건 별도 | 자체 tile 선택 시만 | 캐시/오프라인 다운로드·귀속/삭제는 tile 계약. renderer BSD가 데이터 권리 아님 |
| P4 음성 엔진·Cloud STT/TTS | 불필요, 전송 금지 설계 | STT 원음은 engine/server 선택에 따라 전송; TTS는 텍스트 전송 | 기본 고정 안내만; 회고 낭독은 별도 동의 없으면 미전송 | 최소 고정 안내 외 원시 차량 데이터 전송 금지 | Android engine 조건별; Pleos server ID/secret; Google Cloud 계정/billing·서버 credential | Cloud 사용 시 secret 보호 backend | provider별 data logging/region/보관 일수 UNKNOWN. 명시 push-to-talk·즉시 취소, raw 녹음 로컬 보관 안 함 설계 |
| P5 LLM/Gemini | 기본 미전송; 위치 필요 기능은 coarse region 별도 동의 | text-only안은 원음 미전송 | 사용자가 선택한 최소 문장만 별도 동의 후 | VIN/원시 warning/control 데이터 금지; 필요 시 최소 검증 사실만 | 개발자 project/key·유료 billing 검토; 운전자 로그인 필수 아님 | production secret proxy 필요 | 무료는 학습/인적 검토 가능 및 민감 PII 금지 안내. 유료는 학습 제외이나 제한적 보관; 정확 보관 기간/리전 UNKNOWN. 앱 삭제가 provider 즉시 삭제 보장은 아님 |
| P6 cloud memory/계정/결제 | 동기화 scope에 따라 가능; 기본 제외 | 불필요 | 선택한 기억만; 기본 제외 | 최소 진행도; 원시 차량 이력 기본 제외 | 사용자 소유권/개발자 billing/서버 secret 필요 | 필요 | 처리자·국외 이전·보관 기한·탈퇴/삭제 전파/환불 증빙 정책 미확정. 제안 단계 |
| P7 사진/mobile/web 업로드 | EXIF 위치 포함 가능 → 업로드 전 제거 기본 | live photo 등 포함 가능 → 지원 형식 제한/명시 선택 | 캡션 선택 시만 | 불필요 | 교차 기기 소유권·만료 pairing 필요; 앱에 영구 secret 금지 | 수동 파일 전송 외 cross-device sync는 필요 | 선택한 사진만, 원본/thumbnail/cache 모두 보관·삭제·로그아웃 규칙. 얼굴/가족·아동 정보 처리 별도 |

Open-Meteo를 선택할 경우 P2와 동일한 좌표 전송 통제가 필요하지만 Kakao key/보관 약관을 공유하지 않는다. 무료 endpoint와 상업 customer endpoint·key 조건이 다르다. 위치+천문 **로컬 계산은 P0/P1**이고 외부 일몰 API 호출은 P2다.

## 한국 서비스 적용과 비용

가격은 2026-11-09 조회 스냅샷이다. 청구 지역·세금·계정·새 모델·약관은 달라질 수 있어 **모든 실도입 전 CURRENT_PRICE_RECHECK_REQUIRED**. 계정/키를 만들거나 실제 과금 호출하지 않았다. 비용 때문에 기술적 불가능으로 분류하지 않는다.

| 공급자/안 | 한국/언어 품질 근거 | 무료/과금 단위 | rate limit / 미확정 | 장애/채택 판단 |
|---|---|---|---|---|
| Android Location / 로컬 경계 | provider와 행정 경계 데이터가 별개 | 플랫폼 호출 종량 없음; 데이터/개발 비용 별도 | fresh fix/차종/정확도 NOT_TESTED | 외부 없이 가능성이 있으나 실제 위치 성공 전 엽서 발급 금지 |
| Kakao reverse geo / POI | 문서상 한국 행정구역·주소·POI; 실제 품질 미측정 | 공통 월3M, geocode/각 POI 일100k 표기. 무료 첫 활성 앱 조건. 추가 geocode0.5원/건, POI2원/건 | 활성화/쿼터·증설·자동차 이용/캐시권 확인 | 검색 지연/반대 차선/없는 결과를 무리하게 채우지 않음 |
| SGIS/국토부 로컬 경계 후보 | 한국 행정구역·기준연도/행정동 vs 법정동 구분 | API 무료 조건/토큰/트래픽은 서비스별; 배포 파일 비용 UNKNOWN | SGIS는 accessToken, WGS84/UTM-K 변환 필요. 실제 재배포 파일 미확보 | schema·권리·CRS 검증 후 offline point-in-polygon 후보 |
| Open-Meteo forecast/sunrise | 좌표 기반 모델; 차량 센서/실측 아님 | 무료 평가·비상업과 상업 subscription 분리; monetary tier 재확인 | 상업 플랜 월 quota별, 계정 SLA/단가 UNKNOWN | 날씨는 표시용 선택 정보만; 실제 외기 온도/P1 안전 센서 대체 금지 |
| NOAA 참고 로컬 천문 | 위도·날짜 기반 천문 근사; 터널·날씨·조도 아님 | 재구현 계산 종량 없음; 코드/표 재사용 조건 별도 | 계산기 유지보수 종료, 고위도/대기 오차 | DERIVED_ASTRONOMICAL_NIGHT 후보로 별도 라벨. 고정 18~06시 대체 금지 |
| MapLibre+허가 tile | renderer와 한국 지명/지도 품질은 독립 | renderer BSD 무료; tile/traffic/storage 별도 | tile quota/상업/캐시 조건 provider 미선정 | 현재 툴체인 갈등/새 권한 감사 필요, Tier4 |
| Nominatim public | 지역/언어 품질 미측정 | 무료 공용 서비스이나 무제한 아님 | 전체 앱 1req/s 이하·식별 UA·귀속/캐시. autocomplete/체계적 grid/POI 수집 금지 | 본 앱 연속 경로 reverse 용도 **NOT_RECOMMENDED**. [공용 정책](https://operations.osmfoundation.org/policies/nominatim/) 준수; 직접 hosting은 별도 운영 범위 |
| Android STT/TTS | 엔진·ko-KR·오프라인 조건별 | 플랫폼 종량 없음과 provider 이용료는 다름 | STT availability만 확인, TTS 현 AVD init ERROR | 설치만으로 실제 음성 지원이라고 보고하지 않음 |
| Google Cloud STT V2 | 공식 ko-KR 모델/리전 목록; 차내 잡음 미측정 | standard 첫500k분 $0.016/분; dynamic batch는 실시간 대안 아님 | 계정/리전별 quota·로그/보관 조건 재확인; V1 무료량 혼용 금지 | 서버/녹음 동의·취소·품질 선행 |
| Google Cloud TTS v1 | ko-KR Standard/Neural2/Chirp 목록 | Standard 월4M문자 무료 후$4/1M; Neural2 월1M 후$16/1M. billing 필요 | 실제 voice/계약·쿼터 재확인 | 정차 고정 안내부터; 서버 합성이 audiofocus를 대체하지 않음 |
| Gemini 3.5 Flash-Lite | 공식 Stable 후보; 한국어/회고 정확도/latency 실측 없음 | standard input$0.30/output$2.50 per1M token. 무료와 유료 데이터 약관 다름 | 프로젝트별 RPM/TPM/RPD UNKNOWN. 2.5 Flash는 deprecated 안내라 새 기준으로 고르지 않음 | vehicle tool 금지, 최소 회고 텍스트만, 로컬 fallback 필수 |
| 자체 backend | 제공자/리전 미선정 | compute/storage/DB/egress/로그·운영비 모두 별도 | 비용·SLA UNKNOWN | 제출 전 신규 운영 체계 비추천 |

근거: [S40–60](PHASE8_5_SOURCE_REGISTER.md#S40). REST 후보는 Android SDK 좌표가 필요하지 않으므로 없는 Maven 이름을 제안하지 않았다. 실제 provider별 retention·자동차 상업 사용 조건이 UNKNOWN이면 동의 문구로 임의 해결하지 않는다.

## 네트워크/서비스 장애 계약 — 향후 구현 시 PASS 기준

아래 숫자는 공급자 보장이 아닌 **제안할 제품 예산**이다. 구현 전 범위 합의에서 확정해야 하며 이번에 검증한 timeout 수치가 아니다. 핵심 Trip/동의·삭제/결정적 Safety 엔진은 로컬 독립 경계이고 외부 작업을 기다리지 않는다. ControlGateway는 외부 응답과 무관하게 비활성이다.

| 상황 | 제안 동작 | PASS 기준 |
|---|---|---|
| offline | 지역 미상/검색 불가·텍스트/로컬 카드로 즉시 fallback | Trip 기록/케어 입력 상태·사용자 삭제에 지연/가짜 값 없음 |
| slow network | 즉시 로컬 UI, 2초 내 진행 상태; 정차 UX를 기다리게 강제하지 않음 | 메인 스레드 차단 없음, 경로 이탈/주행 전환 시 취소 |
| timeout | 연결3초/전체8초 예산 후보. 음성·안전 안내는 더 짧은 별도 TTL | timeout 이후 미발급/미낭독; 불완전 응답을 성공으로 저장하지 않음 |
| DNS/TLS 오류 | 요청 실패로 분류; local fallback. 인증서 검증 완화 안 함 | 임의 IP/cleartext/인증서 무시 우회 없음 |
| server5xx | idempotent 읽기만 제한된 재시도/지수 backoff·jitter 후보 | 앱 lifecycle 밖 폭주/중복 엽서/중복 XP 없음 |
| 429/예산 초과 | Retry-After/쿼터 회로 차단·설명 | 차량 상태·회고를 다른 provider에 무동의 전송하지 않음 |
| 응답 schema 오류/LLM hallucination | allowlist/길이/스키마·민감 표현 검사 실패 → 로컬 문구 | 안전 우선순위·warning 원인·차량 성공·지역 방문·XP 생성 불가 |
| 취소/권한 철회/기억 삭제 | 요청 generation 취소, 늦은 응답 폐기, 캐시/전송 scope 재검증 | 삭제된 회고 재출현·철회 후 전송·주행 중 지연 음성 없음 |
| 위치 상실/부정확/mock/stale | UNKNOWN으로 전파. 목적에 맞는 age/accuracy/provenance 검증 | 마지막 좌표로 새 방문/새 일몰 사실을 생성하지 않음 |

외부 연계는 `Provider → 검증된 Evidence → domain projection` 경계로만 들어온다. LLM은 이미 확인된 사실의 정차 표현을 선택적으로 다듬을 수 있지만, 안전 고정 문구/우선순위/Trip 사실/보상/차량 control intent를 생성하는 권위가 되지 않는다.

## 신규 보안 발견 — 현재 저장소를 제출 완료로 볼 수 없는 이유

`core/contracts/.../storage/TripStoreCodec.kt`는 DataOutputStream으로 schema3 binary를 구성하고 FileOutputStream으로 쓴다. staging 원자 교체/동의/삭제는 구현되어 있지만 **binary는 암호화가 아니며 앱 수준 암호화는 없다**. 선택 회고에는 PII가 포함될 수 있다. 공식 보안 기준과의 갭으로 분류하며 역사적 저장/삭제 테스트 PASS를 취소하거나 보안 PASS로 확대하지 않는다. [S11](PHASE8_5_SOURCE_REGISTER.md#S11).

다음 구현 후보 R27: Android Keystore 키 관리 + 검증된 authenticated encryption, staging/사본 포함 평문 최소화, 기존 schema 읽기/암호화 migration 및 실패 시 데이터 보존, 키 무효화/손상/원자 교체/삭제·동의 철회 검증. **이번에 암호화 코드·저장 schema·사용자 데이터를 변경하거나 삭제하지 않았다.**
