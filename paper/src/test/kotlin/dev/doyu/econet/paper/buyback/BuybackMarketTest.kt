package dev.doyu.econet.paper.buyback

import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuybackMarketTest {
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    // 0.01씩 깎이면 80개에서 바닥(20%)에 닿는다.
    private val pricing = DynamicPricing(BigDecimal("0.2"), BigDecimal("0.01"), Duration.ofHours(24))
    private val prices = mapOf("minecraft:diamond" to BigDecimal("10"), "minecraft:wheat" to BigDecimal("0.1"))

    private fun market() = BuybackMarket(prices, pricing)

    private fun assertSame(expected: String, actual: BigDecimal) =
        assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected but was $actual")

    @Test
    fun `fresh items sell at the base price`() {
        val quote = market().quote("minecraft:diamond", 1, now)

        assertSame("10", quote.currentUnitPrice)
        assertSame("10", quote.total)
    }

    @Test
    fun `price falls within a single sale`() {
        val quote = market().quote("minecraft:diamond", 3, now)

        // 10 + 9.9 + 9.8
        assertSame("29.7", quote.total)
    }

    @Test
    fun `price never goes below the floor`() {
        val market = market()
        market.recordSale("minecraft:diamond", 1_000, now)

        assertSame("2", market.quote("minecraft:diamond", 1, now).currentUnitPrice)
        assertSame("20", market.quote("minecraft:diamond", 10, now).total)
    }

    @Test
    fun `price recovers linearly and fully within the recovery time`() {
        val market = market()
        market.recordSale("minecraft:diamond", 80, now)

        assertSame("2", market.quote("minecraft:diamond", 1, now).currentUnitPrice)
        assertSame("6", market.quote("minecraft:diamond", 1, now.plus(Duration.ofHours(12))).currentUnitPrice)
        assertSame("10", market.quote("minecraft:diamond", 1, now.plus(Duration.ofHours(24))).currentUnitPrice)
        assertSame("10", market.quote("minecraft:diamond", 1, now.plus(Duration.ofDays(3))).currentUnitPrice)
    }

    @Test
    fun `selling far past the floor still recovers within one day`() {
        val market = market()
        market.recordSale("minecraft:diamond", 100_000, now)

        assertSame("10", market.quote("minecraft:diamond", 1, now.plus(Duration.ofHours(24))).currentUnitPrice)
    }

    @Test
    fun `items are priced independently`() {
        val market = market()
        market.recordSale("minecraft:diamond", 80, now)

        assertSame("0.1", market.quote("minecraft:wheat", 1, now).currentUnitPrice)
    }

    @Test
    fun `a clock going backwards does not raise pressure`() {
        val market = market()
        market.recordSale("minecraft:diamond", 10, now)

        assertSame("9", market.quote("minecraft:diamond", 1, now.minus(Duration.ofHours(1))).currentUnitPrice)
    }

    @Test
    fun `payout floors only the final sum so tiny prices still add up`() {
        val market = market()
        val wheat = market.quote("minecraft:wheat", 10, now) // 0.1 + 0.099 + ... = 0.955
        val diamonds = market.quote("minecraft:diamond", 1, now)

        assertEquals(0, payoutOf(listOf(wheat)))
        assertEquals(10, payoutOf(listOf(wheat, diamonds)))
    }

    @Test
    fun `unknown items are not bought and pressure for removed items is dropped`() {
        val market = BuybackMarket(prices, pricing, mapOf("minecraft:emerald" to SalesPressure(BigDecimal.TEN, now)))

        assertFalse(market.buys("minecraft:emerald"))
        assertTrue(market.snapshot().isEmpty())
    }
}
