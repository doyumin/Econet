# Econet

친구들끼리 쓰는 Paper 서버용 경제 플러그인. 이 문서는 core와 paper 레이어 작업 모두의 코드 기준이다.
게임 규칙과 그 결정 이유는 `docs/DESIGN.md`를 기준으로 한다.

## 모듈

- `core` — 순수 Kotlin 도메인. 패키지 `dev.doyu.econet.core`. Paper/Bukkit 의존성 0, 외부 의존성은 `kotlinx-coroutines-core`만.
  - 이유: 경제 규칙을 서버 없이 테스트하고, 플랫폼이 바뀌어도 그대로 쓰기 위해.
- `paper` — Paper 플러그인. 커맨드, 저장소 구현, 표시(포매팅), 설정을 담당한다.
- 빌드 설정(`gradle/libs.versions.toml`, 모듈 구조, Kotlin/JDK 25/Paper 버전)은 함부로 바꾸지 않는다. 의존성은 버전 카탈로그로만 추가한다.
- 옛 구현은 `v0-legacy` 태그에 남아 있다.

## 핵심 원칙: 돈은 이동만 한다

돈은 생기거나 사라지지 않고 계좌 사이를 이동만 한다. **모든 계좌 잔액의 합은 항상 0이다.**
- 이유: "합 = 0"이라는 단 하나의 불변식으로 복제·증발 버그를 언제든 검사할 수 있다.

잔액을 직접 설정하거나 더하는 API는 만들지 않는다. 모든 변화는 `Ledger.transfer`로만 일어난다.
- 이유: 우회 경로가 하나라도 생기면 불변식이 깨진다.

## 타입

| 타입 | 설명 | 결정 이유 |
|---|---|---|
| `Amount` | Long value class, 항상 양수(생성 시 검증). 송금 금액. | 0원·음수 송금을 타입 단계에서 막는다. |
| `Balance` | Long value class, 부호 있음. 연산은 `Math.addExact`/`subtractExact`. | Issuance가 음수가 될 수 있고, 오버플로는 돈의 생성·소멸이므로 조용히 넘기지 않는다. |
| `AccountId` | sealed: `Player(uuid)`, `Government`, `Issuance`, `CashPool` | 특수 계좌를 문자열이 아닌 타입으로 구분해 `when`이 빠짐없이 처리하게 한다. |
| `Actor` | sealed: `Player(uuid)`, `Admin(uuid)`, `System(source)` | 돈이 오간 계좌와 "누가 시켰는지"는 다른 정보다. |
| `TxKind` | Transfer, Mint, Burn, CashOut, CashIn, Reward, Purchase, Sale, Tax | 거래 분류. 특수 계좌 경로 검사에도 쓴다. |
| `Transaction` | id(`TransactionId`, Long 순번), from, to, amount, kind, actor, memo, at(Instant) | Long 순번은 재생 순서가 명확하고 SQLite AUTOINCREMENT와 맞는다. |
| `TransferResult` | `Success(transaction)` / `Failure`: `InsufficientFunds(balance, required)`, `SameAccount`, `Overflow`, `InvalidRoute`, `Locked` | 예상 가능한 실패는 예외가 아니라 값으로 돌려준다. 호출자가 `when`으로 빠짐없이 처리하게 하기 위해. |

## 계좌와 경로 규칙

- `Issuance`(발행 계좌): **유일하게 음수 잔액을 허용한다.** 발행은 Issuance 잔액을 음수로 내리는 것이고, 그 절댓값이 총 통화량이다.
  - 이유: 돈을 무에서 만들지 않고도 발행할 수 있어 합 = 0이 유지된다.
- `Government`(정부 금고): 발행된 돈이 처음 들어오는 곳.
- `CashPool`(현금 유통 계좌): 계좌 밖(아이템 등)으로 나간 돈을 장부 안에 묶어 둔다. 잔액 = 현금 유통량.
- 일반 계좌 = `Player`, `Government`.

| 종류 | 허용 경로 |
|---|---|
| `Mint` | Issuance → Government 만 |
| `Burn` | Government → Issuance 만 |
| `CashOut` | 일반 계좌 → CashPool 만 |
| `CashIn` | CashPool → 일반 계좌 만 |
| 나머지 (Transfer, Reward, Purchase, Sale, Tax) | 일반 계좌 ↔ 일반 계좌, 방향 자유 |

