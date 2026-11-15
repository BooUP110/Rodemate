# 구성요소·데이터·에셋 출처

이 문서는 확인한 출처와 license metadata를 기록한다. 프로젝트 자체에 오픈소스 라이선스를 자동 부여하지 않는다. 루트 LICENSE는 없으며 **LICENSE_DECISION_REQUIRED**다. 공개 열람과 소스의 재사용 허가는 별개다.

## 라이브러리

이번 `productionReleaseRuntimeClasspath` 보고서에 나온 dependency/constraint 좌표102개를 로컬 캐시의 실제 POM과 대조했다. 이것은 packaged binary 전체의 법적 감사를 완료한 SBOM이 아니다. [APK에 포함되는 좌표별 고지](app/src/main/assets/licenses/third-party-notices.txt), [Apache-2.0 원문](app/src/main/assets/licenses/apache-2.0.txt)을 제공한다. 버전 충돌 해석을 반영하므로 Kotlin 플러그인2.0.0과 runtime stdlib2.0.21을 혼동하지 않는다.

| 구성요소 | 실제 확인 | 출처·조건 |
|---|---|---|
| AndroidX Activity/Lifecycle/Compose/Material3 및 전이 구성요소 | 해당 POM의 Apache-2.0 선언 | [AndroidX 소스](https://android.googlesource.com/platform/frameworks/support/)·POM별 URL, 위 고지 참조 |
| Kotlin stdlib·Coroutines·Serialization·JetBrains annotations | 해당 POM의 Apache-2.0 선언 | [Kotlin](https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt), [Coroutines](https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE.txt) |
| Pleos Vehicle2.3.2 / vehicle-data1.1.11 / vehicle-aidl1.1.1 | 다운로드 POM에 license 항목 없음 | [공식 Maven](https://nexus-playground.pleos.ai/repository/maven-releases/)·SDK 제공/배포 조건 별도 확인. 공개 SDK 다운로드를 오픈소스 허가로 해석하지 않음 |
| Guava listenablefuture1.0 / javax.inject1 | 이번 캐시 POM 자체에 license 항목 없음 | POM만으로 임의 license 지정하지 않음. 원저작물/상속 metadata의 배포 조건은 공개 전 최종 확인 대상 |
| JUnit4.13.2 | test-only POM의 EPL1.0 선언 | 제품 runtime 의존성이 아니며 테스트 소스로만 사용 |
| Gradle Wrapper8.13 | 검증된 공식 배포본의 Apache-2.0 LICENSE | Wrapper 배포 SHA256은 wrapper.properties에 고정 |

## 글꼴·지도·장소

- 제출 release의 `carpet_heading.ttf`는 실제 name table에서 **Jua**를 확인했다. Copyright2018 The Jua Project Authors, SIL OFL1.1. [원문](app/src/main/assets/licenses/jua-ofl.txt).
- Gowun Dodum, Copyright2021 The Gowun Dodum Project Authors, SIL OFL1.1. [원문](app/src/main/assets/licenses/gowundodum-ofl.txt). 로컬 Debug 글꼴 오버라이드는 제출 release 입력이 아니다.
- 행정경계: geoBoundaries KOR ADM2(2020), geoBoundaries/citypopulation.de, **CC BY3.0**, 228개 피처를 투영·반올림한 파생 자료. ADM1 그룹은 Natural Earth public-domain 자료와 행정명 보정. 원저자·고정 원본 링크·변형 내역은 [journey/NOTICE.txt](app/src/main/assets/journey/NOTICE.txt)에 있고 APK에 포함된다.
- 반려동물 장소8곳: 공식 기관/운영사의 이름·주소·이용 조건을 짧게 요약한 오프라인 사실 스냅샷. 이미지/기사 본문은 포함하지 않는다. [행별 공식 출처·확인일](docs/PET_MOBILITY_FEATURES.md). 웹 공개가 모든 콘텐츠의 재배포 허가를 뜻하지 않으며 실시간 API/전체 데이터 재배포는 별도 검토한다.

## 프로젝트 에셋과 제휴 표현

현재 제출 APK는 저장소의 오리지널 동반자·여행 배경·UI 효과음5종을 사용하며 내부 외부-IP 이미지/카탈로그는 포함하지 않는다. 제작 도구·입력 권리와 효과음 원본의 사용권은 팀이 최종 확인해야 한다. 실제 체결되지 않은 캐릭터·관광·반려동물 업체 제휴를 주장하지 않는다. 내부 콜라보 예시 소스의 존재가 해당 IP의 배포 허가를 뜻하지 않는다.
