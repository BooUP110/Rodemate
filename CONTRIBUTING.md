# 저장소 관리 및 첫 커밋

앱/테스트/빌드 입력과 공개 문서는 Git으로 관리한다. 로컬 설정·서명키·캐시·APK·개발 원시 자료는 .gitignore로 제외한다. development/는 로컬 보관소이며 Git 제외는 백업을 대신하지 않는다.

## 첫 저장소 등록

아래 명령은 사용자가 PowerShell에서 프로젝트 루트 기준으로 실행한다. 저장소가 이미 있으면 git init을 건너뛴다. 이 안내는 명령을 자동 실행하거나 원격 저장소를 생성하지 않는다.

~~~powershell
# 기존 저장소인지 확인. 저장소가 없으면 fatal 출력이 정상이다.
git rev-parse --show-toplevel

# 저장소가 없는 경우에만 최초 실행
git init

# 로컬/생성 파일 제외 규칙 확인
git check-ignore -v development/README.md .vscode/settings.json app/build/outputs/apk/production/debug/app-production-debug.apk

# 커밋할 경로를 명시한다. 하위 캐시/빌드 출력은 .gitignore가 제외한다.
git add -- .gitignore .gitattributes README.md CONTRIBUTING.md docs app core gradle tools build.gradle.kts build-core.gradle.kts settings.gradle.kts gradle.properties gradlew gradlew.bat

# macOS/Linux 체크아웃에서도 Wrapper를 실행할 수 있게 기록
git update-index --chmod=+x gradlew

# staged 파일/공백 오류/실제 변경을 검토
git diff --cached --stat
git diff --cached --check
git diff --cached

# 아래 검사에는 출력이 없어야 한다.
git diff --cached --name-only | Select-String '(^|/)(development|build|\.gradle|\.gradle-user-home|\.tools|\.dist|\.vscode)/|local\.properties$|\.(apk|aab|jks|keystore)$'

git commit -m "feat: implement AI Roadmate MVP and review documentation"
git status --short
~~~

처음 커밋할 때 Git 이름/이메일이 미설정이라면 본인의 값으로 저장소 로컬 git config user.name 및 git config user.email을 설정한다. 전역 계정 설정을 자동 변경하지 않는다. 원격 연결/push, 브랜치 전환/병합은 이 절차에 포함하지 않는다.

## 포함/제외 기준

| 포함 | 제외 |
|---|---|
| app/core 소스·JVM/계측 테스트 | 모든 build 출력·APK/AAB·로그 |
| Gradle Wrapper JAR/실행 파일·버전 카탈로그 | .gradle/.gradle-user-home/.kotlin·다운로드 도구 |
| schema1/2 golden BIN fixture | development/ 전체·로컬 편집기/에이전트 설정 |
| docs/와 소스셋 안내·선택 UI 관측 도구 소스 | local.properties·서명키·비밀 환경 설정 |

Wrapper JAR나 테스트 BIN/JSON/이미지를 일괄 제외하지 않는다. 앱 빌드는 development/ 없이 가능해야 하며, 실행/검증은 [빌드 안내](docs/BUILD_AND_RUN.md)를 따른다. 기존에 추적된 파일은 .gitignore만으로 해제되지 않으므로 staged 목록을 반드시 검토한다.
