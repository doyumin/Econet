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
- 장부 호출은 플러그인 `CoroutineScope`(Dispatchers.Default)에서 한다. 메인 스레드에서 `runBlocking`으로 장부를 기다리지 않는다. 예외는 셋: 서버 시작 시 무결성 검사, **아이템과 돈이 함께 움직이는 처리**(판매 상자 정산, 인출·입금, 상점 구매, 사망 보험), 그리고 **백업**(같은 시점 보장).
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
- 정산은 메인 스레드에서 장부를 기다린다(`runBlocking`). "아이템과 돈이 함께 움직이는 처리" 예외에 해당한다.
  - 이유: 비동기로 하면 지급을 기다리는 동안 아이템이 어디에도 없는 순간이 생기고, 그 사이 종료되면 돌려줄 수 없다.
- 닫힘 경로별 반환: 일반·접속 종료 → 인벤토리(넘치면 발밑), 사망 → 그 자리에 드롭, 플러그인 종료 → `settleAll`로 직접 정산.
  - 이유(Paper 26.3 서버 코드에서 확인): 접속 종료는 닫힘 → 퇴장 이벤트 → 저장 순서라 인벤토리에 넣으면 저장된다. 사망은 드롭 결정 → 닫힘 순서라 인벤토리에 넣으면 사라질 수 있다. 서버 종료는 플러그인 비활성화가 플레이어 저장보다 먼저라, 닫힘 이벤트를 기다리지 않고 직접 처리해야 한다.
- 판매 상자 아랫줄 UI 아이템에는 고유 태그를 단다. 그 칸을 건드리는 클릭·드래그는 취소한다.
  - 이유: 태그가 없으면 플레이어의 같은 아이템이 그 칸에 합쳐져, 돌려주지 않는 칸으로 사라진다.
- 시세 압력은 돈이 아니므로 장부가 아니라 `market.yml`에 둔다.

## 현금 (4단계)

- 동전: 철화 1(철 조각), 금화 10(금 조각), 다이아화 100(다이아몬드). PDC 태그 `econet:coin` = 액면 이름. 이름·로어·반짝임을 붙인다.
- 동전 판별은 태그와 베이스 아이템이 **둘 다** 맞아야 한다(`CoinItems.denominationOf`).
  - 이유: 다른 아이템에 태그만 옮겨 붙인 경우를 막기 위해.
- 인출: 장부(CashOut)가 성공한 **뒤에** 동전을 만든다. 입금: 동전을 **먼저** 꺼내 두고, 입금(CashIn)이 성공하지 못하면 어떤 경우든 `finally`에서 되돌린다.
  - 이유: 어느 쪽이 실패해도 돈과 동전이 동시에 생기거나 동시에 사라지지 않게.
- 입금이 `InsufficientFunds`로 거절되면 동전 복사 의심으로 관리자에게 경고한다.
  - 이유: CashPool보다 많은 동전이 있다는 것은 장부 밖에서 동전이 생겼다는 뜻이다(DESIGN 2장).
- 인출은 동전이 인벤토리에 다 들어갈 때만 한다(복사본 인벤토리로 미리 넣어 본다). 한 번에 36×64개까지.
  - 이유: 대량의 동전을 바닥에 쏟지 않기 위해. 목록을 만들기 전에 개수부터 막아야 큰 금액에서 메모리가 터지지 않는다.
- 잔돈 교환(쪼개기·합치기)은 장부를 건드리지 않는다. 현금 총액이 그대로이기 때문이다.
- 이벤트용 동전은 `/econet coins`로 정부 금고에서 꺼낸다(Government → CashPool). 관리자 개인 예금을 거치지 않는다.
- 동전은 제작 재료로 쓸 수 없다(조합대 결과를 비우고, 크래프터 제작을 취소한다).
  - 이유: 레시피 책 자동 채우기가 동전을 집어 넣어 큰돈이 실수로 사라질 수 있다.
- 동전은 정부 매입 대상이 아니다(손대지 않은 기본 아이템이 아니므로 자동으로 걸러진다). 판매 상자에 넣으면 돌려주며 `/입금`을 안내한다.

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

