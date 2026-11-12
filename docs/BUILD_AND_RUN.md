# 빌드 및 실행

통합 변경과 검증 결과는 [Phase8.6 / wkdal 통합 검토](MERGE_PHASE86_WKDAL.md)를 따른다. `phase86IsolatedAcceptance`와 `roadmateInternalCollabs`는 동시에 지정할 수 없다. 검증 앱과 내부 콜라보 앱의 UID·저장·키 격리를 유지한다.

콜라보 이미지/카탈로그는 Git 제외 자료이므로 다른 PC에서 자동으로 생기지 않는다. 아래 내부 빌드 안내의 `development/collab-preview/assets/companion-previews/` 자료가 있을 때 게임·전용 패스·캐릭터를 활성화한다. 자료가 없으면 기존 기본 Demo/Production 흐름을 유지한다.

## 환경

| 항목 | 현재 프로젝트 값 |
|---|---|
| JDK | 실제 빌드 검증21.0.4, JVM target17 |
| Gradle / AGP / Kotlin | Wrapper8.13 / 8.13.2 / 2.0.0 |
| Android SDK | compile/target35, min28, build-tools35.0.1 |
| Compose | BOM2024.04.01 |
| Pleos 의존성 | Production 전용 Vehicle2.3.2, vehicle-data1.1.11 |
| 실행 환경 | Pleos Connect2.1.0 / Android API34 AVD |

Gradle/Kotlin CLI의 별도 설치는 필요하지 않다. Android Studio에서는 루트를 열고 SDK/JDK 경로를 자신의 환경에 맞게 지정한다. local.properties, SDK, AVD 이미지와 다운로드 캐시는 Git에 포함하지 않는다. 실제 SDK 계정 사용권과 문서/아티팩트 접근 가능 여부는 별개다.

Pleos Maven 저장소는 settings.gradle.kts에 선언된 https://nexus-playground.pleos.ai/repository/maven-releases/ 를 사용한다. 새 좌표/버전을 추정해 교체하지 않는다. 별도 SDK/AVD 설치는 [공식 개발 문서](https://document.pleos.ai/)를 따른다.

## 빌드와 JVM 테스트

Windows PowerShell, 프로젝트 루트 기준:

~~~powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_USER_HOME = "$env:USERPROFILE\.android"
.\gradlew.bat --gradle-user-home .gradle-user-home --version

# 모델/엔진/저장소: Android SDK 없이 검증
.\gradlew.bat --gradle-user-home .gradle-user-home --no-daemon '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT' -PcoreOnly :core:model:test :core:contracts:build

# 전체 JVM 회귀, APK3종, Production lint
.\gradlew.bat --gradle-user-home .gradle-user-home --no-daemon '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT' '-Pandroid.overridePathCheck=true' :core:model:test :core:contracts:build :app:testDemoDebugUnitTest :app:testProductionDebugUnitTest :app:assembleDemoDebug :app:assembleProductionDebug :app:assembleProductionRelease :app:lintProductionDebug
~~~

최초 빌드는 Wrapper와 Maven 의존성 다운로드가 필요하다. 필요한 의존성을 모두 받은 뒤에만 --offline을 추가할 수 있다. 실행을 강제하려면 --rerun-tasks를 추가한다. 테스트 XML은 각 모듈 build/test-results 아래, lint XML/HTML은 app/build/reports 아래에 생성된다. 새 실행 결과와 [기록된 검증 결과](VALIDATION_SUMMARY.md)를 구분한다.

Windows 한글 경로에서 검증한 명령은 file.encoding=COMPAT와 AGP의 experimental android.overridePathCheck 옵션을 사용한다. ASCII 경로에서는 경로 검사 옵션을 생략할 수 있다. macOS/Linux에서는 ./gradlew와 실제 SDK 경로를 사용한다. 해당 OS에서의 전체 실행은 별도 확인이 필요하다.

## APK 설치 및 실행

