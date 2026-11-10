# AI 로드메이트 · AI Roadmate

차량 상태와 함께하는 오리지널 동반자, 정차 후 드라이브 기억을 연결하는 **Pleos Connect / Android Automotive OS 앱 MVP**입니다. Kotlin과 Jetpack Compose로 구현했으며, 차량 데이터와 시연 데이터를 별도 앱으로 분리합니다.

## 현재 제공 범위

| Production | Demo |
|---|---|
| 실제 하향등·누적 주행거리·앞좌석 왼쪽 공조 목표 온도 읽기 | SIMULATED 입력을 사용하는 케어 시나리오 |
| AAOS motion 기반 트립 시작·정차·재개·완료 | 하향등·온도·휴식·경고 표현과 상호작용 |
| 명시 동의 후 앱 로컬 기록·복원·삭제 | 가상 지역 엽서·회고·시즌·보상 체험 |
| 정차 후 기억 카드·선택 회고·무료 로컬 시즌 | DEMO 표기, 별도 앱 ID와 저장 경로 |

필수 입력이 검증되지 않은 자동 Care와 실제 지역 엽서 발급은 비활성입니다. 실제 경고 Care·음성·지도·위치·차량 제어·외부 LLM·서버·결제는 현재 제공하지 않습니다. 자세한 구분은 [MVP 범위](docs/MVP_SCOPE.md)에 있습니다.

**구현 검증 완료 / USER_ACCEPTANCE_PENDING.** 사용자 직접 인수, 최종 개인정보 문안, Market·DO 심사와 실차 검증이 남아 있습니다. 배포 승인이나 제출 완료를 의미하지 않습니다.

## 빠른 시작

검증 환경은 JDK21.0.4, Android SDK35/build-tools35.0.1, Gradle Wrapper8.13입니다. 실행 대상은 Pleos Connect2.1.0/API34 AVD이며 minSdk28, compile/targetSdk35입니다.

Windows PowerShell, 프로젝트 루트에서:

~~~powershell
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat --gradle-user-home .gradle-user-home --no-daemon '-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=COMPAT' '-Pandroid.overridePathCheck=true' :app:assembleDemoDebug :app:assembleProductionDebug
~~~

첫 빌드에는 네트워크와 Android SDK가 필요합니다. Pleos SDK 의존성은 Production에만 포함됩니다. 전체 빌드·JVM 테스트·플랫폼 없는 core 테스트·APK 설치는 [빌드 및 실행 안내](docs/BUILD_AND_RUN.md)를 참조하세요.

| 실행 파일 | 경로 |
|---|---|
| Production debug | app/build/outputs/apk/production/debug/app-production-debug.apk |
| Demo debug | app/build/outputs/apk/demo/debug/app-demo-debug.apk |
| Production release | app/build/outputs/apk/production/release/app-production-release-unsigned.apk · 미서명 |

APK는 저장소에 포함하지 않고 빌드로 생성합니다. Demo release는 비활성입니다.

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

운행/정책 상태를 확인할 수 없으면 정차 콘텐츠를 잠급니다. 실제 차량 제어 경로는 활성화하지 않았고, 사용자 수락도 차량 설정 변경 성공을 뜻하지 않습니다. 기록은 기본 비활성이고 별도 기억 저장에는 추가 동의와 항목 확인이 필요합니다. [안전·개인정보 안내](docs/SAFETY_AND_PRIVACY.md)를 확인하세요.

최근 검증은 **492고유 테스트/516회, 실패·오류·skip0**, APK3종 빌드 성공입니다. 최종 offline/online lint는 각각 오류0·경고16입니다. 확대 접근성 검수는 PARTIAL이며 실차·심사 승인은 미확인입니다. 실행 범위와 제한은 [검증 요약](docs/VALIDATION_SUMMARY.md)에 기록했습니다.

전체 문서는 [문서 안내](docs/README.md), 직접 확인은 [사용자 검수 안내](docs/USER_ACCEPTANCE_GUIDE.md)와 [시연 동선](docs/SUBMISSION_DEMO_FLOW.md)을 따릅니다. 최초 Git 등록과 제외 규칙 확인은 [저장소 관리 안내](CONTRIBUTING.md)에 있습니다.