테스트 서버는 Gradle 태스크로 띄운다(IntelliJ Gradle 패널의 `paper > run paper`에서도 같다).
```
./gradlew :paper:runServer                              # 평지·가벼운 설정으로 실행
./gradlew :paper:runServer -Pport=25566                 # 포트 지정 (server.properties에 남아 다음에도 쓰인다)
./gradlew :paper:resetTestServer :paper:runServer       # 월드·Econet 데이터를 지우고 새 평지 맵
```
- 설정은 `prepareTestServer`가 `runServer` 전에 `paper/run/server.properties`에 덮어쓴다. 셸 스크립트를 따로 두지 않는다.
  - 이유: 플랫폼마다 실행 방법이 달라지고, 설정이 Gradle과 스크립트 두 군데로 갈라진다.
- 26.3은 `generator-settings={}`이면 평지 레이어가 없어 오버월드를 만들지 못하므로, 레이어를 직접 적는다.
- 26.3은 접속자가 없으면 스폰 청크가 로드되지 않는다. 콘솔에서 블록을 확인하려면 먼저 `forceload add`를 한다.

### 배포 (`./gradlew :paper:deploy`)

- `deployUpload`(shadowJar → SFTP로 `plugins/`) → `deployRestart`(Pterodactyl Client API `power` = restart). 태스크 구현은 `buildSrc/src/main/kotlin/econet/deploy/`.
- 접속 정보는 저장소에 두지 않는다. `~/.gradle/gradle.properties` 또는 환경 변수(`econet.sftp.host` → `ECONET_SFTP_HOST`)에서 읽고, 없으면 실행할 때 키 이름을 밝혀 실패한다.
  - 이유: 비밀번호·API 키가 커밋되지 않게. 배포하지 않는 빌드는 설정 없이 돌아야 한다.
- SFTP 연산만 쓴다(sshj, 순수 Java). Pterodactyl은 셸·scp를 열어 주지 않는다.
- 서버의 파일명은 버전 없이 `Econet.jar`로 고정한다.
  - 이유: 버전이 올라도 같은 이름을 덮어쓰면 되고, 버전이 다른 jar가 둘 남아 플러그인이 두 번 로드되는 일이 없다.
- 순서: `Econet.jar.uploading`으로 업로드 → 크기 확인 → 이 플러그인의 옛 파일(`Econet.jar`, 옛 `Econet-*.jar`, `*.jar.uploading`)만 삭제 → 최종 이름으로 rename.
  - 이유: 업로드가 실패해도 돌아가던 jar가 남고, 임시 파일은 `.jar`로 끝나지 않아 서버가 반쯤 쓴 파일을 읽지 않는다. 다른 플러그인 파일은 이름 접두사로 걸러 절대 건드리지 않는다.
- 서버 신원은 `~/.ssh/known_hosts` 또는 `econet.sftp.hostKey`(SHA256 지문)로 확인한다. 둘 다 없으면 서버 지문을 알려 주며 멈춘다.
  - 이유: 확인 없이 접속하면 가로챈 서버에 비밀번호를 넘길 수 있다.
- 인증은 키(`keyPath`) → 비밀번호 순으로 시도한다. 패널 URL은 https만 허용한다(로컬 시험 서버 제외).

## 보험·현상금·상점 (5단계)

- 사망 보험: 가입자가 죽으면 보험료(Player → Government, `Purchase`, 주체 `System("insurance")`)를 받고 `keepInventory = true`, `drops`를 비운 뒤 동전만 인벤토리에서 빼 `drops`에 넣는다.
  - 이유: Paper 26.3은 keepInventory와 상관없이 `drops`를 떨군다(CraftEventFactory 확인). 그래서 이 조합이면 "인벤토리는 지키고 동전은 떨군다"가 된다.
- 보험료를 못 내면(잔액 부족·잠금·오류) 보험이 적용되지 않고 일반 사망 처리된다. 서버가 이미 keepInventory면 보험료를 받지 않는다.
- 가입 명단은 `insurance.yml`에 둔다. 돈이 아니라 약정이라서다.
- 현상금: 보스급만(설정 `bounty.rewards`). 처치한 플레이어(`killer`)에게 Government → Player `Reward`. 아이템이 오가지 않으므로 비동기로 처리한다. 키는 엔티티 ID뿐이다. 습격 대장은 습격 농장이 흔해 무한 수입이 되므로 대상에서 뺐다.
- 정부 상점(틀만): `shop.yml`(`ShopCatalog`)의 순서대로 진열한다. 클릭하면 1개를 산다(Player → Government `Purchase`). 공간을 먼저 확인하고, 돈을 받은 뒤 아이템을 준다. 상점이 열려 있는 동안의 클릭·드래그는 모두 취소한다.
  - 이유: 진열품은 견본이라 꺼낼 수 없어야 한다. 위쪽만 막으면 shift-클릭·숫자키 같은 경로를 하나씩 따져야 한다.