AVD 부팅과 시스템 런처 준비 후 실행한다. 아래 예제의 emulator-5554는 adb devices 결과에 맞게 바꾼다. 현재 Android 사용자를 조회하며 특정 사용자 번호를 고정하지 않는다.

~~~powershell
$roadmateAdb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $roadmateAdb devices -l
$roadmateUser = (& $roadmateAdb -s emulator-5554 shell am get-current-user).Trim()
& $roadmateAdb -s emulator-5554 install -r app/build/outputs/apk/production/debug/app-production-debug.apk
& $roadmateAdb -s emulator-5554 install -r app/build/outputs/apk/demo/debug/app-demo-debug.apk
& $roadmateAdb -s emulator-5554 shell sync

& $roadmateAdb -s emulator-5554 shell am start --user $roadmateUser -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n io.booup.roadmate/io.booup.roadmate.MainActivity
& $roadmateAdb -s emulator-5554 shell am start --user $roadmateUser -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n io.booup.roadmate.demo/io.booup.roadmate.MainActivity --es roadmate.demo.scenario I4_CHARACTER_SWITCH
~~~

Production과 Demo는 앱 서랍에서도 구분해 실행할 수 있다. install -r은 기존 데이터를 유지한다. Production 최초 속도 읽기 권한 요청을 확인한다. UI가 실제 보이지 않으면 am start 성공 응답만으로 정상 실행으로 판정하지 않는다. release APK는 미서명이므로 위 설치에는 debug APK를 사용한다.

## Virtual Console과 선택 도구

~~~powershell
& $roadmateAdb -s emulator-5554 forward tcp:8088 tcp:8088
~~~

http://127.0.0.1:8088/ 을 브라우저로 열고 활성 상태로 유지한다. Console은 AVD 시험 입력 도구이며 앱의 차량 제어 기능이 아니다. 상세 절차는 [시연 동선](SUBMISSION_DEMO_FLOW.md)을 따른다.

tools/ui-probe는 제품 APK와 독립된 testOnly UI 관측 도구다. 필요한 경우 위 Gradle 공통 옵션에 -Pphase8UiProbe :ui-probe:assembleDebug를 추가해 빌드한다. 일반 빌드와 사용자 직접 검수에는 필요하지 않으며 제품에 의존성으로 포함되지 않는다. 원시 자동 수집 기록은 저장소에서 배포하지 않는다.

Phase8.6의 삭제/회고 자동 검증에는 `-Pphase86IsolatedAcceptance :app:assembleProductionDebug :app:assembleProductionDebugAndroidTest`로 별도 검증 APK를 만들었다. 같은 Production 소스를 쓰되 앱 ID는 `io.booup.roadmate.acceptance`, 출력은 `app/build/phase86-acceptance/`이며 UID·데이터·Keystore를 원래 앱과 분리한다. 이 옵션에서는 release가 비활성이다. 일반 Production 산출물을 대체하지 않으며 사용자는 기본 Production 앱으로 검수한다. 별도 계측 APK의 `Phase86Instrumentation`은 보안 fixture/현재 파일의 count-only 확인/외기 연결 검증을 수행하고 제품 APK에는 포함되지 않는다. 과거 `ClosureInstrumentation`은 이번 실행에서 사용하지 않았다.

Keystore 키가 없거나 암호문 인증에 실패하면 기존 파일을 자동 초기화하지 않는다. 재설치/데이터 지우기로 해결하려 하지 말고 [저장 보안](PHASE8_6_STORAGE_SECURITY.md)과 [migration](PHASE8_6_STORAGE_MIGRATION.md)을 먼저 확인한다. 단말의 전체 앱 데이터/키 삭제나 복구는 이번 자동 검증에 포함되지 않는다.
### NaviHelper 위치 응답 확인

Pleos AVD를 정차 상태로 두고 별도 검증 APK에서 위치 응답 형식을 확인한다. 결과에는 좌표의 범위 확인 여부만 남는다. 도착 시점과의 일치 여부 및 엽서 발급은 별도로 검증한다.

