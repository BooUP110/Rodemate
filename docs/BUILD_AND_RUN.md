# 빌드 및 실행

## 환경

| 항목 | 현재 프로젝트 값 |
|---|---|
| JDK | 실제 빌드 검증21.0.4, JVM target17 |
| Gradle / AGP / Kotlin | Wrapper8.10.2 / 8.8.2 / 2.0.0 |
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

### NaviHelper 위치 응답 확인

Pleos AVD를 정차 상태로 두고 별도 검증 APK에서 위치 응답 형식을 확인한다. 결과에는 좌표의 범위 확인 여부만 남는다. 도착 시점과의 일치 여부 및 엽서 발급은 별도로 검증한다.

~~~powershell
.\gradlew.bat -Pphase8UiProbe -ProadmateNaviProbe :ui-probe:assembleDebug
& $roadmateAdb -s emulator-5554 install -r -t tools/ui-probe/build/outputs/apk/debug/ui-probe-debug.apk
& $roadmateAdb -s emulator-5554 shell am instrument -w io.booup.roadmate.uiprobe/.NaviLocationProbe
~~~

### 내부 콜라보 미리보기

로컬 `development/collab-preview/assets/companion-previews/`에 `catalog.json`과 각 초상화를 둔다. 카탈로그는 `id`, `name`, `seasonTitle`과 선택 항목 `tone`(`WARM` 또는 `CALM`)을 담은 객체 배열이다. 초상화 파일명은 `<id>.png`로 맞춘다. `<id>-idle.png`가 있으면 동일한 크기의 2×2 칸을 왼쪽 위부터 오른쪽 아래 순으로 재생한다. 내부 시연 앱 이름은 로컬 `development/collab-preview/res/values/strings.xml`의 `app_name`으로 지정할 수 있다.

~~~powershell
.\gradlew.bat --project-cache-dir .gradle/collab-preview -ProadmateInternalCollabs :app:packageInternalCollab
~~~

내부 APK는 `development/collab-preview/apk/roadmate-collab-debug.apk`에 복사되고 앱 ID는 `io.booup.roadmate.demo.collab`이다. 로컬 `development/`는 Git 제외 경로다. 내부 이미지가 담긴 APK는 가상 콜라보 시연용으로 관리한다.

### 탐험 지도와 엽서 시연

정차 홈에서 동반자를 선택한다. **탐험 지도 → 지역 선택 → 방문 시연**으로 약 10초 분량의 합성 트립을 재생한다. 정차 후 해당 구역과 엽서가 열리고, 완료된 트립과 발급된 엽서가 시즌 원장에 반영된다. **엽서 컬렉션**에서 엽서를 펼치고 **시즌 패스**에서 보상을 받는다. 프리미엄 체험은 내부 시연에서만 켤 수 있다. 지도는 시·군·구 방문 기록으로 안개를 걷고, 위치가 확인되지 않은 구역은 가려 둔다. Production의 위치 응답 검증과 경로 격자 수집은 별도 단계다.

지도는 [geoBoundaries KOR ADM2, 2020](https://www.geoboundaries.org/api/current/gbOpen/KOR/ADM2/)의 [고정 버전 단순화 경계](https://github.com/wmgeolab/geoBoundaries/raw/9469f09/releaseData/gbOpen/KOR/ADM2/geoBoundaries-KOR-ADM2_simplified.geojson)를 사용한다. 원저자는 geoBoundaries와 citypopulation.de이며 라이선스는 [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/)이다. 앱의 `journey/korea-districts.json`은 원본 파일의 228개 피처에서 식별자와 링 좌표를 추출해 화면용으로 투영하고 반올림한 파생 자료다. 엽서 배경은 생성한 여행 일러스트다.