- Issuance와 CashPool은 위의 전용 종류로만 드나들 수 있다. 어기면 `InvalidRoute`.
  - 이유: 그래야 두 계좌의 잔액이 각각 "총 통화량", "현금 유통량"이라는 의미를 유지한다.
- 나머지 종류는 분류용 라벨이다. 방향(예: Tax는 Player → Government)은 paper 레이어가 맞게 호출할 책임이 있다.
  - 이유: 라벨까지 경로를 강제하면 사용처가 늘어날 때마다 core를 고쳐야 한다.
- 검사 순서: (Locked) → SameAccount → InvalidRoute → InsufficientFunds → Overflow. 실패하면 아무것도 바뀌지 않는다.
- 판정은 `decideTransfer`(core) 한 곳에서 한다. Ledger 구현은 저장만 다르고 판정은 이 함수를 쓴다.
  - 이유: 구현마다 규칙이나 검사 순서가 어긋나는 것을 막기 위해.
- 거래한 적 없는 계좌의 잔액은 0이다. 계좌 개설 절차는 없다.
  - 이유: 잔액 0인 계좌와 없는 계좌를 구분할 필요가 없고, 개설 단계가 있으면 그만큼 실패 경로가 늘어난다.

## 표시

core는 화폐 이름("듄")이나 코드("DYN")를 모른다. `toString`에는 숫자만 쓰고 단위를 붙이지 않는다.
표시는 paper 쪽 포매터가 설정을 읽어 처리한다.
- 이유: 표시 방식은 서버 설정이고, 도메인 값에 섞이면 로그·저장 포맷까지 오염된다.

## Ledger

```kotlin
interface Ledger {
    suspend fun transfer(from, to, amount, kind, actor, memo = null): TransferResult
    suspend fun balanceOf(id): Balance
    suspend fun history(id, limit): List<Transaction>   // 최신순
    suspend fun allBalances(): Map<AccountId, Balance>
    suspend fun allTransactions(): List<Transaction>    // 기록 순(오래된 것부터)
}
```

- 인터페이스로 둔다. 저장소(메모리, SQLite 등)를 갈아 끼울 경계이기 때문이다. "구현이 하나면 인터페이스 금지" 규칙의 유일한 예외.
- `suspend`: 저장소 구현이 I/O를 할 수 있어야 하므로.
- `allBalances`/`allTransactions`: 무결성 검사가 어떤 구현에서든 동작하게 하기 위해 인터페이스에 둔다.
- 경로 규칙은 `TxKind.permits`, 음수 허용은 `AccountId.allowsNegativeBalance`에 있고, 둘을 묶은 판정이 `decideTransfer`다. 새 구현도 이것을 써야 한다.
  - 이유: 규칙은 저장소가 아니라 도메인에 속한다.

### InMemoryLedger

테스트·개발용 구현. 모든 연산(읽기 포함)을 `Mutex` 하나로 직렬화한다. `Clock`은 생성자로 주입받는다.
- 이유: 잔액 확인과 갱신 사이에 다른 송금이 끼어들면 잔액 부족 검사가 무의미해진다. Clock 주입은 시간을 테스트에서 고정하기 위해.

### SqliteLedger (paper 모듈, `dev.doyu.econet.paper.storage`)

운영용 구현. DESIGN.md 8장을 따른다.
- 직접 JDBC, SQLite 파일 하나, 테이블은 `accounts`(잔액 캐시)와 `transactions`(장부, `AUTOINCREMENT`).
  - 이유: 쿼리가 몇 개뿐이고 SQL이 그대로 보여 읽기 쉽다.
- 연결 하나를 전용 쓰기 스레드 하나에서만 쓴다. 거래 한 건 = DB 트랜잭션 한 번. DB 커밋이 성공한 뒤에만 메모리 캐시를 갱신한다.
  - 이유: 메인 스레드를 막지 않고, 스레드가 하나라 거래가 저절로 직렬화되며, 실패해도 되돌릴 메모리 상태가 없다.
- 잔액 조회(`balanceOf`, `allBalances`)는 캐시에서 바로 읽는다. 쓰기 스레드를 거치지 않는다.
- 평소 연결은 autoCommit, 쓰기·검사만 명시적 트랜잭션.
  - 이유: 읽기 후 열린 트랜잭션이 옛 스냅샷을 붙잡으면 외부 변경(복구 도구 등)을 못 본다.
