package dev.doyu.econet.paper.storage

import dev.doyu.econet.core.AccountId
import dev.doyu.econet.core.Actor
import dev.doyu.econet.core.Amount
import dev.doyu.econet.core.Balance
import dev.doyu.econet.core.IntegrityReport
import dev.doyu.econet.core.Ledger
import dev.doyu.econet.core.Transaction
import dev.doyu.econet.core.TransactionId
import dev.doyu.econet.core.TransferDecision
import dev.doyu.econet.core.TransferResult
import dev.doyu.econet.core.TxKind
import dev.doyu.econet.core.decideTransfer
import dev.doyu.econet.core.integrityReportOf
import dev.doyu.econet.core.replayBalances
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 잔액(accounts)과 장부(transactions)를 SQLite 파일 하나에 저장한다.
 *
 * 연결 하나를 전용 스레드 하나에서만 쓴다. 메인 스레드를 막지 않으면서, 스레드가 하나뿐이니 거래가 저절로 한 줄로 선다.
 * 잔액 조회는 그 스레드를 거치지 않고 캐시에서 바로 읽는다. 캐시는 DB 커밋이 성공한 뒤에만 갱신되므로 DB보다 앞서지 않는다.
 */
class SqliteLedger(file: Path, private val clock: Clock) : Ledger, AutoCloseable {
    private val writerThread = Executors.newSingleThreadExecutor { Thread(it, "econet-ledger-writer") }
    private val writer = writerThread.asCoroutineDispatcher()
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:$file")
    private val balanceCache = ConcurrentHashMap<AccountId, Balance>()

    @Volatile
    var isLocked: Boolean = false
        private set

