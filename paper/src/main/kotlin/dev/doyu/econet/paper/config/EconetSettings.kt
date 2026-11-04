package dev.doyu.econet.paper.config

import org.bukkit.configuration.ConfigurationSection
import java.math.BigDecimal
import java.time.Duration

data class EconetSettings(
    val currency: CurrencySettings,
    val integrityCheckInterval: Duration,
    val buybackPrices: Map<String, BigDecimal>,
)

data class CurrencySettings(
    val name: String,
    val code: String,
    val format: String,
)

/**
 * 잘못된 설정으로 서버가 조용히 엉뚱한 값을 쓰는 것보다 시작 시 실패하는 편이 낫다.
 * 그래서 기본값으로 덮지 않고, 빠졌거나 틀린 값은 어떤 키인지 밝혀 예외를 던진다.
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
        buybackPrices = buybackPrices,
    )
}

private fun ConfigurationSection.requireString(path: String): String =
    requireNotNull(getString(path)?.takeIf { it.isNotBlank() }) { "$path is missing" }

private fun ConfigurationSection.requirePositiveLong(path: String): Long {
    require(isLong(path) || isInt(path)) { "$path must be a whole number" }
    val value = getLong(path)
    require(value > 0) { "$path must be positive" }
    return value
}