~~~powershell
.\gradlew.bat -Pphase8UiProbe -ProadmateNaviProbe :ui-probe:assembleDebug
& $roadmateAdb -s emulator-5554 install -r -t tools/ui-probe/build/outputs/apk/debug/ui-probe-debug.apk
& $roadmateAdb -s emulator-5554 shell am instrument -w io.booup.roadmate.uiprobe/.NaviLocationProbe
~~~

### 내부 콜라보 미리보기

로컬 `development/collab-preview/assets/companion-previews/`에 `catalog.json`과 각 초상화를 둔다. 카탈로그는 `id`, `name`, `seasonTitle`과 선택 항목 `tone`(`WARM` 또는 `CALM`)을 담은 객체 배열이다. `<id>.png` 또는 `<id>-idle.png`를 사용한다. 동작 파일의 2×2 칸은 왼쪽 위부터 오른쪽 아래 순으로 읽는다. 두 빌드의 앱 이름은 `app/src/main/res/values/strings.xml`의 `app_name`을 사용한다.

시즌 화면의 일러스트는 로컬 `development/collab-preview/assets/pass-worlds/`에 `travel.jpg`, `stars.jpg`, `coast.jpg`, `post.jpg`로 둔다. 해당 이미지가 없는 빌드는 테마별 그라데이션 배경을 사용한다.

~~~powershell
.\gradlew.bat --project-cache-dir .gradle/collab-preview -ProadmateInternalCollabs :app:mergeDemoDebugAssets --rerun :app:packageInternalCollab
~~~

내부 APK는 `development/collab-preview/apk/roadmate-collab-debug.apk`에 복사되고 앱 ID는 `io.booup.roadmate.demo.collab`이다. 로컬 `development/`는 Git 제외 경로다. 내부 이미지가 담긴 APK는 가상 콜라보 시연용으로 관리한다.

Android Studio에서 `C:/Users/scspr/WorkSpace/Rodemate`를 프로젝트로 열고 `demoDebug`로 기본 Run을 누르면 **카펫**(`io.booup.roadmate.demo`)이 실행된다. 정차 시 동행 홈 전체에 풍경과 캐릭터가 펼쳐진다. `차량 상태`에서 차량 정보를 열고, `강릉 이야기`에서 오리지널 동행과 함께 장소별 일러스트를 본다. 하단의 `시즌 패스`에서 보상과 무료·프리미엄 진행도를 확인한다. `roadmateInternalCollabs`를 지정한 빌드도 **카펫**(`io.booup.roadmate.demo.collab`)으로 표시되며 홈에 `내부 콜라보 시연` 배지가 붙는다. 이 빌드는 로컬 시연 캐릭터와 콜라보 시즌 화면을 포함한다. 두 앱은 각각의 저장 데이터를 사용한다.

콜라보 캐릭터와 함께 플레이하려면 에뮬레이터를 켜고 위에서 만든 내부 APK를 에뮬레이터 화면에 끌어다 놓는다. 프로젝트에서 바로 빌드하고 설치하려면 Android Studio 터미널에서 다음 명령을 실행한다.

~~~powershell
.\gradlew.bat --project-cache-dir .gradle/collab-preview -ProadmateInternalCollabs :app:mergeDemoDebugAssets --rerun :app:installDemoDebug
~~~

설치 후 **카펫**을 열고 홈의 `내부 콜라보 시연` 배지를 확인한다. `강릉 이야기 → 이야기 시작`을 선택한다. 이미 완주한 경우 `처음부터`를 선택한다.

### 탐험 지도와 엽서 시연

