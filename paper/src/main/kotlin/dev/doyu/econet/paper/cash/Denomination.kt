package dev.doyu.econet.paper.cash

/** 액면이 10배씩이라 큰 단위부터 채우는 방식이 언제나 개수가 가장 적다. */
enum class Denomination(val value: Long, val itemKey: String, val displayName: String) {
    Iron(1, "minecraft:iron_nugget", "철화"),
    Gold(10, "minecraft:gold_nugget", "금화"),
    Diamond(100, "minecraft:diamond", "다이아화");

    /** 한 단계 아래 액면. 쪼갤 때 쓴다. */
    val smaller: Denomination?
        get() = entries.getOrNull(ordinal - 1)

    companion object {
        val largestFirst: List<Denomination> = entries.sortedByDescending { it.value }
    }
}

/** 금액을 가장 적은 개수의 동전으로 나눈다. */
fun breakDown(amount: Long): Map<Denomination, Long> {
    require(amount >= 0) { "amount must not be negative: $amount" }
    var remaining = amount
    val coins = linkedMapOf<Denomination, Long>()
    for (denomination in Denomination.largestFirst) {
        val count = remaining / denomination.value
        if (count > 0) coins[denomination] = count
        remaining -= count * denomination.value
    }
    return coins
}

/** 동전 묶음의 총액. 넘치면 예외를 던진다. 넘친 값으로 입금하면 돈이 사라지기 때문이다. */
fun totalValue(coins: Map<Denomination, Long>): Long =
    coins.entries.fold(0L) { sum, (denomination, count) -> Math.addExact(sum, Math.multiplyExact(denomination.value, count)) }