- 상점 품목은 `/상점관리 추가|삭제|목록`(`ShopAdminCommand`, 관리자)으로 바꾼다. 바뀐 품목은 `shop.yml`에 저장하고, 파일이 없을 때만 config.yml의 `shop.items`를 시작값으로 복사한다. 그 뒤 config.yml의 값은 무시한다.
  - 이유: 명령어로 바꾸는 값을 config.yml에 쓰면 Bukkit이 저장하면서 주석("임시값" 표시 등)을 모두 지운다. 보험 명단·시세 상태처럼 서버가 쓰는 데이터는 설정과 파일을 나눈다.
  - 최대 54종(상자 한 화면). 가득 차면 새 품목은 거절하고 가격 변경만 받는다. 이미 열려 있는 상점 화면은 닫았다 열어야 바뀐 품목이 보인다.
- 설정의 아이템·엔티티 ID는 시작 시 Registry로 검사해 경고한다.
  - 이유: 오타는 조용히 "매입 안 함·지급 안 함"이 되기 때문이다.
- 인벤토리 공간 확인은 `fitsInInventory`(복사본에 넣어 보기), 지급은 `receive`(넘치면 발밑)로 통일한다.

## 관리자 도구 (6단계)

- `/econet inv|ender <플레이어>`: 접속 중이면 실제 인벤토리·엔더상자를 연다(실시간, 수정 가능). 오프라인이면 저장 파일의 사본을 **읽기 전용**(`ReadOnlyView`, 모든 클릭·드래그 취소)으로 연다.
  - 이유: 오프라인 파일을 고치면 접속 시 서버가 덮어써 수정이 사라지거나 아이템이 복제된다.
- 오프라인 파일: Paper 26.3은 `<월드 루트>/players/data/<uuid>.dat`(DESIGN의 `playerdata/`는 옛 위치라 대체 경로로만 본다). 키는 `Inventory`·`EnderItems`(원소에 `Slot`), `equipment`(head/chest/legs/feet/offhand/body/saddle), 루트 `DataVersion`.
- 26.3의 `World.getWorldFolder()`는 차원 폴더(`world/dimensions/minecraft/overworld`)를 돌려준다. 월드 루트는 `level.dat`가 있는 폴더까지 올라가 찾는다. 실제 서버에서 발견했다.
- NBT는 서버 내부 클래스(NMS) 없이 직접 읽는다(`Nbt.kt`). 아이템 변환만 `ItemStack.deserializeBytes`에 맡긴다: 아이템 compound에서 `Slot`을 빼고 `DataVersion`을 붙여 gzip.
  - 이유: NMS는 버전마다 깨진다. Paper가 DataVersion을 보고 옛 아이템도 현재 형식으로 변환해 준다. 실제 서버에서 인챈트·이름·동전·셜커·꾸러미가 원본과 같게 돌아오는 것을 확인했다.
- 동전 집계(`/econet census`): 접속자는 실제 인벤토리, 오프라인은 파일을 읽어 인벤토리·엔더상자·장비 안의 동전을 센다. 셜커 상자·꾸러미 안까지(깊이 4) 센다. 총액과 CashPool의 차이만 보여 주고, 개인별 보유량은 보여 주지 않는다.
  - 이유: 월드 상자는 세지 않으므로 참고용이고(DESIGN 9장), 현금의 익명성(DESIGN 6장)을 평상시에 해치지 않기 위해서다. 차이가 음수면 장부보다 동전이 많다는 뜻(복사 의심)이다.
- 백업(`/econet backup`, 설정 주기): 메인 스레드에서 `savePlayers()` → `SqliteLedger.backupTo`(VACUUM INTO, 쓰기 스레드) → 플레이어 파일 복사를 한 번에 한다. `backups/<UTC 시각>/`에 `econet.db`와 `playerdata/`가 같이 들어간다. 오래된 것부터 지워 `backup.keep`개를 남긴다.
  - 이유: 둘을 따로 복구하면 장부와 동전이 어긋난다. 메인 스레드가 멈춰 있는 동안에는 아이템과 돈을 함께 옮기는 처리가 끼어들 수 없다. DB 파일을 그냥 복사하면 WAL에 남은 최근 거래가 빠질 수 있다.
