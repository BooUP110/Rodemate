# 로컬 저장 보안

저장 파일은 Android Keystore 관리 키와 JCA **AES-256-GCM/NoPadding**으로 보호한다. 사용자 PIN·캐릭터·상수 문자열을 키로 쓰지 않으며, APK/설정 파일에 비밀 키를 넣지 않는다. 신규 crypto 라이브러리 의존성은 없다. 사용자 인수와 Market/실차 승인은 별도다.

## 책임과 형식

Demo 게임 진행·획득한 팩·동반자 여행 연결 정보도 `EncryptedJourneyGameStore`에서 같은 AES-GCM envelope 형식으로 보호한다. aggregate와 별도 Demo 키 namespace를 사용하며, 암호문 staging·fsync·atomic replace 후 성공으로 처리한다. 기존 평문 SharedPreferences는 암호화 이행 성공 후 제거한다. 읽기/인증/키 실패 시 기존 파일을 보존하고 쓰기를 차단한다.

게임의 여행 연결 정보는 회고 원문이나 별도 감정 기억이 아니며, Trip 기록 동의를 요구한다. 원본 Trip 삭제·기록 동의 철회 시 연결 정보를 제거하고, 시즌 진행 초기화 시 게임 진행도 함께 초기화한다. 엽서 자체는 기존 컬렉션 보존 정책을 따른다. Production에는 게임 저장 구현과 콜라보 자료 공급자를 넣지 않는다.

| 계층 | 책임 |
|---|---|
| `app/.../security/AndroidStoreKeys.kt` | AndroidKeyStore에서 키 생성/재사용. 키 재료·alias·예외 원문을 로그로 출력하지 않음 |
| `core/contracts/.../storage/EncryptedEnvelope.kt` | 표준 Cipher의 authenticated encryption 호출, 헤더/길이 검증과 오류 분류 |
| `TripStoreCodec` | Android와 독립된 도메인 직렬화/검증. schema1/2/3 읽기 호환, schema4 쓰기 |
| `FileTripRepository` | OS file lock 아래 읽기·migration·암호화 staging·재검증·fsync·atomic replace·StateFlow 발행 |

**Envelope version1 ≠ Domain schema4.** schema4는 명시 선호 온도를 추가한 도메인 변경이다. 암호화 metadata를 Trip/Memory 모델에 섞지 않는다. 기존 암호화 envelope 안의 schema3도 계속 읽을 수 있다.

Envelope v1:

| 필드 | 크기/검증 |
|---|---|
| magic | ASCII `RDMATEE1`, 8 bytes |
| envelopeVersion | int32, 현재1 |
| algorithm | 1 byte, AES-256-GCM/128-bit tag 식별자1 |
| environment | 1 byte, Demo0 / Production1 |
| IV length | 1 byte, 현재12 |
| ciphertext length | int32, tag 포함; 전체 길이 일치/최대 크기 검증 |
| IV | provider가 새 암호화 작업마다 생성한12 bytes |
| ciphertext + tag | 암호문 +128-bit 인증 tag |

19-byte 헤더는 AAD로 인증한다. IV는 `Cipher.init(ENCRYPT_MODE, key)`에서 provider가 생성하며 고정 IV나 수동 재사용을 하지 않는다. 복호화는 `GCMParameterSpec(128, iv)`와 헤더 AAD를 사용한다. 알고리즘/버전/환경/길이 오류도 읽기 실패이며 파일을 초기화하지 않는다.

## 키 생명주기

| 상황 | 처리 |
|---|---|
| 새 저장소/유효한 legacy migration | 기존 키를 조회하고 없을 때 Android Keystore AES256 키 생성 |
| encrypted reopen | **기존 키만 조회**; 읽기 실패를 새 키 생성으로 숨기지 않음 |
| 키 누락 | `KEY_MISSING`, 파일 보존, 민감한 캐시 상태를 화면에 내보내지 않음 |
| 키 무효/사용 불가 | `KEY_INVALIDATED` 또는 `ENCRYPTION_UNAVAILABLE`, 파일 보존. Cipher provider의 `ProviderException`도 메시지를 내보내지 않고 unavailable로 분류 |
| 인증 tag/IV/암호문 변조 | `AUTHENTICATION_FAILED`; 읽기/후속 쓰기 거부 |
| 알 수 없는 형식/schema | `INVALID_DATA` / `INCOMPATIBLE_SCHEMA`; destructive reset 없음 |
| 앱 삭제/데이터 지우기 | OS의 앱 데이터·Keystore 수명주기에 따름. 앱이 복구를 보장하지 않음 |
| 파일만 복원되고 키가 없음 | 정상 복원으로 처리하지 않음. 자동 키 교체/데이터 초기화 없음 |

