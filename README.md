# 카펫 (Carpet)

**캐릭터와 드라이브의 기억을 쌓고, 반려동물과의 나들이를 준비하는 차량용 동행 앱입니다.**

카펫은 Pleos Connect / Android Automotive OS에서 차량의 이동·정차 맥락을 읽습니다. 오리지널 동반자 **파도 수달·별빛 솜솜**과 여행을 기록하고, 필요할 때 반려동물 동행 모드로 준비 수첩·휴식 계획·동반 조건 검색을 이용할 수 있습니다.

팀 **BooUP** · 2026 현대자동차그룹 차량용 앱 개발 공모전 출품작 · 개발명 AI Roadmate

## 카펫과 함께하는 드라이브

- **차량과 연결된 동행** — 하향등·누적 주행거리·앞좌석 왼쪽 공조 목표·외기 센서 값을 읽고, 실제 motion과 화면 제한에 맞춰 콘텐츠를 제공합니다.
- **정차 후 남기는 기억** — 동의한 드라이브를 기기에 저장하고 기억 카드·선택 회고·월간 기록으로 다시 봅니다.
- **여행과 컬렉션** — 수채화 풍경, 오리지널 동반자, 탐험 지도·엽서·무료 시즌으로 여행의 즐거움을 이어갑니다. 강릉 이야기와 지역 방문·엽서 발급은 별도 여행 체험 앱에서 모의 데이터로 제공합니다.
- **우리 아이와 함께** — 준비 체크·장소 수첩·펫 엽서·다음 출발 메모·무료 펫 패스와 꾸미기 보상을 사용합니다.
- **펫 휴식 도우미** — 보호자가 간격을 선택하고 관측된 동행 시간을 관리합니다. 쉬었다는 확인과 이어가기는 보호자가 직접 합니다.
- **동반 조건 검색** — 서울4·강릉1·제주3곳의 공식 안내를 지역·이름·종류로 찾고 동물 종류·체중·이동장 조건을 비교합니다. 출처·확인일·미확인 조건을 함께 보여줍니다.

일반 여행 모드도 그대로 유지됩니다. 기록과 펫 정보는 별도 동의 후 앱 로컬에 저장하며 Android Keystore와 AES-256-GCM으로 보호합니다. 로그인·서버·API 키가 필요하지 않습니다.

## 설치와 시작

| 항목 | 내용 |
|---|---|
| 앱 | 카펫 / 팀 BooUP |
| APK | [BooUP_카펫_release.apk](apk/BooUP_카펫_release.apk) |
| 패키지·버전 | `io.booup.roadmate` · `1.0.0 (1)` |
| 실행 환경 | **Pleos Connect Emulator v2.1.0 / Android API34 / x86_64** |
| 화면 | **2560×1440 / 160dpi / 가로**, 확인한 글꼴 배율1.0 |
| 시작 설정 | Pleos Vehicle 서비스·Virtual Console 연결, Park·0km/h, 시스템의 차량 속도 읽기 권한 허용 |

```powershell
$roadmateAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
& $roadmateAdb devices -l
$roadmateDevice = 'emulator-5554' # 실제 대상 serial로 설정
& $roadmateAdb -s $roadmateDevice install -r 'apk/BooUP_카펫_release.apk'
$roadmateUser = (& $roadmateAdb -s $roadmateDevice shell am get-current-user).Trim()
& $roadmateAdb -s $roadmateDevice shell am start --user $roadmateUser -n io.booup.roadmate/io.booup.roadmate.MainActivity
```

런처에서 **카펫**을 열고 차량 상태를 확인하세요. 기록 저장은 설정에서 직접 켜며, 펫 기능은 홈의 **반려동물 동행**에서 등록·동의 후 선택합니다. 기존 개발 APK와 서명이 다르면 사용자 앱과 데이터를 삭제하지 말고 별도 AVD를 사용하세요. 현재 signed APK의 서명·구성은 검사했으며 별도 AVD 설치·실행은 미확인입니다. 자세한 결과는 [검증 현황](docs/VALIDATION_SUMMARY.md)에 있습니다.

