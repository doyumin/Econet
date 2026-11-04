package dev.doyu.econet.core

import dev.doyu.econet.core.AccountId.CashPool
import dev.doyu.econet.core.AccountId.Government
import dev.doyu.econet.core.AccountId.Issuance
import dev.doyu.econet.core.TransferResult.Failure
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InMemoryLedgerTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val ledger = InMemoryLedger(Clock.fixed(now, ZoneOffset.UTC))
    private val admin = Actor.Admin(UUID.randomUUID())
    private val alice = AccountId.Player(UUID.randomUUID())
    private val bob = AccountId.Player(UUID.randomUUID())

    private suspend fun mintToGovernment(amount: Long) {
        assertIs<TransferResult.Success>(ledger.transfer(Issuance, Government, Amount(amount), TxKind.Mint, admin))
    }

    private suspend fun fund(player: AccountId.Player, amount: Long) {
        mintToGovernment(amount)
        assertIs<TransferResult.Success>(ledger.transfer(Government, player, Amount(amount), TxKind.Reward, admin))
    }

    @Test
    fun `amount must be positive`() {
        assertFailsWith<IllegalArgumentException> { Amount(0) }
        assertFailsWith<IllegalArgumentException> { Amount(-1) }
    }

    @Test
    fun `transfer moves money and records the transaction`() = runBlocking {
        fund(alice, 100)
        val actor = Actor.Player(alice.uuid)

        val result = ledger.transfer(alice, bob, Amount(30), TxKind.Transfer, actor, memo = "lunch")

        val tx = assertIs<TransferResult.Success>(result).transaction
        assertEquals(Balance(70), ledger.balanceOf(alice))
        assertEquals(Balance(30), ledger.balanceOf(bob))
        assertEquals(Transaction(tx.id, alice, bob, Amount(30), TxKind.Transfer, actor, "lunch", now), tx)
        assertEquals(listOf(tx), ledger.history(bob, limit = 10))
    }

    @Test
    fun `history is newest first and respects the limit`() = runBlocking {
        fund(alice, 100)
        repeat(3) { ledger.transfer(alice, bob, Amount(1), TxKind.Transfer, admin) }

        val history = ledger.history(alice, limit = 2)

        assertEquals(2, history.size)
        assertTrue(history[0].id > history[1].id)
    }

    @Test
    fun `insufficient funds leaves balances untouched`() = runBlocking {
        fund(alice, 10)

        val result = ledger.transfer(alice, bob, Amount(11), TxKind.Transfer, admin)

        assertEquals(Failure.InsufficientFunds(balance = Balance(10), required = Amount(11)), result)
        assertEquals(Balance(10), ledger.balanceOf(alice))
        assertEquals(Balance.ZERO, ledger.balanceOf(bob))
    }

    @Test
    fun `sending to the same account fails`() = runBlocking {
        fund(alice, 10)

        assertEquals(Failure.SameAccount, ledger.transfer(alice, alice, Amount(1), TxKind.Transfer, admin))
    }

    @Test
    fun `overflow on the receiving side fails without changing anything`() = runBlocking {
        mintToGovernment(Long.MAX_VALUE)
        val before = ledger.allBalances()
        val result = ledger.transfer(Issuance, Government, Amount(1), TxKind.Mint, admin)

        assertEquals(Failure.Overflow, result)
        assertEquals(before, ledger.allBalances())
    }

    @Test
    fun `routes through special accounts are enforced`() = runBlocking {
        fund(alice, 100)
        val invalid = listOf(
            Triple(Issuance, alice, TxKind.Mint),
            Triple(Government, alice, TxKind.Mint),
            Triple(Issuance, Government, TxKind.Transfer),
            Triple(alice, Issuance, TxKind.Burn),
            Triple(Government, Issuance, TxKind.Tax),
            Triple(Issuance, CashPool, TxKind.CashOut),
            Triple(alice, CashPool, TxKind.Transfer),
            Triple(alice, bob, TxKind.CashOut),
            Triple(CashPool, Issuance, TxKind.CashIn),
            Triple(alice, bob, TxKind.Mint),
        )

        for ((from, to, kind) in invalid) {
            assertEquals(Failure.InvalidRoute, ledger.transfer(from, to, Amount(1), kind, admin), "$kind $from -> $to")
        }
    }

    @Test
    fun `cash moves through the cash pool for players and government`() = runBlocking {
        fund(alice, 100)
        mintToGovernment(50)

        assertIs<TransferResult.Success>(ledger.transfer(alice, CashPool, Amount(40), TxKind.CashOut, admin))
        assertIs<TransferResult.Success>(ledger.transfer(Government, CashPool, Amount(50), TxKind.CashOut, admin))
        assertIs<TransferResult.Success>(ledger.transfer(CashPool, bob, Amount(90), TxKind.CashIn, admin))

        assertEquals(Balance(60), ledger.balanceOf(alice))
        assertEquals(Balance(90), ledger.balanceOf(bob))
        assertEquals(Balance.ZERO, ledger.balanceOf(CashPool))
    }

    @Test
    fun `mint drives issuance negative and keeps the total at zero`() = runBlocking {
        mintToGovernment(1_000)

        assertEquals(Balance(-1_000), ledger.balanceOf(Issuance))
        assertEquals(Balance(1_000), ledger.balanceOf(Government))
        assertTrue(ledger.verifyIntegrity().isIntact)
    }

    @Test
    fun `burn returns money to issuance`() = runBlocking {
        mintToGovernment(1_000)

        assertIs<TransferResult.Success>(ledger.transfer(Government, Issuance, Amount(400), TxKind.Burn, admin))

        assertEquals(Balance(-600), ledger.balanceOf(Issuance))
        assertEquals(Balance(600), ledger.balanceOf(Government))
    }

    @Test
    fun `government cannot burn more than it holds`() = runBlocking {
        mintToGovernment(10)

        val result = ledger.transfer(Government, Issuance, Amount(11), TxKind.Burn, admin)

        assertEquals(Failure.InsufficientFunds(Balance(10), Amount(11)), result)
    }
}
