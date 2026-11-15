package dev.doyu.econet.paper.casino

import dev.doyu.econet.paper.cash.Denomination
import dev.doyu.econet.paper.cash.breakDown

/** 판돈으로 꺼낼 동전과, 액면이 맞지 않아 더 꺼낸 만큼 돌려줄 잔돈. */
data class StakePlan(val take: Map<Denomination, Long>, val change: Map<Denomination, Long>)

/**
 * 작은 동전부터 모아 판돈을 낸다. 모은 값이 판돈을 넘으면 넘친 만큼은 잔돈으로 돌려준다.
 * 잔돈 교환은 현금 총액을 바꾸지 않으므로 장부와 무관하다. 가진 동전이 모자라면 null.
 * 작은 동전부터 쓰는 이유: 큰 동전을 쓸데없이 깨지 않기 위해서다.
 */
fun planStake(held: Map<Denomination, Long>, bet: Long): StakePlan? {
    require(bet > 0) { "bet must be positive: $bet" }
    var remaining = bet
    val take = linkedMapOf<Denomination, Long>()
    for (denomination in Denomination.entries) {
        if (remaining <= 0) break
        val needed = (remaining + denomination.value - 1) / denomination.value
        val count = minOf(held[denomination] ?: 0L, needed)
        if (count > 0) {
            take[denomination] = count
            remaining -= count * denomination.value
        }
    }
    if (remaining > 0) return null
    return StakePlan(take, breakDown(-remaining))
}
