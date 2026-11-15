# 빌드와 실행

## 환경

JDK21.0.4 / Wrapper8.13 / AGP8.13.2 / Kotlin 플러그인2.0.0. Android SDK35·build-tools35.0.1·platform-tools를 설치한다. minSdk28·compile/targetSdk35이며 runtime stdlib은 의존성 해석 결과2.0.21이다.

확인한 실행 환경은 Pleos Connect Emulator2.1.0/API34/x86_64, 가로2560×1440·160dpi·글꼴 배율1.0이다. AVD RAM4GB와 호스트 여유 메모리가 필요하다. 첫 빌드는 Google Maven·Maven Central·공식 Pleos Maven에 접근해야 한다. SDK 다운로드·계정 이용권·차종 지원·Market 승인은 별도로 확인한다.

## 빌드 변형

| 변형 | 역할·앱 ID |
|---|---|
| production | 실제 Vehicle/AAOS 읽기, `io.booup.roadmate` |
| demo | 별도 모의 입력 여행 체험, `io.booup.roadmate.demo` |
| debug / release | Android 표준 빌드 타입. 일반 demo release는 비활성 |

내부 경계 식별자인 `RuntimeMode.DEMO`, `DataSource.SIMULATED`·flavor·저장 경로는 실제/모의 입력 격리를 위해 유지한다. 이름을 지우거나 모의 값을 production으로 연결하지 않는다. 기본 빌드는 `development/`의 로컬 이미지·글꼴 없이 가능하다.

Windows PowerShell, 프로젝트 루트:

```powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATAAndroidSdk"
$env:ANDROID_USER_HOME = "$env:USERPROFILE.android"
.gradlew.bat --gradle-user-home .gradle-user-home --console=plain --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT' '-Pandroid.overridePathCheck=true' :app:assembleProductionDebug :app:assembleDemoDebug
```

다른 운영체제에서는 `./gradlew`와 해당 SDK 경로를 사용한다. 한글/공백 Windows 경로의 AGP 설정을 명시한 명령이며 다른 OS 전체 빌드 검증을 뜻하지 않는다.

## 테스트

```powershell
.gradlew.bat --gradle-user-home .gradle-user-home --console=plain --offline --no-daemon --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT' '-Pandroid.overridePathCheck=true' :core:model:test :core:contracts:build :app:testDemoDebugUnitTest :app:testProductionDebugUnitTest :app:assembleProductionRelease :app:assembleDemoDebug :app:lintProductionDebug
```

`--offline`은 의존성 캐시가 설치된 환경에서만 사용한다. Android 없이 core를 확인하려면 `-PcoreOnly :core:model:test :core:contracts:build`를 사용한다. 현재 검사 결과는 [검증 현황](VALIDATION_SUMMARY.md)에 있다.

## Release 서명

```powershell
.gradlew.bat --gradle-user-home .gradle-user-home '-Pandroid.overridePathCheck=true' :app:assembleProductionRelease
python tools/package_submission_release.py
```

일반 Gradle 출력 `app/build/outputs/apk/production/release/app-production-release-unsigned.apk`는 미서명 중간 파일이다. Python 도구는 보존된 로컬 키로 서명·정렬·Manifest/payload를 검사하고 `apk/BooUP_카펫_release.apk`에 만든다. 서명 키를 처음 만들 때만 `--init-signing`을 사용하며 기존 identity를 교체하지 않는다. 키/credential은 Git 제외 로컬에 두고 별도 안전 백업한다. 기존 다른 내용의 APK도 로컬에 보존한다. 비밀번호는 명령행/공개 문서에 넣지 않는다.

## 설치

```powershell
$roadmateAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
& $roadmateAdb devices -l
$roadmateDevice = 'emulator-5554'
& $roadmateAdb -s $roadmateDevice install -r 'apk/BooUP_카펫_release.apk'
$roadmateUser = (& $roadmateAdb -s $roadmateDevice shell am get-current-user).Trim()
& $roadmateAdb -s $roadmateDevice shell am start --user $roadmateUser -n io.booup.roadmate/io.booup.roadmate.MainActivity
```

서명이 다르면 기존 사용자 앱·기록·키를 삭제하지 않고 별도 AVD를 사용한다. 현재 signed APK의 별도 설치/실행은 미확인이다. Pleos 시스템 런처·Vehicle 서비스·Virtual Console이 준비된 후 Park0에서 시작하고 정상 시스템 권한 요청을 허용한다. root/권한 identity 우회는 사용하지 않는다.

차량 상태가 미확인이면 정차 화면 잠금이 정상이다. 기록 저장은 설정에서 직접 동의하고, 펫 휴식은 별도 간격·해당 저장 동의를 선택한다. 체험 앱에는 DEMO·SIMULATED가 표시되며 메뉴는 `더보기 → 여행 체험`이다. 프리미엄 미리보기는 실제 구매가 아니다.
