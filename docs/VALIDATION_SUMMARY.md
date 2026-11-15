# 검증 현황

## 빌드와 회귀

화면 문구 정리 후 core 모델/계약·Demo/Production JVM 검사, Production release·체험 debug 빌드와 Production offline lint를 실행했다. **BUILD SUCCESSFUL 2분20초**,129개 task 중30실행·99UP-TO-DATE다.

JUnit 결과는 **626개 고유 테스트 /651회**, failure/error/skip0이며 기존626개 ID를 모두 보존했다. model30/contracts443/체험117/차량 연결61이다. 변경 없는 core 테스트는 UP-TO-DATE 결과를 포함하며651회 전부를 이번 신규 실행으로 세지 않는다. lint는 **Error/Fatal0·Warning3**이고 새 억제/baseline은 없다. 원격 최신 의존성 검사는 다시 실행하지 않았다.

차량/보상/Trip·동의·저장 정책과 SDK/Manifest는 바꾸지 않았다. core의 변경은 주석3곳이며, 시즌 표시 이름은 화면에서만 변환해 기존 카탈로그·원장·획득 기록을 유지한다. 앱 버튼·안내와 문서 표현을 다듬었고 화면 구조나 데이터 형식은 변경하지 않았다.

## Release APK

| 항목 | 값 |
|---|---|
| 파일 | [BooUP_카펫_release.apk](../apk/BooUP_카펫_release.apk) |
| package / version | `io.booup.roadmate` /1.0.0(1) |
| 크기 | 86,607,960bytes |
| SHA256 | `4fde8ee9eb755ab4495788950929c518aa886c5432eeffb7011fe3dbb65799bd` |
| 인증서 SHA256 | `56ecaf304d39e807d42452c25dea51ebaca475a19c462364dfecc6337152bade` |
| 서명·정렬 | 기존 공모전 키, apksigner verify·zipalign PASS |
| debuggable / minSdk / targetSdk | false /28 /35 |
| 별도 AVD 설치·실행 | **미실행** |

Production payload의 Demo adapter/host 시나리오·계측/테스트 키·고정 테스트 문구·내부 콜라보 이미지/카탈로그 부재를 검사했다. release 빌드 성공과 서명 검증을 새 기기 실행 성공으로 해석하지 않는다.

## 플랫폼 확인 범위

이전 Pleos Connect2.1.0/API34·2560×1440/160dpi 환경에서 실제 SDK/AAOS 읽기·motion/UX 제한·Trip·Keystore 저장/복원·삭제·펫 동행/휴식/검색을 확인했다. 이번 문구 변경 후 AVD를 다시 실행하거나 기존 사용자 앱/데이터를 변경하지 않았다.

실차·다른 VHAL·장기 성능/전원 장애·새 펫 화면 확대/분할·전체 로터리/스크린리더·Market/DO·사용자 직접 인수는 미완료다. 앱 관측 시간과 실제 연속 운전/휴식·건강 상태, 오프라인 장소 사실과 실시간 입장 허가를 구분한다.
