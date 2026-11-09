# 시연 동선

**USER_ACCEPTANCE_PENDING / 제출 준비 NOT YET.** 실제 관측과 합성 시연을 구분하는 앱 실행 안내이며 최종 발표 자료나 심사 승인 문서는 아니다.

## A. Production — 실제 AVD 입력

| 순서 | 동작 | 설명 |
|---|---|---|
| 1 | 동행 홈 | 실제 차량 연결·현재 Trip·오리지널 임시 동반자 |
| 2 | 설정에서 기록 저장 선택 | 기본 꺼짐·이 기기 저장·별도 기억 동의 구분 |
| 3 | Console 하향등·앞좌석 왼쪽 목표 온도 확인 | 실제 읽기 전용 값. 운전자 의미/자동 제어로 확대하지 않음 |
| 4 | Drive10km/h 10초 이상 | motion 기반 Trip 시작. 비 DO 플랫폼 주행 차단 유지 |
| 5 | 짧은 정차 후 재개 | 같은 Trip 유지. 휴식 확증/연속 운전 시간과 구분 |
| 6 | Park0km/h 120초 이상 | 기본 종료 임계값 유지. 전체 Live에 약3분 이상 배정 |
| 7 | 기억 카드·선택 회고 | 확인된 사실·UTC·사용자 이야기만 표시 |
| 8 | 재실행·복원·삭제 확인 | 로컬 저장과 삭제 유지 |
| 9 | 무료 시즌·빈 컬렉션 | 보상 별도 수령, 실제 지역 부재 설명 |

시간이 부족해도 Production 임계값을 줄이거나 Demo 결과를 실제 결과로 설명하지 않는다. 기록된 화면을 별도로 준비하면 녹화/기록임을 명시한다. 현재 저장소가 원시 영상 파일을 제공한다고 주장하지 않는다.

## B. Demo — 명시적으로 앱 전환

AI 로드메이트 (Demo)를 실행하고 상단 **DEMO · SIMULATED**와 차량 변경 없음 안내를 먼저 설명한다. 정차 시 “시연 선택”에서 그룹을 선택한다. 기존 전체 ID는 추가 시나리오 목록에 있다.

| 시나리오 | ID | 구분 |
|---|---|---|
| 하향등 P1 | I1_P1_LIGHT | 합성 NIGHT, 약10초 후 안내 |
| 공조 제안/수락 | I2_HVAC_ACCEPT | 합성 외기/운전자/선호, 제어 없음 |
| 휴식 제안 | C4_CONTINUOUS_DRIVE | 합성 confirmed 연속 시간 |
| 경고 표현 | C5_IMMEDIATE_WARNING | 합성 분류, 실제 제조사 경고 수신 아님 |
| 동반자 선택 | I4_CHARACTER_SWITCH | 오리지널 임시 표현·동일 안전 정책 |
| 트립 | T1_NORMAL_TRIP | 합성 시작/완료 |
| 선택 회고 | P5_4_LOCAL_REFLECTION | 합성 기록/회고 |
| 지역 엽서 | P5_7_FIRST_REGION | 가상 지역 |
| 시즌/보상 | S1_FIRST_TRIP | 무료 보상 “확인하고 받기”→확인창 “받기” |
| 수령 상태 예시 | S7_FREE_CLAIM | 자동 시연된 무료 수령 상태 |

시나리오는 Demo 데이터와 합성 동의를 초기화할 수 있다. 제한 중 선택기를 우회해서 열지 않는다. 다음 명령은 Demo 전용 입력이며 Production에는 적용되지 않는다. 빌드 안내의 roadmateAdb/roadmateUser 설정 후 실행한다.

~~~powershell
$roadmateScenario = "I1_P1_LIGHT"
& $roadmateAdb -s emulator-5554 shell am start --user $roadmateUser -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -f 0x10200000 -n io.booup.roadmate.demo/io.booup.roadmate.MainActivity --es roadmate.demo.scenario $roadmateScenario
~~~

## 마무리

Production으로 돌아와 [현재 범위](MVP_SCOPE.md)의 비활성/후속 기능을 설명한다. 실제 음성/제어/위치/LLM/결제·Market 승인으로 오인시키지 않는다. Console 입력을 복원하고 [직접 검수 체크리스트](USER_ACCEPTANCE_GUIDE.md)를 진행한다.
