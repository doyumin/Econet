package dev.doyu.econet.paper.buyback

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant

data class DynamicPricing(
    /** 기준가 대비 바닥 가격 비율. */
    val floorRatio: BigDecimal,
    /** 한 개 팔릴 때마다 기준가에서 깎이는 비율. */
    val dropPerItem: BigDecimal,
    /** 바닥 가격에서 기준가로 완전히 돌아오는 데 걸리는 시간. */
    val recoveryTime: Duration,
) {
    init {
        require(floorRatio > BigDecimal.ZERO && floorRatio < BigDecimal.ONE) { "floor ratio must be between 0 and 1" }
        require(dropPerItem > BigDecimal.ZERO) { "drop per item must be positive" }
        require(!recoveryTime.isNegative && !recoveryTime.isZero) { "recovery time must be positive" }
    }

    /**
     * 바닥 가격에 도달하는 판매량. 이보다 많이 쌓아 두지 않는다.
     * 쌓아 두면 바닥에서 회복하는 데 하루보다 오래 걸려 "하루에 걸쳐 회복" 규칙이 깨지기 때문이다.
     */
    val pressureAtFloor: BigDecimal = (BigDecimal.ONE - floorRatio).divide(dropPerItem, MATH)
}

/** 품목별 누적 판매 압력. 판매가 없는 동안 시간에 비례해 줄어든다. */
data class SalesPressure(val units: BigDecimal, val at: Instant)

data class ItemQuote(
    val itemKey: String,
    val count: Int,
    /** 지금 한 개를 팔면 받는 값. 화면 표시용. */
    val currentUnitPrice: BigDecimal,
    /** 이번에 count개를 팔 때의 합. 팔리는 동안 가격이 계속 내려가는 것까지 반영한다. */
    val total: BigDecimal,
)

/**
 * 정부 매입 시세. 서버 전체 판매량이 쌓일수록 품목별 가격이 직선으로 내려가 바닥 가격에서 멈추고,
 * 판매가 없으면 바닥에서 기준가까지 recoveryTime에 걸쳐 직선으로 회복한다.
 *
 * 직선 모델을 쓰는 이유: 숫자가 미정인 단계라 운영자가 "몇 개 팔면 바닥, 몇 시간이면 회복"을 바로 계산할 수 있어야 조정이 쉽다.
 * 내부 계산은 BigDecimal로 한다. 0.1듄 같은 기준가를 double로 더하면 내림 결과가 1듄씩 어긋날 수 있다.
 *
 * 메인 스레드에서만 쓴다.
 */
class BuybackMarket(
    private val basePrices: Map<String, BigDecimal>,
    private val pricing: DynamicPricing,
    initialPressure: Map<String, SalesPressure> = emptyMap(),
) {
    private val pressure = initialPressure.filterKeys { it in basePrices }.toMutableMap()

    fun buys(itemKey: String): Boolean = itemKey in basePrices

    fun quote(itemKey: String, count: Int, now: Instant): ItemQuote {
        require(count > 0) { "count must be positive" }
        val base = requireNotNull(basePrices[itemKey]) { "$itemKey is not bought" }
        val start = pressureAt(itemKey, now)
        var total = BigDecimal.ZERO
        for (sold in 0 until count) total += unitPrice(base, start + BigDecimal.valueOf(sold.toLong()))
        return ItemQuote(itemKey, count, unitPrice(base, start), total)
    }

    fun recordSale(itemKey: String, count: Int, now: Instant) {
        require(buys(itemKey)) { "$itemKey is not bought" }
        val units = (pressureAt(itemKey, now) + BigDecimal.valueOf(count.toLong())).min(pricing.pressureAtFloor)
        pressure[itemKey] = SalesPressure(units, now)
    }

    fun snapshot(): Map<String, SalesPressure> = pressure.toMap()

    private fun pressureAt(itemKey: String, now: Instant): BigDecimal {
        val last = pressure[itemKey] ?: return BigDecimal.ZERO
        // 시계가 뒤로 가도(서버 시간 조정) 압력이 늘어나지 않게 경과 시간은 0 아래로 내려가지 않는다.
        val elapsedMillis = Duration.between(last.at, now).toMillis().coerceAtLeast(0)
        // 나누기를 마지막에 해야 "12시간이면 절반"처럼 딱 떨어지는 값이 반복 소수 오차 없이 나온다.
        val recovered = (pricing.pressureAtFloor * BigDecimal.valueOf(elapsedMillis))
            .divide(BigDecimal.valueOf(pricing.recoveryTime.toMillis()), MATH)
        return (last.units - recovered).max(BigDecimal.ZERO)
    }

    private fun unitPrice(base: BigDecimal, units: BigDecimal): BigDecimal {
        val ratio = (BigDecimal.ONE - pricing.dropPerItem * units).max(pricing.floorRatio)
        // 남은 오차가 내림에서 1듄을 갉아먹지 않도록 단가를 적당한 자릿수에서 끊는다.
        return (base * ratio).setScale(UNIT_PRICE_SCALE, RoundingMode.HALF_EVEN)
    }
}

/** 한 번의 판매에서 최종 지급액만 내림한다. 끝전은 지급되지 않으므로 정부 금고에 남는다. */
fun payoutOf(quotes: Collection<ItemQuote>): Long =
    quotes.fold(BigDecimal.ZERO) { sum, quote -> sum + quote.total }.setScale(0, RoundingMode.FLOOR).longValueExact()

private val MATH = MathContext.DECIMAL128
private const val UNIT_PRICE_SCALE = 4