1. **탐험 지도 → 서울·강릉·경주·제주 → 다음 목표 → 방문 시연**으로 합성 트립을 재생한다. 정차하면 해당 지점의 엽서가 열리고 다음 원형 포인트가 공개된다. 주행과 첫 방문 XP는 같은 시즌 원장에 쌓인다.
2. **여행 엽서 펼치기 → 별 스탬프 붙이기**로 미션 보드 첫 줄을 완성한다. **시즌 패스 → 미션 보드**에서 미보유 패스권 한 종을 선택한다.
3. **시즌 패스**에서 일러스트 포스터를 눌러 해당 패스에 들어간다. 하단 패스 트랙의 보상 칸을 누르면 상세와 수령 상태가 열린다. **패스권 뽑기**에서 무료 시연권으로 미보유 패스를 뽑고 봉투를 연다. 강릉과 제주 엽서를 모으면 두 번째 시연권이 열린다.
4. 보유한 프리미엄 패스에서 시즌 Lv.5를 달성하면 **Lv.5 완주 동반자 팩 → 동반자 팩 열기 → 받기 → 동행으로 지정**을 누른다. 다시 방문 시연을 하면 **내 패스함**에 동반자의 첫 동행 엽서가 생긴다.
5. 공개된 원을 선택한 뒤 **출발지로**와 **목적지로**를 각각 지정한다. 공개된 지점만으로 이어지는 구간이 금색 선으로 표시된다. 한 지역을 완주하고 시연 순위 3위 안에 들면 **랜드마크 이름 짓기**에서 완주 지점 하나와 이름을 정한다. 등록 후 시연 방문자별로 방문과 좋아요를 추가할 수 있으며, 방문자 한 명당 5 RP를 한 번 적립한다.

내부 시연판에서는 **동반자 체험 → 캐릭터 선택 → 동행 홈에서 체험**으로 정차 중 홈에 캐릭터를 잠시 적용할 수 있다. 캐릭터는 홈 풍경에서 좌우로 이동하고, 누르면 멈춰서 인사한다. **체험 끝내기**를 누르거나 홈을 벗어나면 기존 동반자로 돌아간다. [내부 검토용 모션 목업](internal-demo/companion-motion-mockup.gif)

Demo와 Production 모두 같은 오프라인 지역 경계를 표시한다. 금색 선은 탐험 포인트 사이의 순서다. Demo의 완주 순위와 방문자 반응은 이 기기에 저장되는 시연 데이터다. Production의 자동 출발지 선택과 첫 방문 기록에는 지원되는 위치 공급원의 확인된 장소명이 필요하다.

### 신규 디자인 이미지 적용

원본 PNG를 `app/src/main/assets/journey/design/`에 둔다. 지도 배경 파일명은 `map-seoul.png`, `map-gangneung.png`, `map-gyeongju.png`, `map-jeju.png`다. 홈과 기록에 쓰는 파일은 `home-parked.png`, `care-car.png`, `drive-memory.png`, `drive-empty.png`다. 랜드마크에는 `landmark-creator-ticket.png`와 `landmark-placeholder.png`를, 안개 질감에는 `map-fog.png`를 사용한다. 이미지가 없는 빌드는 기존 엽서와 기본 UI를 사용한다.

### 로컬 제목 글꼴