Production/Demo는 앱 UID·저장 경로·키 namespace가 분리된다. 하드웨어 보안 모듈/StrongBox 사용을 강제하거나 AVD의 키가 하드웨어에 있다고 주장하지 않는다. 키 생성은 사용자 잠금/생체 인증에 의존하지 않으며, 잠금 해제된 앱을 사용하는 사람의 신원을 증명하지 않는다.

## 평문과 원자 교체

1. 기존 파일을 제한된 크기로 읽고, legacy인 경우 도메인을 검증한다.
2. 새 도메인 bytes는 메모리에서만 만들고 즉시 암호화한다.
3. `trips.pending`에는 **암호문만** 쓰고 파일을 fsync한다.
4. staging을 다시 인증/복호화하고 도메인이 의도한 새 상태와 같은지 확인한다.
5. atomic replace가 성공한 뒤에만 새 상태를 발행한다.

사용한 평문 byte array는 가능한 범위에서 zero-fill한다. JVM/Android의 immutable String·복사본·GC 동작까지 완전한 메모리 소거를 보장하지는 않는다. 앱이 만드는 평문 backup/copy는 없다. 성공 전 장애는 이전 정상 파일을 보존한다. legacy migration 실패 시 기존 평문 원본을 보존하는 것은 데이터 손실 방지 정책이며, 보안 완료로 표시하지 않는다.

고아 staging은 정상 committed 파일을 읽은 뒤 폐기하며 자동 승격하지 않는다. staging 경로가 디렉터리이거나 write/fsync/move가 실패하면 성공으로 보고하지 않는다. 파일 교체는 기존 `ATOMIC_MOVE` 계약을 유지하며 비원자 교체로 조용히 후퇴하지 않는다. 임의 전원 손실/모든 파일시스템의 내구성 보장은 아니다.

## 삭제·동의·로그

기록 삭제/동의 철회는 실제 aggregate에서 Trip·회고·연결된 기억을 제거하고 다시 암호화한다. 암호화를 삭제의 대체 수단으로 사용하지 않는다. 기존의 진행도/보상·컬렉션 보존과 가명 연결 제거 정책은 유지한다. 저장한 선호는 FUNCTIONAL_PREFERENCE의 독립 목적이므로 별도 삭제 버튼/동의 철회로 제거한다. Trip 삭제가 선호까지 삭제한다는 안내는 하지 않는다.

자유 회고·선호의 불필요한 raw 값·암호문·키 재료·Keystore alias를 로그/예외 메시지로 전파하지 않는다. AVD 검사는 고정 테스트 문구와 별도 fixture/테스트 앱만 사용한다. 원래 사용자 파일을 평문 artifact로 추출하지 않는다.

## 검증과 한계

기존 보안 JVM 테스트에서 legacy1/2/3, encrypted reopen, 암호문/IV 변조, 키 누락/무효 fixture, staging 실패, 동의/삭제, persisted bytes를 검사한다. 실제 Android Keystore의 이전 Pleos API34 AVD 관측과 이번 JVM 회귀를 구분한다. 실제 키를 무효화하기 위해 사용자 키를 삭제하지 않는다. 최신 실행 범위는 [검증 요약](VALIDATION_SUMMARY.md)을 따른다. 원시 로그와 과거 보고서는 로컬 보관소에 보존한다.

AEAD는 파일의 변조를 검출하지만 전체 유효 파일의 과거 버전 replay, root/OS 침해, 앱 프로세스 침해, 플래시의 forensic secure erase까지 해결하지 않는다. legacy migration은 앱 전용 경로의 이전 도메인 형식을 검증하여 가져오는 경로다. 서버 원장/anti-rollback 보장은 추가하지 않았다.

공식 근거: [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [Android 권장 암호화](https://developer.android.com/privacy-and-security/cryptography), [랜덤 IV 요구](https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder#setRandomizedEncryptionRequired(boolean)), [Cipher AEAD/AAD](https://developer.android.com/reference/javax/crypto/Cipher), [Pleos 보안 기준](https://document.pleos.ai/docs/pleos-only/app-registration-guide/app-security-guide).
