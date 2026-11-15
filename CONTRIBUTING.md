# 개발 및 저장소 관리

소스·Wrapper·테스트·제품/개발 문서·검증한 release APK를 관리한다. 개발 실행 보고서·접수 자료·원시 로그·다운로드 캐시·비밀 설정은 Git 제외 로컬 보관소에 둔다.

| 포함 | 제외 |
|---|---|
| app/core 소스·테스트·fixture·오리지널 에셋·고지 | build·캐시·임시 probe APK·원시 사용자 자료 |
| Wrapper·버전 카탈로그·도구 소스 | local.properties·편집기 설정·다운로드 SDK/source |
| README·제품 기획·설계·사용/개발 문서 | development·.tools·.dist 전체 |
| `apk/BooUP_카펫_release.apk` | 기타 APK/AAB·서명키·비밀번호·서명/개인정보 양식 |

실제 차량과 모의 입력의 source·flavor·저장·권한 경계를 유지한다. UI 문구 변경 때문에 직렬화 enum·ID·시즌 원장·동의·안전 gate를 바꾸지 않는다. 보상/캐릭터가 차량 판단을 바꾸지 않게 한다.

커밋 전에 변경/삭제/추가 목록·공백·민감정보·APK 해시를 검토한다. `.gitignore`는 기존 추적 파일이나 Git 이력을 지우지 않는다. 과거 경로와 사용권은 공개 전에 별도 확인한다. 키·credential은 공유하지 않고 안전하게 백업한다.

[빌드 안내](docs/BUILD_AND_RUN.md) · [아키텍처](docs/ARCHITECTURE.md) · [구성요소 출처](THIRD_PARTY_NOTICES.md)
