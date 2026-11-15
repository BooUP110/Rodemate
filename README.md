# 카펫 (Carpet)

**드라이브가 동행 캐릭터와 함께 모으는 여행 엽서와 시즌 패스가 되는 Pleos Connect 앱입니다.**

차량 상태와 함께하는 오리지널 동반자(파도 수달·별빛 솜솜), 정차 후 드라이브 기억을 연결하는 **Pleos Connect / Android Automotive OS 앱 MVP**입니다. Kotlin과 Jetpack Compose로 구현했으며, 차량 데이터와 시연 데이터를 별도 앱으로 분리합니다.

## 제출 정보

| 항목 | 내용 |
|---|---|
| 팀 / 앱 | BooUP / 카펫 |
| 제출 APK | [GitHub Releases](https://github.com/BooUP110/Rodemate/releases)의 `BooUP_카펫_release.apk` (약 120MB라 저장소 대신 Releases에 올림) |
| 앱 ID | `io.booup.roadmate.demo.collab` (시연용 release, 디버그 키 서명) |
| 테스트 환경 | Pleos Connect 에뮬레이터 v2.1.0 (Android Automotive, API 34, x86_64) |
| 화면 규격 | 2560×1440, 160dpi, 가로 |
| 설치·실행 | 에뮬레이터 실행 후 `adb install -r BooUP_카펫_release.apk` → 앱 목록의 **카펫** 실행 → **더보기 → 시연 선택 → 화면 둘러보기 시작** |
| 필요한 설정 | 없음. 시연 앱은 합성 정차 상태로 시작하며 차량 권한·계정·네트워크가 필요 없습니다 |

> 시연 속 치이카와·짱구 캐릭터는 IP 콜라보 패스 구조를 보여 주기 위한 **예시이며 실제 제휴가 아닙니다.** 앱 화면에도 "콜라보 예시 · 실제 제휴 아님"으로 표시했고, 출시할 때는 정식 라이선스 계약 후 적용합니다. 해당 그림은 Git 저장소에 포함하지 않습니다.

## 현재 제공 범위

| Production | Demo |
|---|---|
| 실제 하향등·누적 주행거리·앞좌석 왼쪽 공조 목표·외기 온도 읽기 | SIMULATED 입력을 사용하는 케어 시나리오 |
| AAOS motion 기반 트립 시작·정차·재개·완료 | 하향등·온도·휴식·경고 표현과 상호작용 |
| 명시 동의 후 앱 로컬 기록·복원·삭제 | 가상 지역 엽서·회고·시즌·보상 체험 |
| 정차 후 기억 카드·선택 회고·무료 로컬 시즌 | DEMO 표기, 별도 앱 ID와 저장 경로 |
| 정차 명시 온도 선호 안내·동의한 기억 회상/월간 사실 요약 | 실제 차량 adapter/permission 없이 기존 합성 경계 유지 |
| Android Keystore/AES-GCM 저장 보호·기존 데이터 비파괴 전환 | 별도 UID/키/암호화 저장 |
| 선택형 반려동물 동행: 준비·장소 수첩·완료 드라이브 기반 펫 엽서·상시 무료 펫 패스 | 동일 로컬 기능, 기존 합성 드라이브와 연결 |

필수 입력이 검증되지 않은 자동 Care와 실제 지역 엽서 발급은 비활성입니다. 실제 경고 Care·음성·지도·위치·차량 제어·외부 LLM·서버·결제는 현재 제공하지 않습니다. 자세한 구분은 [MVP 범위](docs/MVP_SCOPE.md)에 있습니다.

**공모전 제출본입니다.** 구현 검증을 마쳤습니다. 실차 검증, Market·DO 심사, 최종 개인정보 문안 확정은 아직 받지 않았으며 배포 승인을 뜻하지 않습니다.

## 빠른 시작

반려동물 기능은 홈의 **반려동물 동행**에서 등록·동의 후 선택합니다. 일반 모드와 기존 시즌도 유지됩니다. 자세한 사용·삭제·정보 범위는 [반려동물 동행 안내](docs/PET_TRAVEL_MODE.md)를 참조하세요.

**펫 휴식 도우미**에서 보호자가 정한 간격으로 동행 시간을 관리하고, **동반 조건 검색**에서 서울·강릉·제주 8곳의 공식 안내를 오프라인으로 확인할 수 있습니다. 타이머 재개·휴식 완료는 보호자가 직접 확인합니다. [이동 도우미 사용·정보 출처](docs/PET_MOBILITY_FEATURES.md)를 확인하세요.

검증 환경은 JDK21.0.4, Android SDK35/build-tools35.0.1, Gradle Wrapper8.13입니다. 실행 대상은 Pleos Connect2.1.0/API34 AVD이며 minSdk28, compile/targetSdk35입니다.

Windows PowerShell, 프로젝트 루트에서:

~~~powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_USER_HOME = "$env:USERPROFILE\.android"
.\gradlew.bat --gradle-user-home .gradle-user-home --no-daemon '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT' '-Pandroid.overridePathCheck=true' :app:assembleDemoDebug :app:assembleProductionDebug
~~~

첫 빌드에는 네트워크와 Android SDK가 필요합니다. Pleos SDK 의존성은 Production에만 포함됩니다. 전체 빌드·JVM 테스트·플랫폼 없는 core 테스트·APK 설치는 [빌드 및 실행 안내](docs/BUILD_AND_RUN.md)를 참조하세요.

| 실행 파일 | 경로 |
|---|---|
| Production debug | app/build/outputs/apk/production/debug/app-production-debug.apk |
| Demo debug | app/build/outputs/apk/demo/debug/app-demo-debug.apk |
| Production release | app/build/outputs/apk/production/release/app-production-release-unsigned.apk · 미서명 |
| 공모전 제출용 release | apk/BooUP_카펫_release.apk · 내부 콜라보 빌드에서만 생성, 디버그 키 서명 |

APK는 Git에 올리지 않고 빌드로 생성합니다. 기본 빌드에서 Demo release는 비활성이고, 공모전 제출용 release만 내부 콜라보 빌드에서 만듭니다.

~~~powershell
.\gradlew.bat --project-cache-dir .gradle/collab-preview -ProadmateInternalCollabs :app:mergeDemoReleaseAssets --rerun :app:packageContestRelease
~~~

## 패스 시연 체크리스트

시연 영상 촬영용 순서입니다. 콜라보 패스는 **내부 콜라보 APK**에서만 보입니다.

### 준비

- 제출용 APK `apk/BooUP_카펫_release.apk`(또는 같은 화면의 내부 디버그 APK `development/collab-preview/apk/roadmate-collab-debug.apk`)를 **Pleos Connect 에뮬레이터**에 설치합니다. 앱 ID는 `io.booup.roadmate.demo.collab`입니다. 치이카와·짱구 그림은 Git에 없어서 일반 Demo APK에는 치이카와 패스와 잠긴 콜라보 패스가 나오지 않습니다. 빌드 방법은 [빌드 및 실행 안내](docs/BUILD_AND_RUN.md)의 내부 빌드 항목에 있습니다.
- 효과음이 들어가므로 기기 볼륨을 켜고, 녹화 도구가 앱 소리까지 녹음하는지 먼저 짧게 테스트합니다.
- 녹화는 **더보기 → 시연 선택 → 화면 둘러보기 시작**으로 엽니다. 둘러보기는 열 때마다 새 상태로 시작하므로 리허설에서 받은 보상이 남지 않습니다. 아래 ‹ 이전 / 다음 › / 목차 버튼으로 7개 화면(동행, 강릉 이야기, 안개 지도, 엽서, 시즌 보상, 패스권 뽑기, 차량 상태)을 오갑니다.
- 치이카와·하치와레·우사기는 처음부터 보유 상태입니다. 슬롯까지 한 번에 채우려면 목차의 **콜라보 캐릭터 시연**을 누릅니다.

### 촬영 순서

| # | 화면 | 누를 것 | 보여 줄 것 |
|---|---|---|---|
| 1 | 동행 | **동행 바꾸기** | 동행 선택창. 치이카와 세 친구는 "보유", 짱구·철수·유리·맹구·훈이는 그림과 함께 "예시", 다음 콜라보 자리는 "???" 실루엣 |
| 2 | 동행 선택 | 짱구 → **패스 미리보기** → 돌아가기 → 치이카와 → **이 동행과 출발** | 잠긴 캐릭터는 잠김 효과음과 "서울 투어 짱구 패스 · 콜라보 예시". 홈에 치이카와가 나옴 |
| 3 | 동행 선택 | **슬롯 편집** → 빈 슬롯 → 우사기 → **슬롯에 넣기** | 동행 슬롯 3칸 구성 |
| 4 | 시즌 보상 | 화면을 아래로 내리기 | 시즌 패스 3종(치이카와와 함께하는 여행, 별빛 캠핑, 바닷길 탐험대) 아래에 **NEXT COLLAB** 잠긴 포스터 2장(서울 투어 짱구 패스, 이름을 가린 다음 콜라보 패스) |
| 4-1 | 짱구 패스 미리보기 | 짱구 포스터 → 화면 확인 → **‹ 돌아가기** | 잠김 효과음과 함께 미리보기: 키 비주얼, 움직이는 짱구 친구들 5명, 잠긴 보상 4개, "콜라보가 확정되면 열려요" |
| 5 | 패스 로비 | **치이카와와 함께하는 여행** 포스터 | 패스 상세와 무료·유료 패스 트랙 |
| 6 | 패스 상세 | **패스 업그레이드 시연** | 배경 조명이 켜지고 업그레이드 효과음. 유료 칸이 열림 |
| 7 | 패스 트랙 | "수령 가능" 칸 → **보상 받기** | 보상 효과음, 칸이 "수령 완료"로 바뀜. XP가 닿은 칸만 열림 |
| 8 | 패스 트랙 | 완주 칸 → **동반자 팩 열기** → 캐릭터 받기 | 완주 동반자 팩. XP가 부족하면 이 단계는 건너뜀 |
| 9 | 패스권 뽑기 | **패스권 1회 뽑기** → **여행 봉투 열기** → **획득한 패스 열기** | 봉투 도착 소리, 봉투를 열 때 업그레이드 효과음 |
| 10 | 미션 보드 | 탭 이동 | 9칸 미션 빙고. 한 줄을 채우면 패스 선택권 |
| 11 | 패스 상세 | **동반자 체험** → 캐릭터 고르기 → **공조 제안 체험** | 캐릭터별 공조 제안 대사 |
| 12 | 강릉 이야기 | 화면 아래 버튼을 차례로 | 출발 → 도착 시연 → 위치 인증 시연 → 엽서 빈칸 퍼즐 → 달빛 엽서 완성(보상 효과음) |
| 13 | 엽서 | 엽서 하나 열기 → **별 스탬프 붙이기** | 엽서를 열 때 종이 소리, 별을 붙일 때 보상 효과음 |
| 14 | 패스함 | **내 패스함** 탭 | 받은 패스 목록, 동반자와의 추억 |

### 효과음

`tools/sfx/generate_sfx.py`로 직접 합성한 음원이라 외부 음원 라이선스가 없습니다. 한 가지 벨 음색과 같은 조(D장조 5음계)로 맞췄습니다.

| 소리 | 나오는 곳 |
|---|---|
| 짧은 탭음 | 게임 버튼, 하단 메뉴, 패스 탭 |
| 낮은 톡톡 | 잠긴 캐릭터, 잠긴 콜라보 포스터(미리보기 열기) |
| 두 음 벨 + 반짝임 | 보상 받기, 별 스탬프, 의상 보상, 달빛 엽서 완성 |
| 상승 아르페지오 | 패스 업그레이드, 패스 선택권, 봉투 열기, 완주 동반자 팩, 프리미엄 권한 시연 |
| 종이 소리 + 벨 | 엽서 열기, 패스권 봉투 도착 |

## 프로젝트 구성

~~~text
app/                  Compose UI, ViewModel, Production SDK 연동, Demo 런타임
core/model/           플랫폼 독립 데이터·출처·정책 모델
core/contracts/       Adapter 계약, 결정적 엔진, 로컬 저장소와 테스트
gradle/               버전 카탈로그와 Gradle Wrapper
tools/ui-probe/       선택적으로 빌드하는 독립 testOnly UI 관측 도구
docs/                 실행·설계·범위·검수 문서
~~~

기본 제품은 3개 모듈입니다. SDK 타입을 UI/도메인에서 분리하고, 안전 판단은 캐릭터·무료 보상과 독립적으로 유지합니다. 상세 흐름은 [아키텍처](docs/ARCHITECTURE.md)에 있습니다.

## 안전과 검증

2026-11-15 이동 도우미 추가: **626개 / 651회 테스트 결과, 실패·오류·skip0**, Demo debug·Production debug·Production release unsigned 빌드 성공, 최종 offline lint 오류0·경고3. [최신 이동 도우미 검증](docs/verification/pet-mobility-artifacts.json)에 실제 AVD 범위와 한계를 구분했습니다. 아래 이전 날짜 수치는 당시 실행 기록입니다.

홈·시즌 개선과 반려동물 동행, 개발자 브랜치의 효과음·콜라보 예시·제출용 release 구성을 합친 최신 검증은 **594개 고유 테스트 / 619회 실행, 실패·오류·skip 0**, 기본 APK 3종 및 제출용 Demo release 빌드 성공, Production offline lint 오류0·경고3이다. 로컬 콜라보 자료의 부재 및 AVD 검증 범위는 [통합 검토](docs/MERGE_CONTEST_POLISH.md)를 따른다. 아래 수치는 이전 실행 기록이다.

2026-11-14 펫 모드 추가 검증: **593개 테스트 / 618회 실행, 실패·오류·skip0**. Demo debug·Production debug·Production release unsigned 빌드 성공, Production offline lint 오류0·경고3. 별도 AVD 검증 앱에서 저장·삭제·프레임과 Production 주행 제한을 확인했습니다. 글자1.3배의 동행 수첩·패스 화면도 확인했으며 전체 접근성/실차 검증은 남아 있습니다. [최신 검증 결과](docs/verification/pet-travel-artifacts.json)를 참조하세요. 아래 Phase8.6 수치는 당시 기록입니다.

운행/정책 상태를 확인할 수 없으면 정차 콘텐츠를 잠급니다. 실제 차량 제어 경로는 활성화하지 않았고, 사용자 수락도 차량 설정 변경 성공을 뜻하지 않습니다. 기록은 기본 비활성이고 별도 기억 저장에는 추가 동의와 항목 확인이 필요합니다. [안전·개인정보 안내](docs/SAFETY_AND_PRIVACY.md)를 확인하세요.

Phase8.6 검증은 **기존492개 보존 + 신규49개 = 541고유 테스트/565회, 실패·오류·skip0**, APK3종 빌드 성공입니다. Online lint는 오류0·경고16으로 증가가 없습니다. Offline에서 의존성 검사 생략으로 경고1만 나온 실행도 있으나 경고 해결로 세지 않습니다. 확대 접근성 검수는 PARTIAL이며 실차·심사 승인은 미확인입니다. 실제 AVD·저장 보안 결과와 한계는 [검증 요약](docs/VALIDATION_SUMMARY.md) 및 [Phase8.6 결과](docs/PHASE8_6_ENHANCED_LOCAL_PLATFORM_REPORT.md)에 기록했습니다.

전체 문서는 [문서 안내](docs/README.md), 직접 확인은 [사용자 검수 안내](docs/USER_ACCEPTANCE_GUIDE.md)와 [시연 동선](docs/SUBMISSION_DEMO_FLOW.md)을 따릅니다. 최초 Git 등록과 제외 규칙 확인은 [저장소 관리 안내](CONTRIBUTING.md)에 있습니다.
