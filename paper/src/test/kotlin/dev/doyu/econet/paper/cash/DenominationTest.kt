package dev.doyu.econet.paper.cash

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DenominationTest {
    @Test
    fun `amounts break into the fewest coins`() {
        assertEquals(mapOf(Denomination.Diamond to 12L, Denomination.Gold to 3L, Denomination.Iron to 4L), breakDown(1_234))
        assertEquals(mapOf(Denomination.Gold to 1L), breakDown(10))
        assertEquals(emptyMap(), breakDown(0))
    }

    @Test
    fun `breaking down and adding up gives back the same amount`() {
        val random = Random(1)
        repeat(1_000) {
            val amount = random.nextLong(0, 1_000_000_000)
            assertEquals(amount, totalValue(breakDown(amount)))
        }
    }

    @Test
    fun `each denomination splits into ten of the next smaller one`() {
        assertEquals(Denomination.Gold, Denomination.Diamond.smaller)
        assertEquals(Denomination.Iron, Denomination.Gold.smaller)
        assertNull(Denomination.Iron.smaller)
        for (denomination in Denomination.entries) {
            val smaller = denomination.smaller ?: continue
            assertEquals(denomination.value, smaller.value * 10)
        }
    }

    @Test
    fun `overflowing totals are rejected instead of wrapping`() {
        assertFailsWith<ArithmeticException> { totalValue(mapOf(Denomination.Diamond to Long.MAX_VALUE)) }
    }
}
