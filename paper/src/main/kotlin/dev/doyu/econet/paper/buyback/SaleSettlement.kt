package dev.doyu.econet.paper.buyback

import dev.doyu.econet.core.Amount
import dev.doyu.econet.core.Transaction
import dev.doyu.econet.core.TransferResult
import java.time.Instant

/** 화면에 보여 줄 견적과, 실제 정산에 쓸 아이템 분류. */
data class SaleOffer<T>(
    val sellable: List<T>,
    val rejected: List<T>,
    val quotes: List<ItemQuote>,
    val payout: Long,
)

/**
 * 정산 결과. 어떤 결과든 [returned]에 플레이어에게 돌려줄 아이템을 반드시 담는다.
 * "팔린 것 + 돌려줄 것 = 넣은 것"이 타입으로 강제되어야, 어느 갈래에서도 아이템이 증발하지 않는다.
 */
sealed interface SaleOutcome<T> {
    val returned: List<T>

    data class Sold<T>(val payout: Amount, val transaction: Transaction, override val returned: List<T>) : SaleOutcome<T>

    /** 매입하는 아이템이 없거나, 합이 1듄 미만이라 지급할 게 없다. */
    data class NothingToSell<T>(override val returned: List<T>) : SaleOutcome<T>

    data class Refused<T>(val failure: TransferResult.Failure, override val returned: List<T>) : SaleOutcome<T>

    data class Failed<T>(val error: Exception, override val returned: List<T>) : SaleOutcome<T>
}

/**
 * 아이템 타입을 제네릭으로 둔다. Bukkit 없이 "아이템이 사라지지 않는다"를 테스트하기 위해서다.
 * [itemKeyOf]가 null을 돌려주면 그 아이템은 매입하지 않는다(이름 붙인 아이템, 금화 같은 태그 아이템 등).
 */
class SaleSettlement<T>(
    private val market: BuybackMarket,
    private val itemKeyOf: (T) -> String?,
    private val countOf: (T) -> Int,
) {
    fun offer(items: List<T>, now: Instant): SaleOffer<T> {
        val (sellable, rejected) = items.partition { item -> itemKeyOf(item)?.let(market::buys) == true }
        val counts = sellable.groupingBy { itemKeyOf(it)!! }.fold(0) { sum, item -> sum + countOf(item) }
        val quotes = counts.map { (key, count) -> market.quote(key, count, now) }
        return SaleOffer(sellable, rejected, quotes, payoutOf(quotes))
    }

    /**
     * 지급이 성공한 뒤에만 아이템을 판 것으로 친다. 지급이 거절되거나 예외가 나면 전부 돌려준다.
     * 시세는 지급이 성공했을 때만 반영한다.
     */
    suspend fun settle(items: List<T>, now: Instant, pay: suspend (Amount, String) -> TransferResult): SaleOutcome<T> {
        val offer = try {
            offer(items, now)
        } catch (e: Exception) {
            return SaleOutcome.Failed(e, items)
        }
        if (offer.payout < 1) return SaleOutcome.NothingToSell(items)

        val payout = Amount(offer.payout)
        val result = try {
            pay(payout, offer.quotes.joinToString(", ", prefix = "buyback ") { "${it.itemKey} x${it.count}" })
        } catch (e: Exception) {
            return SaleOutcome.Failed(e, items)
        }
        return when (result) {
            is TransferResult.Success -> {
                offer.quotes.forEach { market.recordSale(it.itemKey, it.count, now) }
                SaleOutcome.Sold(payout, result.transaction, offer.rejected)
            }
            is TransferResult.Failure -> SaleOutcome.Refused(result, items)
        }
    }
}