- `checkIntegrity()`: 쓰기 스레드 위에서 DB 잔액 테이블과 장부 재생을 한 트랜잭션으로 비교한다. 실패하면 잠긴다(`isLocked`). 잠긴 동안 `transfer`는 `Locked`를 돌려주고, 잔액 조회는 된다.
- `repair()`: 잔액 테이블을 장부 재생 결과로 덮어쓰고 캐시를 다시 채운 뒤 잠금을 푼다.
- DB에 남는 계좌·주체 문자열(`player:<uuid>`, `government` 등)은 `StorageKeys.kt`에서 직접 정한다.
  - 이유: 클래스 이름을 바꿔도 저장 형식이 깨지지 않게.
- 거래 시각은 밀리초로 잘라 저장하고, 돌려주는 `Transaction`도 같은 값을 쓴다.

### 무결성 검사

`integrityReportOf(stored, transactions)`가 순수 계산이고, `suspend fun Ledger.verifyIntegrity(): IntegrityReport`는 그 위의 범용 래퍼다.
1. 전체 잔액 합 == 0 (`BigInteger`로 합산, 중간 오버플로 방지)
2. 저장된 잔액 == 거래 기록을 처음부터 재생한 잔액 (불일치는 `BalanceMismatch`로 보고)

범용 `verifyIntegrity`는 잔액과 장부를 따로 읽으므로 송금이 없을 때 호출한다. `SqliteLedger.checkIntegrity`는 한 시점으로 묶어 검사하므로 언제든 쓸 수 있다.
- 이유: 저장된 잔액은 캐시이고 진실은 거래 기록이다. 검사 도중 송금이 끼어들면 두 값이 서로 다른 시점을 가리킨다.

## paper 레이어 규칙

- 로직은 Bukkit과 분리해 단위 테스트하고, Paper 연결부(명령어·리스너)는 얇게 유지한다.
  - 이유: 실제 서버 없이 검증할 수 있는 범위를 최대로 넓히기 위해.
- 명령어는 Paper Brigadier lifecycle(`LifecycleEvents.COMMANDS`)에 `BasicCommand`로 등록한다. 영어 이름 + 한글 별칭.
  - Paper 26.3에서 한글 별칭이 등록·실행되는 것을 실제 서버 콘솔로 확인했다.
- 장부 호출은 플러그인 `CoroutineScope`(Dispatchers.Default)에서 한다. 메인 스레드에서 `runBlocking`으로 장부를 기다리지 않는다. 예외는 서버 시작 시 무결성 검사 한 번.
  - 이유: 메인 스레드가 멈추면 서버 전체가 멈춘다.
- 월드·인벤토리·접속자 목록은 메인 스레드에서만 만진다(`MainThreadDispatcher`).
- `onDisable`은 메인 스레드에서 남은 작업을 잠깐 기다린다. 그러니 종료 직전에 끝나야 하는 작업이 메인 스레드를 기다리게(`withContext(mainThread)`) 만들지 않는다. 알림처럼 결과를 기다릴 필요 없는 일은 `runTask`로 넘기기만 한다.
  - 이유: 서로 기다리는 교착으로 그 작업이 취소된다. 실제 서버에서 겪었다.
- 금액 표시는 `MoneyFormat.display`(플레이어 화면, `12,500듄`), 로그·관리자 출력은 `MoneyFormat.plain`(`12500 DYN`).
- 이름으로 플레이어를 찾을 때는 `findKnownPlayer`만 쓴다. 접속한 적 없는 이름은 거절한다.
  - 이유: `getOfflinePlayer(name)`는 모르는 이름에도 가짜 UUID를 만들어, 오타 낸 송금이 주인 없는 계좌로 간다.
- 실패 메시지는 `failureMessage` 한 곳의 `when`을 거친다.
- 설정 값이 틀리면 기본값으로 덮지 않고 시작 시 실패한다. 빠진 키는 Bukkit이 번들 `config.yml` 값으로 채운다.
  - 이유: 틀린 값을 조용히 쓰는 것보다 멈추는 게 낫고, 새 버전에서 키가 늘어도 기존 설정 파일은 그대로 동작해야 한다.
- DESIGN.md에서 미정인 숫자는 설정으로 빼고 주석에 "임시값"이라고 적는다.

## 정부 매입 (3단계)

