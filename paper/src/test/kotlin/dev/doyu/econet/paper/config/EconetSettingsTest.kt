package dev.doyu.econet.paper.config

import dev.doyu.econet.core.Amount
import org.bukkit.configuration.file.YamlConfiguration
import java.math.BigDecimal
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EconetSettingsTest {
    private fun bundledConfig(): YamlConfiguration {
        val text = javaClass.getResource("/config.yml")!!.readText()
        return YamlConfiguration().apply { loadFromString(text) }
    }

    @Test
    fun `bundled config is valid and matches the design defaults`() {
        val settings = readSettings(bundledConfig())

        assertEquals(CurrencySettings("듄", "DYN", "{amount}{name}"), settings.currency)
        assertEquals(Duration.ofHours(24), settings.integrityCheckInterval)
        assertEquals(BigDecimal("0.1"), settings.buyback.prices["minecraft:wheat"])
        assertEquals(BigDecimal("30"), settings.buyback.prices["minecraft:ancient_debris"])
        assertEquals(19, settings.buyback.prices.size)
        assertEquals(0, BigDecimal("0.2").compareTo(settings.buyback.pricing.floorRatio))
        assertEquals(Duration.ofHours(24), settings.buyback.pricing.recoveryTime)
        assertEquals(Amount(100), settings.insurancePremium)
        assertEquals(Amount(100), settings.bountyRewards["minecraft:ender_dragon"])
        assertEquals(null, settings.bountyRewards["raid-captain"])
        assertEquals(Amount(50), settings.shopPrices["minecraft:name_tag"])
        assertEquals("minecraft:name_tag", settings.shopPrices.keys.first())
        assertEquals(listOf(Amount(1), Amount(10), Amount(50), Amount(100)), settings.coinFlip.bets)
        assertEquals(0.5, settings.coinFlip.winChance)
    }

    @Test
    fun `invalid coin flip settings fail loudly`() {
        val certainWin = bundledConfig().apply { set("casino.coin-flip.win-chance", 1.0) }
        val unsorted = bundledConfig().apply { set("casino.coin-flip.bets", listOf(10, 5)) }
        val tooMany = bundledConfig().apply { set("casino.coin-flip.bets", (1..10).toList()) }

        assertEquals("casino.coin-flip.win-chance must be between 0 and 1 exclusive", assertFailsWith<IllegalArgumentException> { readSettings(certainWin) }.message)
        assertEquals("casino.coin-flip.bets must be strictly increasing", assertFailsWith<IllegalArgumentException> { readSettings(unsorted) }.message)
        assertEquals("casino.coin-flip.bets must have 1 to 9 entries", assertFailsWith<IllegalArgumentException> { readSettings(tooMany) }.message)
    }

    @Test
    fun `excluded items are not in the price table`() {
        val prices = readSettings(bundledConfig()).buyback.prices

        for (excluded in listOf("minecraft:emerald", "minecraft:iron_ingot", "minecraft:gold_nugget", "minecraft:sugar_cane", "minecraft:iron_block")) {
            assertEquals(null, prices[excluded], excluded)
        }
    }

    @Test
    fun `missing or invalid values fail loudly with the key name`() {
        val noName = bundledConfig().apply { set("currency.name", null) }
        val zeroInterval = bundledConfig().apply { set("integrity.check-interval-hours", 0) }
        val negativePrice = bundledConfig().apply { set("buyback.prices.minecraft:coal", -1) }
        val fractionalShopPrice = bundledConfig().apply { set("shop.items.minecraft:saddle", 1.5) }

        assertEquals("currency.name is missing", assertFailsWith<IllegalArgumentException> { readSettings(noName) }.message)
        assertFailsWith<IllegalArgumentException> { readSettings(zeroInterval) }
        assertEquals("buyback.prices.minecraft:coal must be a positive number", assertFailsWith<IllegalArgumentException> { readSettings(negativePrice) }.message)
        assertEquals("shop.items.minecraft:saddle must be a whole number", assertFailsWith<IllegalArgumentException> { readSettings(fractionalShopPrice) }.message)
    }
}
