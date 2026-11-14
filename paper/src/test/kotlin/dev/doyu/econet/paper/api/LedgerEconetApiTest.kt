package dev.doyu.econet.paper.api

import dev.doyu.econet.api.ChargeResult
import dev.doyu.econet.core.AccountId
import dev.doyu.econet.core.Actor
import dev.doyu.econet.core.Amount
import dev.doyu.econet.core.InMemoryLedger
import dev.doyu.econet.core.TransferResult
import dev.doyu.econet.core.TxKind
import dev.doyu.econet.core.verifyIntegrity
import kotlinx.coroutines.runBlocking
import java.time.Clock
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class LedgerEconetApiTest {
    private val ledger = InMemoryLedger(Clock.systemUTC())
    private val api = LedgerEconetApi(ledger)
    private val player = UUID.randomUUID()
    private val admin = Actor.Admin(UUID.randomUUID())

    private suspend fun fundGovernment(amount: Long) {
        assertIs<TransferResult.Success>(ledger.transfer(AccountId.Issuance, AccountId.Government, Amount(amount), TxKind.Mint, admin))
    }

    private suspend fun fundPlayer(amount: Long) {
        fundGovernment(amount)
        assertIs<TransferResult.Success>(ledger.transfer(AccountId.Government, AccountId.Player(player), Amount(amount), TxKind.Reward, admin))
    }

    @Test
    fun `charge moves money to the government and records plugin and reason`() = runBlocking<Unit> {
        fundPlayer(100)

        val result = api.charge("SMPUtil", player, 30, "teleport")

        assertIs<ChargeResult.Success>(result)
        assertEquals(70, api.balanceOf(player))
        assertEquals(30, ledger.balanceOf(AccountId.Government).value)
        val recorded = ledger.history(AccountId.Player(player), 1).single()
        assertEquals(result.transactionId, recorded.id.value)
        assertEquals(TxKind.Purchase, recorded.kind)
        assertEquals(Actor.System("SMPUtil"), recorded.actor)
        assertEquals("teleport", recorded.memo)
    }

    @Test
    fun `charge fails without changing anything when the player cannot afford it`() = runBlocking<Unit> {
        fundPlayer(10)

        assertEquals(ChargeResult.InsufficientFunds(balance = 10, required = 30), api.charge("SMPUtil", player, 30, "teleport"))
        assertEquals(10, api.balanceOf(player))
    }

    @Test
    fun `reward pays from the government and fails when the treasury is empty`() = runBlocking<Unit> {
        fundGovernment(50)

        assertIs<ChargeResult.Success>(api.reward("SMPUtil", player, 20, "quest"))
        assertEquals(20, api.balanceOf(player))
        assertEquals(ChargeResult.InsufficientFunds(balance = 30, required = 40), api.reward("SMPUtil", player, 40, "quest"))
        assertEquals(TxKind.Reward, ledger.history(AccountId.Player(player), 1).single().kind)
    }

    @Test
    fun `non-positive amounts are rejected as a value`() = runBlocking<Unit> {
        assertEquals(ChargeResult.InvalidAmount, api.charge("SMPUtil", player, 0, "x"))
        assertEquals(ChargeResult.InvalidAmount, api.reward("SMPUtil", player, -5, "x"))
    }

    @Test
    fun `a blank plugin name is a caller bug`() = runBlocking<Unit> {
        assertFailsWith<IllegalArgumentException> { api.charge(" ", player, 1, "x") }
    }

    @Test
    fun `the sum of all balances stays zero`() = runBlocking<Unit> {
        fundPlayer(100)
        api.charge("SMPUtil", player, 40, "a")
        api.reward("SMPUtil", player, 10, "b")
        api.charge("SMPUtil", player, 1_000, "too much")

        assertEquals(0, ledger.allBalances().values.sumOf { it.value })
        assertEquals(true, ledger.verifyIntegrity().isIntact)
    }
}