Debug 빌드에서 제목에 Ok단단체를 쓰려면 [오케이티콘 공식 배포 페이지](https://www.okticon.com/freefont/?bmode=view&idx=169969648)에서 TTF를 받아 `development/local-fonts/res/font/carpet_heading.ttf`로 둔다. 이 로컬 폴더는 Git에서 제외된다. 기본 제목 글꼴은 주아체이며 메뉴와 대사는 고운돋움을 사용한다.

### 일반 Android AVD에서 디자인 확인

Android Studio의 일반 휴대폰 AVD를 열고 별도 Demo APK를 설치한다. 가로 1920×1080은 Pleos 전체 화면과 같은 16:9 비율이다. 분할 화면, 차량 SDK 연동 및 주행 제한은 Pleos에서 확인한다.

~~~powershell
.\gradlew.bat -Pphase86IsolatedAcceptance :app:assembleDemoDebug
$roadmateAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
& $roadmateAdb devices -l
# 위 목록에서 일반 Android AVD의 serial로 바꾼다.
$designDevice = 'emulator-5556'
& $roadmateAdb -s $designDevice shell wm size 1080x1920
& $roadmateAdb -s $designDevice shell cmd window user-rotation lock 1
& $roadmateAdb -s $designDevice install -r app/build/phase86-acceptance/outputs/apk/demo/debug/app-demo-debug.apk
& $roadmateAdb -s $designDevice shell am start -n io.booup.roadmate.acceptance.demo/io.booup.roadmate.MainActivity
~~~

가로 디자인 확인을 마치면 `adb shell wm size reset`과 `adb shell cmd window user-rotation free`를 해당 AVD에 실행해 기본 표시 크기와 자동 회전으로 돌아간다.

### 강릉 이야기 시연

강릉 패스 촬영용 장면, 레이어와 생성 프롬프트는 [이미지 제작 요청서](GANGNEUNG_DEMO_ASSETS.md)를 따른다.

정차 상태의 Demo 홈 일러스트에서 `강릉 이야기`를 누른다. 오죽헌, 경포대, 강문해변, 안목해변 순으로 도착 장면, 주민 대화, 위치 인증 시연, 퍼즐과 단서가 열린다. 각 장을 완료하면 다음 장부터 이어 할 수 있다. `동행 선택`은 기본 동행과 Demo 보유 캐릭터를 보여준다. 내부 콜라보 이미지가 있는 빌드는 치이카와 패스 캐릭터를 주민 장면에 사용한다. 의상 보상의 프리미엄 권한 버튼은 시연 전용이며 결제나 외부 영수증 확인을 호출하지 않는다.

이야기 장면의 16:9 PNG는 `app/src/main/assets/journey/design/`에 `story-gangneung-ojukheon.png`, `story-gangneung-gyeongpodae.png`, `story-gangneung-gangmun.png`, `story-gangneung-anmok.png`로 둔다. 파일을 추가하기 전에는 기존 강릉 엽서 일러스트로 표시한다.

이미지를 만들 때 기존 강릉 엽서의 색감과 붓질을 참고 이미지로 붙인다. 공통 프롬프트는 “동화책 여행 게임에 쓰는 16:9 가로 배경. 오른쪽 중앙은 캐릭터가 들어올 공간, 왼쪽 아래는 대사창이 얹힐 공간으로 두고 글자와 인물 없이 풍경만 그려 주세요.”로 쓴다.

- `story-gangneung-ojukheon.png`에는 오죽헌을 참고한 고택 마당과 대나무 사이로 들어오는 부드러운 늦은 오후 빛을 그린다.
- `story-gangneung-gyeongpodae.png`에는 경포대 풍경을 참고해 물가가 넓게 보이는 해 질 녘을 그린다. 앱이 달과 반사광을 얹을 수 있도록 오른쪽 물가와 하늘에 여백을 둔다.
- `story-gangneung-gangmun.png`에는 강문해변을 참고해 모래사장 가까이 부서지는 낮은 파도와 남보라색 저녁 하늘을 그린다.
- `story-gangneung-anmok.png`에는 안목해변을 참고해 저녁 바다와 멀리 보이는 따뜻한 불빛을 그린다. 엽서를 건네는 결말 장면의 배경이다.

지도는 [geoBoundaries KOR ADM2, 2020](https://www.geoboundaries.org/api/current/gbOpen/KOR/ADM2/)의 [고정 버전 단순화 경계](https://github.com/wmgeolab/geoBoundaries/raw/9469f09/releaseData/gbOpen/KOR/ADM2/geoBoundaries-KOR-ADM2_simplified.geojson)를 사용한다. 원저자는 geoBoundaries와 citypopulation.de이며 라이선스는 [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/)이다. 앱의 `journey/korea-districts.json`은 원본 파일의 228개 피처에서 식별자와 링 좌표를 추출해 화면용으로 투영하고 반올림한 파생 자료다. 엽서 배경은 생성한 여행 일러스트다.
