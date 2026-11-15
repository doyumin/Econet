package dev.doyu.econet.paper.casino

import dev.doyu.econet.paper.cash.Denomination.Diamond
import dev.doyu.econet.paper.cash.Denomination.Gold
import dev.doyu.econet.paper.cash.Denomination.Iron
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StakePlanTest {
    @Test
    fun `exact coins need no change`() {
        assertEquals(StakePlan(mapOf(Gold to 1L), emptyMap()), planStake(mapOf(Gold to 3L), 10))
    }

    @Test
    fun `small coins are spent first`() {
        assertEquals(StakePlan(mapOf(Iron to 5L, Gold to 4L), emptyMap()), planStake(mapOf(Iron to 5L, Gold to 4L, Diamond to 1L), 45))
    }

    @Test
    fun `a larger coin is broken and the overpay returned as change`() {
        assertEquals(StakePlan(mapOf(Diamond to 1L), mapOf(Gold to 9L)), planStake(mapOf(Diamond to 1L), 10))
    }

    @Test
    fun `mixed coins overpay by less than the next coin`() {
        // 철화 5개(5) + 금화 1개(10) = 15를 내고 5를 돌려받는다.
        assertEquals(StakePlan(mapOf(Iron to 5L, Gold to 1L), mapOf(Iron to 5L)), planStake(mapOf(Iron to 5L, Gold to 2L), 10))
    }

    @Test
    fun `not enough coins gives no plan`() {
        assertNull(planStake(mapOf(Iron to 9L), 10))
        assertNull(planStake(emptyMap(), 1))
    }

    @Test
    fun `taken value always equals bet plus change`() {
        val held = mapOf(Iron to 7L, Gold to 3L, Diamond to 2L)
        for (bet in 1L..200L) {
            val plan = planStake(held, bet) ?: continue
            val taken = plan.take.entries.sumOf { (d, n) -> d.value * n }
            val change = plan.change.entries.sumOf { (d, n) -> d.value * n }
            assertEquals(bet + change, taken, "bet $bet")
            plan.take.forEach { (d, n) -> check(n <= held.getValue(d)) { "took more $d than held for bet $bet" } }
        }
    }

    @Test
    fun `non positive bets are a caller bug`() {
        assertFailsWith<IllegalArgumentException> { planStake(mapOf(Iron to 1L), 0) }
    }
}