## 연결 모드와 여행 체험

기본 APK는 실제 Pleos/AAOS 서비스에서 읽은 차량 상태로 동작합니다. 별도 체험 앱 `io.booup.roadmate.demo`는 **DEMO · SIMULATED** 표시 아래 모의 차량·지역·여행 데이터를 사용합니다. **더보기 → 여행 체험 → 화면 둘러보기 시작**에서 이야기·엽서·보상 화면을 살펴볼 수 있습니다. 반려동물 기능은 임시 화면 둘러보기를 종료한 일반 체험 홈에서 이용합니다.

모의 방문은 실제 위치 인증이 아니며 프리미엄 미리보기에는 결제가 없습니다. 실제 차량 관측·저장 기록·모의 체험을 서로 바꾸어 사용하지 않습니다. [제품 기능과 제공 범위](docs/MVP_SCOPE.md).

## 차량 환경과 개인정보

주행·미확인·오래된 차량 정보·플랫폼 제한 상태에서는 정차 상세와 입력을 잠급니다. 차량 제어를 실행하지 않으며, 공조 안내는 사용자가 직접 조절할 때 참고하는 정차 기능입니다. 캐릭터와 보상이 안전 판단을 바꾸지 않습니다.

펫 휴식은 **앱 전경에서 관측한 동행 시간**이며 정차 대기도 포함합니다. 백그라운드 알람·탑승/건강·실제 휴식 감지는 제공하지 않습니다. 장소 검색은 **2026-11-15 확인한 공식 정보8곳의 오프라인 목록**으로, 실시간 영업·입장 허가를 보장하지 않습니다.

현재 판단과 표현은 로컬 규칙과 고정 문구를 사용합니다. AI Companion을 지향하지만 외부 LLM·자유 대화는 연결하지 않았습니다. 실제 위치 자동 방문·내비 경로·음성·차량 제어·결제·클라우드 동기화, 실차·Market/DO 승인과 전체 접근성 검수는 후속 범위입니다. [안전·개인정보](docs/SAFETY_AND_PRIVACY.md).

## 개발

Kotlin · Jetpack Compose · ViewModel/StateFlow · Coroutines/Flow. `app`에서 플랫폼과 화면을 연결하고 `core:model`, `core:contracts`에서 모델·엔진·저장소를 분리합니다. [아키텍처](docs/ARCHITECTURE.md).

툴체인: JDK21.0.4 / Gradle Wrapper8.13 / AGP8.13.2 / Kotlin2.0.0 / SDK35·build-tools35.0.1, minSdk28·compile/targetSdk35.

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATAAndroidSdk"
$env:ANDROID_USER_HOME = "$env:USERPROFILE.android"
.gradlew.bat --gradle-user-home .gradle-user-home --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT' '-Pandroid.overridePathCheck=true' :app:assembleProductionDebug :app:assembleDemoDebug
```

첫 빌드에는 SDK와 Maven 네트워크 접근이 필요합니다. 기본 앱은 `production`, 모의 입력 체험 앱은 `demo` flavor입니다. release 빌드·서명·JVM 테스트는 [빌드 안내](docs/BUILD_AND_RUN.md)를 따릅니다.

```text
app/              화면·ViewModel·플랫폼 adapter·체험 런타임
core/model/       플랫폼 독립 데이터 모델
core/contracts/   엔진·계약·암호화 저장소·테스트
gradle/           Wrapper·버전 카탈로그
tools/            빌드/검증 보조 도구
docs/             제품 기획·설계·사용·개발 문서
apk/              서명한 release APK
```

[제품 기획](docs/PRODUCT_PLAN.md) · [문서 안내](docs/README.md) · [개발 로드맵](docs/DEVELOPMENT_PHASES.md) · [구성요소 출처](THIRD_PARTY_NOTICES.md)
