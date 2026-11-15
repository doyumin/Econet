package dev.doyu.econet.paper.storage

import dev.doyu.econet.core.AccountId
import dev.doyu.econet.core.AccountId.Government
import dev.doyu.econet.core.AccountId.Issuance
import dev.doyu.econet.core.Actor
import dev.doyu.econet.core.Amount
import dev.doyu.econet.core.Balance
import dev.doyu.econet.core.BalanceMismatch
import dev.doyu.econet.core.TransactionId
import dev.doyu.econet.core.TransferResult
import dev.doyu.econet.core.TransferResult.Failure
import dev.doyu.econet.core.TxKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.sql.SQLException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.io.path.deleteRecursively
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SqliteLedgerTest {
    private val directory: Path = Files.createTempDirectory("econet-ledger")
    private val file: Path = directory.resolve("econet.db")
    private val clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00.123456Z"), ZoneOffset.UTC)
    private val admin = Actor.Admin(UUID.randomUUID())
    private val alice = AccountId.Player(UUID.randomUUID())
    private val bob = AccountId.Player(UUID.randomUUID())
    private val opened = mutableListOf<SqliteLedger>()

    private fun open(): SqliteLedger = SqliteLedger(file, clock).also { opened += it }

    private fun execute(sql: String) {
        DriverManager.getConnection("jdbc:sqlite:$file").use { it.createStatement().use { statement -> statement.execute(sql) } }
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun cleanUp() {
        opened.forEach { it.close() }
        directory.deleteRecursively()
    }

    private suspend fun SqliteLedger.fund(player: AccountId.Player, amount: Long) {
        assertIs<TransferResult.Success>(transfer(Issuance, Government, Amount(amount), TxKind.Mint, admin))
        assertIs<TransferResult.Success>(transfer(Government, player, Amount(amount), TxKind.Reward, admin))
    }

    @Test
    fun `transactions and balances survive a restart`() = runBlocking<Unit> {
        val ledger = open()
        ledger.fund(alice, 100)
        val sent = assertIs<TransferResult.Success>(ledger.transfer(alice, bob, Amount(30), TxKind.Transfer, Actor.Player(alice.uuid), "rent"))
        ledger.close()
        opened.clear()

        val reopened = open()

        assertEquals(Balance(70), reopened.balanceOf(alice))
        assertEquals(Balance(30), reopened.balanceOf(bob))
        assertEquals(Balance(-100), reopened.balanceOf(Issuance))
        assertEquals(listOf(sent.transaction), reopened.history(bob, limit = 10))
        assertTrue(reopened.checkIntegrity().isIntact)
    }

    @Test
    fun `transaction ids are sequential and history is newest first`() = runBlocking<Unit> {
        val ledger = open()
        ledger.fund(alice, 10)
        repeat(3) { ledger.transfer(alice, bob, Amount(1), TxKind.Transfer, admin) }

        val history = ledger.history(alice, limit = 2)

        assertEquals(listOf(TransactionId(5), TransactionId(4)), history.map { it.id })
        assertEquals((1L..5L).map(::TransactionId), ledger.allTransactions().map { it.id })
    }

    @Test
    fun `stored time is truncated to millis and matches the returned transaction`() = runBlocking<Unit> {
        val ledger = open()

        val tx = assertIs<TransferResult.Success>(ledger.transfer(Issuance, Government, Amount(1), TxKind.Mint, admin)).transaction

        assertEquals(Instant.parse("2026-01-01T00:00:00.123Z"), tx.at)
        assertEquals(listOf(tx), ledger.allTransactions())
    }

    @Test
    fun `rejected transfers write nothing`() = runBlocking<Unit> {
        val ledger = open()
        ledger.fund(alice, 10)

        assertEquals(Failure.InsufficientFunds(Balance(10), Amount(11)), ledger.transfer(alice, bob, Amount(11), TxKind.Transfer, admin))
        assertEquals(Failure.SameAccount, ledger.transfer(alice, alice, Amount(1), TxKind.Transfer, admin))
        assertEquals(Failure.InvalidRoute, ledger.transfer(Issuance, alice, Amount(1), TxKind.Mint, admin))
        assertEquals(2, ledger.allTransactions().size)
    }

    @Test
    fun `a failed write leaves both the database and the cache untouched`() = runBlocking<Unit> {
        val ledger = open()
        ledger.fund(alice, 10)
        execute("CREATE TRIGGER fail_inserts BEFORE INSERT ON transactions BEGIN SELECT RAISE(ABORT, 'disk on fire'); END")

        assertFailsWith<SQLException> { ledger.transfer(alice, bob, Amount(5), TxKind.Transfer, admin) }

        assertEquals(Balance(10), ledger.balanceOf(alice))
        assertEquals(Balance.ZERO, ledger.balanceOf(bob))
        assertTrue(ledger.checkIntegrity().isIntact)
    }

    @Test
    fun `tampered balances lock transfers until repaired`() = runBlocking<Unit> {
        val ledger = open()
        ledger.fund(alice, 100)
        execute("UPDATE accounts SET balance = 1000 WHERE id = '${alice.toKey()}'")

        val report = ledger.checkIntegrity()

        assertFalse(report.isIntact)
        assertEquals(listOf(BalanceMismatch(alice, stored = Balance(1000), replayed = Balance(100))), report.mismatches)
        assertTrue(ledger.isLocked)
        assertEquals(Failure.Locked, ledger.transfer(alice, bob, Amount(1), TxKind.Transfer, admin))
        assertEquals(Balance(100), ledger.balanceOf(alice))

        val repaired = ledger.repair()

        assertTrue(repaired.isIntact)
        assertFalse(ledger.isLocked)
        assertIs<TransferResult.Success>(ledger.transfer(alice, bob, Amount(1), TxKind.Transfer, admin))
    }

    @Test
    fun `a ledger that cannot be read locks transfers instead of passing the check`() = runBlocking<Unit> {
        val ledger = open()
        ledger.fund(alice, 100)
        execute("INSERT INTO transactions (from_account, to_account, amount, kind, actor, at) VALUES ('government', 'government', 1, 'Bogus', 'system:test', 0)")

        assertFailsWith<IllegalArgumentException> { ledger.checkIntegrity() }

        assertTrue(ledger.isLocked)
        assertEquals(Failure.Locked, ledger.transfer(alice, bob, Amount(1), TxKind.Transfer, admin))
        assertEquals(Balance(100), ledger.balanceOf(alice))
    }

    @Test
    fun `repair restores accounts that were deleted from the balance table`() = runBlocking<Unit> {
        val ledger = open()
        ledger.fund(alice, 100)
        execute("DELETE FROM accounts")

        assertFalse(ledger.checkIntegrity().isIntact)
        ledger.repair()

        assertEquals(Balance(100), ledger.balanceOf(alice))
        assertTrue(ledger.checkIntegrity().isIntact)
    }

    @Test
    fun `a backup is a complete ledger that passes the integrity check`() = runBlocking<Unit> {
        val ledger = open()
        ledger.fund(alice, 100)
        val backupFile = directory.resolve("backup.db")

        ledger.backupTo(backupFile)
        ledger.transfer(alice, bob, Amount(1), TxKind.Transfer, admin)

        val backup = SqliteLedger(backupFile, clock).also { opened += it }
        assertEquals(Balance(100), backup.balanceOf(alice))
        assertEquals(2, backup.allTransactions().size)
        assertTrue(backup.checkIntegrity().isIntact)
    }

    @Test
    fun `concurrent transfers preserve the total`() = runBlocking<Unit> {
        val ledger = open()
        val players = List(10) { AccountId.Player(UUID.randomUUID()) }
        players.forEach { ledger.fund(it, 1_000) }

        withContext(Dispatchers.Default) {
            repeat(200) { worker ->
                launch {
                    val random = Random(worker)
                    repeat(10) {
                        ledger.transfer(players.random(random), players.random(random), Amount(random.nextLong(1, 100)), TxKind.Transfer, admin)
                    }
                }
            }
        }

        assertTrue(ledger.checkIntegrity().isIntact)
        assertEquals(10_000L, players.sumOf { ledger.balanceOf(it).value })
    }
}
