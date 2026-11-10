# Phase 8.6 — 저장 형식 전환

저장 경로/파일 이름은 유지한다. Production은 `noBackupFilesDir/roadmate-production/trips.v1.bin`, Demo는 별도 앱의 `roadmate-demo/trips.v1.bin`이다. `v1` 파일 이름과 현재 도메인 schema/envelope 버전은 서로 다른 개념이다.

| 입력 | 읽기/변환 |
|---|---|
| plaintext schema1 | Trip·동의·active·memory를 검증하여 유지, 없는 후속 필드는 기본값 |
| plaintext schema2 | 기존 reflection/region/postcard metadata도 검증하여 유지 |
| plaintext schema3 | progress 원장/claim/시즌을 포함하여 검증하고 유지 |
| envelope1 + schema3 | 기존 암호화 파일 그대로 읽기 가능. 선호는 없음 |
| envelope1 + schema4 | 선호 값/source/확인 시각·FUNCTIONAL_PREFERENCE 동의를 포함 |
| 알 수 없는 schema/변조/키 누락 | 실패를 구분하고 기존 파일 보존. 빈 저장소로 reset하지 않음 |

plaintext1/2/3을 읽을 때 암호화된 staging을 생성·fsync·재검증하고 atomic replace한다. **데이터 삭제나 재가입을 migration의 선행 조건으로 요구하지 않는다.** 별도 평문 backup을 생성하지 않는다. 실패하면 기존 원본을 보존하고 UI에서 성공으로 표시하지 않는다.

암호화 키는 UID/기기와 연결되므로 다른 차량/사용자/설치로 파일만 복사하는 것은 지원하는 동기화·백업 방식이 아니다. noBackup/allowBackup=false를 유지한다. Android 데이터 이전 규칙/lint 경고와 최종 개인정보 문안은 별도 검토 항목이다.

기존 사용자 수락용 데이터는 지우지 않았다. AVD의 원래 Production aggregate는 실제로 암호화 전환한 뒤 프로세스 재시작으로 다시 읽었고 기존 진행도 항목을 보존했다. destructive 테스트는 별도 fixture/격리된 acceptance 앱 데이터에서 수행한다. 정확한 결과/제한은 [실행 보고서](PHASE8_6_ENHANCED_LOCAL_PLATFORM_REPORT.md)를 따른다.