- 시세(`BuybackMarket`)는 직선 모델: 한 개 팔릴 때마다 `drop-per-item`만큼 떨어지고, 바닥(`floor-ratio`)에서 멈추며, 바닥에서 기준가까지 `recovery-hours`에 걸쳐 직선으로 회복한다. 압력은 바닥 도달량 이상 쌓지 않는다.
  - 이유: 숫자가 미정인 단계라 운영자가 "몇 개면 바닥, 몇 시간이면 회복"을 바로 계산할 수 있어야 한다. 압력에 상한을 둬야 "하루에 걸쳐 회복"이 지켜진다.
- 내부 가격은 BigDecimal(단가는 소수 4자리), 지급액은 한 번의 판매 합계만 내림한다.
  - 이유: double 누적 오차가 내림에서 1듄을 갉아먹는다. 실제로 테스트에서 9.999…가 나왔다.
- 매입 대상은 "손대지 않은 기본 아이템"만 받는다(`buybackKeyOf`: `isSimilar(ItemStack.of(type))`). 이름·인챈트·태그가 붙으면 돌려준다.
  - 이유: 금화 같은 태그 아이템과 앞으로 생길 특수 아이템을 품목별 예외 없이 한 규칙으로 거른다. 실제 서버에서 확인했다.
- 판매 상자 정산(`SaleSettlement`)은 모든 결과에 돌려줄 아이템(`returned`)을 담는다. 지급 거래가 성공한 뒤에만 아이템을 판 것으로 친다.
- 정산은 메인 스레드에서 장부를 기다린다(`runBlocking`). 이 규칙의 유일한 예외.
  - 이유: 비동기로 하면 지급을 기다리는 동안 아이템이 어디에도 없는 순간이 생기고, 그 사이 종료되면 돌려줄 수 없다.
- 닫힘 경로별 반환: 일반·접속 종료 → 인벤토리(넘치면 발밑), 사망 → 그 자리에 드롭, 플러그인 종료 → `settleAll`로 직접 정산.
  - 이유(Paper 26.3 서버 코드에서 확인): 접속 종료는 닫힘 → 퇴장 이벤트 → 저장 순서라 인벤토리에 넣으면 저장된다. 사망은 드롭 결정 → 닫힘 순서라 인벤토리에 넣으면 사라질 수 있다. 서버 종료는 플러그인 비활성화가 플레이어 저장보다 먼저라, 닫힘 이벤트를 기다리지 않고 직접 처리해야 한다.
- 판매 상자 아랫줄 UI 아이템에는 고유 태그를 단다. 그 칸을 건드리는 클릭·드래그는 취소한다.
  - 이유: 태그가 없으면 플레이어의 같은 아이템이 그 칸에 합쳐져, 돌려주지 않는 칸으로 사라진다.
- 시세 압력은 돈이 아니므로 장부가 아니라 `market.yml`에 둔다.

## 코드 스타일: 잘 읽히는 클린 코드

- 구현이 하나뿐이면 인터페이스를 만들지 않는다. (Ledger는 예외)
  - 이유: 추상화는 갈아 끼울 실제 이유가 생겼을 때 만든다.
- `Manager`/`Handler`/`Util` 같은 이름 금지. 역할이 이름에 드러나게 짓는다.
  - 이유: 이런 이름은 무엇이든 담을 수 있어서 결국 무엇이든 담게 된다.
- 스코프 함수(`let`/`also`/`run`/`apply`)는 중첩하지 않는다. 한 단계까지만.
  - 이유: 중첩되면 `it`/`this`가 무엇을 가리키는지 추적해야 한다.
- sealed 결과는 `when`으로 끝까지 받고 `else`로 뭉개지 않는다.
  - 이유: 새 실패 케이스가 추가되면 컴파일러가 처리하지 않은 곳을 알려 준다.
- 주석은 "왜"만 쓴다. 무엇을 하는지는 코드가 말한다.
  - 이유: "무엇" 주석은 코드와 쉽게 어긋난다.
- 한 번만 쓰이는 짧은 로직은 굳이 함수로 빼지 않는다.
  - 이유: 읽는 사람이 정의를 찾아 왔다 갔다 하게 만든다.
- 테스트는 `kotlin.test`, 코루틴 테스트는 `runBlocking<Unit>`.
  - 이유: 마지막 식이 값을 돌려주면 테스트 메서드가 void가 아니게 되어 JUnit이 조용히 건너뛴다.

## 빌드·테스트

```
./gradlew :core:test
./gradlew build
```

JDK 25 툴체인이 필요하다.
