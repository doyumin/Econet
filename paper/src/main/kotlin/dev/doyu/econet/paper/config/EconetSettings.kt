package dev.doyu.econet.paper.config

import dev.doyu.econet.paper.buyback.DynamicPricing
import org.bukkit.configuration.ConfigurationSection
import java.math.BigDecimal
import java.time.Duration

data class EconetSettings(
    val currency: CurrencySettings,
    val integrityCheckInterval: Duration,
    val buyback: BuybackSettings,
)

data class BuybackSettings(
    val prices: Map<String, BigDecimal>,
    val pricing: DynamicPricing,
)

data class CurrencySettings(
    val name: String,
    val code: String,
    val format: String,
)

/**
 * 잘못된 설정으로 서버가 조용히 엉뚱한 값을 쓰는 것보다 시작 시 실패하는 편이 낫다.
 * 그래서 틀린 값은 어떤 키인지 밝혀 예외를 던진다.
 * 빠진 키는 Bukkit이 jar에 든 config.yml 값으로 채우므로(getConfig의 defaults), 업데이트로 새 키가 생겨도 기존 설정 파일이 깨지지 않는다.
 */
fun readSettings(config: ConfigurationSection): EconetSettings {
    val prices = config.getConfigurationSection("buyback.prices")
    val buybackPrices = if (prices == null) emptyMap() else prices.getKeys(false).associateWith { key ->
        val price = prices.getString(key)?.toBigDecimalOrNull()
        require(price != null && price > BigDecimal.ZERO) { "buyback.prices.$key must be a positive number" }
        price
    }
    return EconetSettings(
        currency = CurrencySettings(
            name = config.requireString("currency.name"),
            code = config.requireString("currency.code"),
            format = config.requireString("currency.format"),
        ),
        integrityCheckInterval = Duration.ofHours(config.requirePositiveLong("integrity.check-interval-hours")),
        buyback = BuybackSettings(
            prices = buybackPrices,
            pricing = DynamicPricing(
                floorRatio = config.requireDecimal("buyback.dynamic.floor-ratio"),
                dropPerItem = config.requireDecimal("buyback.dynamic.drop-per-item"),
                recoveryTime = Duration.ofHours(config.requirePositiveLong("buyback.dynamic.recovery-hours")),
            ),
        ),
    )
}

private fun ConfigurationSection.requireString(path: String): String =
    requireNotNull(getString(path)?.takeIf { it.isNotBlank() }) { "$path is missing" }

private fun ConfigurationSection.requireDecimal(path: String): BigDecimal =
    requireNotNull(getString(path)?.toBigDecimalOrNull()) { "$path must be a number" }

private fun ConfigurationSection.requirePositiveLong(path: String): Long {
    require(isLong(path) || isInt(path)) { "$path must be a whole number" }
    val value = getLong(path)
    require(value > 0) { "$path must be positive" }
    return value
}
