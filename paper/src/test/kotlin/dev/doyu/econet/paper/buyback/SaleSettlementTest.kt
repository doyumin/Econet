package dev.doyu.econet.paper.buyback

import dev.doyu.econet.core.AccountId
import dev.doyu.econet.core.Actor
import dev.doyu.econet.core.Amount
import dev.doyu.econet.core.Balance
import dev.doyu.econet.core.InMemoryLedger
import dev.doyu.econet.core.TransferResult
import dev.doyu.econet.core.TxKind
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SaleSettlementTest {
    /** 테스트용 아이템. key가 null이면 이름 붙인 아이템처럼 매입 대상이 아닌 특수 아이템이다. */
    private data class Stack(val key: String?, val count: Int, val id: Int = nextId++)

    private val now = Instant.parse("2026-01-01T00:00:00Z")
    private val pricing = DynamicPricing(BigDecimal("0.2"), BigDecimal("0.002"), Duration.ofHours(24))
    private val market = BuybackMarket(
        mapOf("minecraft:diamond" to BigDecimal("10"), "minecraft:wheat" to BigDecimal("0.1")),
        pricing,
    )
    private val settlement = SaleSettlement<Stack>(market, { it.key }, { it.count })
    private val ledger = InMemoryLedger(Clock.fixed(now, ZoneOffset.UTC))
    private val seller = AccountId.Player(UUID.randomUUID())
    private val admin = Actor.Admin(UUID.randomUUID())

    private suspend fun payFromGovernment(amount: Amount, memo: String): TransferResult =
        ledger.transfer(AccountId.Government, seller, amount, TxKind.Sale, Actor.Player(seller.uuid), memo)

    private suspend fun fundGovernment(amount: Long) {
        ledger.transfer(AccountId.Issuance, AccountId.Government, Amount(amount), TxKind.Mint, admin)
    }

    private fun assertNothingLost(items: List<Stack>, outcome: SaleOutcome<Stack>) {
        val sold = when (outcome) {
            is SaleOutcome.Sold -> items.filter { it.key == "minecraft:diamond" || it.key == "minecraft:wheat" }
            is SaleOutcome.NothingToSell, is SaleOutcome.Refused, is SaleOutcome.Failed -> emptyList()
        }
        assertEquals(items.toSet(), (sold + outcome.returned).toSet())
        assertEquals(items.size, sold.size + outcome.returned.size, "an item was duplicated or lost")
    }

    @Test
    fun `successful sale pays the government price and returns rejected items`() = runBlocking<Unit> {
        fundGovernment(1_000)
        val items = listOf(Stack("minecraft:diamond", 2), Stack("minecraft:emerald", 5), Stack(null, 1))

        val outcome = settlement.settle(items, now, ::payFromGovernment)

        val sold = assertIs<SaleOutcome.Sold<Stack>>(outcome)
        assertEquals(Amount(19), sold.payout) // 10 + 9.98 = 19.98 → 19
        assertEquals(listOf(items[1], items[2]), sold.returned)
        assertEquals(Balance(19), ledger.balanceOf(seller))
        assertEquals("buyback minecraft:diamond x2", sold.transaction.memo)
        assertNothingLost(items, outcome)
    }

    @Test
    fun `stacks of the same item are priced as one batch`() {
        val offer = settlement.offer(listOf(Stack("minecraft:diamond", 1), Stack("minecraft:diamond", 1)), now)

        assertEquals(1, offer.quotes.size)
        assertEquals(2, offer.quotes.single().count)
    }

    @Test
    fun `an empty treasury refuses the sale and returns everything`() = runBlocking<Unit> {
        fundGovernment(5)
        val items = listOf(Stack("minecraft:diamond", 3), Stack("minecraft:emerald", 1))

        val outcome = settlement.settle(items, now, ::payFromGovernment)

        val refused = assertIs<SaleOutcome.Refused<Stack>>(outcome)
        assertIs<TransferResult.Failure.InsufficientFunds>(refused.failure)
        assertEquals(items, refused.returned)
        assertEquals(Balance.ZERO, ledger.balanceOf(seller))
    }

    @Test
    fun `a refused sale does not move the market price`() = runBlocking<Unit> {
        settlement.settle(listOf(Stack("minecraft:diamond", 64)), now, ::payFromGovernment)

        assertEquals(0, BigDecimal("10").compareTo(market.quote("minecraft:diamond", 1, now).currentUnitPrice))
    }

    @Test
    fun `payment exceptions return everything`() = runBlocking<Unit> {
        val items = listOf(Stack("minecraft:diamond", 1))

        val outcome = settlement.settle(items, now) { _, _ -> error("database is gone") }

        assertEquals(items, assertIs<SaleOutcome.Failed<Stack>>(outcome).returned)
    }

    @Test
    fun `sales worth less than one coin keep the items`() = runBlocking<Unit> {
        fundGovernment(1_000)
        val items = listOf(Stack("minecraft:wheat", 9))

        val outcome = settlement.settle(items, now, ::payFromGovernment)

        assertEquals(items, assertIs<SaleOutcome.NothingToSell<Stack>>(outcome).returned)
        assertEquals(Balance(1_000), ledger.balanceOf(AccountId.Government))
    }

    @Test
    fun `a locked ledger refuses the sale and returns everything`() = runBlocking<Unit> {
        val items = listOf(Stack("minecraft:diamond", 1))

        val outcome = settlement.settle(items, now) { _, _ -> TransferResult.Failure.Locked }

        assertEquals(items, assertIs<SaleOutcome.Refused<Stack>>(outcome).returned)
    }

    @Test
    fun `no random mix of items is ever lost or duplicated`() = runBlocking<Unit> {
        fundGovernment(500)
        val random = Random(7)
        val keys = listOf("minecraft:diamond", "minecraft:wheat", "minecraft:emerald", null)

        repeat(300) {
            val items = List(random.nextInt(0, 30)) { Stack(keys.random(random), random.nextInt(1, 65)) }
            assertNothingLost(items, settlement.settle(items, now, ::payFromGovernment))
        }
        assertTrue(ledger.balanceOf(AccountId.Government) >= Balance.ZERO)
    }

    private companion object {
        var nextId = 0
    }
}