    init {
        connection.createStatement().use { statement ->
            // WAL은 쓰기 중에도 다른 연결(백업, 관리 도구)이 읽을 수 있게 하고, FULL은 커밋된 거래가 정전에도 남게 한다.
            statement.execute("PRAGMA journal_mode = WAL")
            statement.execute("PRAGMA synchronous = FULL")
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS accounts (
                    id      TEXT PRIMARY KEY,
                    balance INTEGER NOT NULL
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS transactions (
                    id           INTEGER PRIMARY KEY AUTOINCREMENT,
                    from_account TEXT    NOT NULL,
                    to_account   TEXT    NOT NULL,
                    amount       INTEGER NOT NULL CHECK (amount > 0),
                    kind         TEXT    NOT NULL,
                    actor        TEXT    NOT NULL,
                    memo         TEXT,
                    at           INTEGER NOT NULL
                )
                """.trimIndent()
            )
            statement.execute("CREATE INDEX IF NOT EXISTS transactions_by_from ON transactions (from_account, id)")
            statement.execute("CREATE INDEX IF NOT EXISTS transactions_by_to ON transactions (to_account, id)")
        }
        balanceCache.putAll(readStoredBalances())
    }

    override suspend fun transfer(
        from: AccountId,
        to: AccountId,
        amount: Amount,
        kind: TxKind,
        actor: Actor,
        memo: String?,
    ): TransferResult = withContext(writer) { commitTransfer(from, to, amount, kind, actor, memo) }

    private fun commitTransfer(
        from: AccountId,
        to: AccountId,
        amount: Amount,
        kind: TxKind,
        actor: Actor,
        memo: String?,
    ): TransferResult {
        if (isLocked) return TransferResult.Failure.Locked

        val decision = decideTransfer(from, to, amount, kind) { balanceCache[it] ?: Balance.ZERO }
        val (newFromBalance, newToBalance) = when (decision) {
            is TransferDecision.Rejected -> return decision.failure
            is TransferDecision.Approved -> decision
        }
        // DB에는 밀리초까지만 남기므로, 돌려주는 거래도 저장된 값과 같도록 미리 자른다.
        val at = clock.instant().truncatedTo(ChronoUnit.MILLIS)

        val id = inTransaction {
            val id = connection.prepareStatement(
                "INSERT INTO transactions (from_account, to_account, amount, kind, actor, memo, at) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id",
            ).use { insert ->
                insert.setString(1, from.toKey())
                insert.setString(2, to.toKey())
                insert.setLong(3, amount.value)
                insert.setString(4, kind.name)
                insert.setString(5, actor.toKey())
                insert.setString(6, memo)
                insert.setLong(7, at.toEpochMilli())
                insert.executeQuery().use { rows ->
                    rows.next()
                    rows.getLong(1)
                }
            }
            writeBalance(from, newFromBalance)
            writeBalance(to, newToBalance)
            id
        }

        balanceCache[from] = newFromBalance
        balanceCache[to] = newToBalance
        return TransferResult.Success(Transaction(TransactionId(id), from, to, amount, kind, actor, memo, at))
    }

    override suspend fun balanceOf(id: AccountId): Balance = balanceCache[id] ?: Balance.ZERO

    override suspend fun history(id: AccountId, limit: Int): List<Transaction> {
        require(limit >= 0) { "limit must not be negative: $limit" }
        return withContext(writer) {
            connection.prepareStatement(
                "SELECT * FROM transactions WHERE from_account = ? OR to_account = ? ORDER BY id DESC LIMIT ?",
            ).use { query ->
                val key = id.toKey()
                query.setString(1, key)
                query.setString(2, key)
                query.setInt(3, limit)
                query.executeQuery().use { rows -> rows.readTransactions().toList() }
            }
        }
    }

    override suspend fun allBalances(): Map<AccountId, Balance> = balanceCache.toMap()

    override suspend fun allTransactions(): List<Transaction> = withContext(writer) {
        readAllTransactions { it.toList() }
    }

    /**
     * 쓰기 스레드 위에서 돌기 때문에 검사하는 동안 거래가 끼어들 수 없어, 잔액과 장부가 같은 시점을 가리킨다.
     * 캐시가 아니라 DB의 잔액 테이블과 비교한다. 디스크에 남은 것이 다음 시작 때 읽힐 값이기 때문이다.
     */
    suspend fun checkIntegrity(): IntegrityReport = withContext(writer) {
        val report = readIntegrityReport()
        if (!report.isIntact) isLocked = true
        report
    }

    /** 장부가 진실이므로 잔액 테이블을 장부 재생 결과로 통째로 덮어쓰고 잠금을 푼다. */
    suspend fun repair(): IntegrityReport = withContext(writer) {
        val replayed = inTransaction {
            val replayed = readAllTransactions { replayBalances(it) }
            connection.createStatement().use { it.executeUpdate("DELETE FROM accounts") }
            for ((account, balance) in replayed) writeBalance(account, balance)
            replayed
        }
        balanceCache.clear()
        balanceCache.putAll(replayed)

        val report = readIntegrityReport()
        isLocked = !report.isIntact
        report
    }

    /**
     * 장부 전체를 [target] 파일로 복사한다. 쓰기 스레드 위에서 돌기 때문에 거래 도중의 반쪽 상태가 담기지 않는다.
     * 파일을 그냥 복사하면 WAL에 남은 최근 거래가 빠질 수 있어서 SQLite의 VACUUM INTO를 쓴다.
     */
    suspend fun backupTo(target: Path) = withContext(writer) {
        require(!target.toFile().exists()) { "backup target already exists: $target" }
        connection.prepareStatement("VACUUM INTO ?").use { statement ->
            statement.setString(1, target.toString())
            statement.execute()
        }
    }

    override fun close() {
        writerThread.submit { connection.close() }.get()
        writerThread.shutdown()
    }

    /**
     * 장부 기록과 두 잔액 변경이 함께 커밋되거나 함께 사라져야 하므로 묶는다.
     * 평소에는 autoCommit으로 둔다. 그래야 읽기만 한 뒤 열린 트랜잭션이 옛 스냅샷을 붙잡고 있지 않는다.
     */
    private fun <T> inTransaction(block: () -> T): T {
        connection.autoCommit = false
        try {
            val result = block()
            connection.commit()
            return result
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    /** 잔액과 장부를 한 트랜잭션에서 읽어야 같은 시점의 값끼리 비교된다. */
    private fun readIntegrityReport(): IntegrityReport =
        inTransaction { readAllTransactions { integrityReportOf(readStoredBalances(), it) } }

    private fun writeBalance(account: AccountId, balance: Balance) {
        connection.prepareStatement(
            "INSERT INTO accounts (id, balance) VALUES (?, ?) ON CONFLICT (id) DO UPDATE SET balance = excluded.balance",
        ).use { upsert ->
            upsert.setString(1, account.toKey())
            upsert.setLong(2, balance.value)
            upsert.executeUpdate()
        }
    }

    private fun readStoredBalances(): Map<AccountId, Balance> =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id, balance FROM accounts").use { rows ->
                buildMap {
                    while (rows.next()) put(accountIdOf(rows.getString("id")), Balance(rows.getLong("balance")))
                }
            }
        }

    /** 장부 전체를 메모리에 올리지 않고 한 줄씩 흘려보낸다. */
    private fun <T> readAllTransactions(consume: (Sequence<Transaction>) -> T): T =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM transactions ORDER BY id").use { rows -> consume(rows.readTransactions()) }
        }

    private fun ResultSet.readTransactions(): Sequence<Transaction> = generateSequence {
        if (!next()) return@generateSequence null
        Transaction(
            id = TransactionId(getLong("id")),
            from = accountIdOf(getString("from_account")),
            to = accountIdOf(getString("to_account")),
            amount = Amount(getLong("amount")),
            kind = TxKind.valueOf(getString("kind")),
            actor = actorOf(getString("actor")),
            memo = getString("memo"),
            at = Instant.ofEpochMilli(getLong("at")),
        )
    }
}
