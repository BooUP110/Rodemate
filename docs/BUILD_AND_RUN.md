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
