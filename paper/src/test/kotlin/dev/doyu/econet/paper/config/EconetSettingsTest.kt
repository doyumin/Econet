package dev.doyu.econet.paper.config

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
        assertEquals(BigDecimal("0.1"), settings.buybackPrices["minecraft:wheat"])
        assertEquals(BigDecimal("30"), settings.buybackPrices["minecraft:ancient_debris"])
        assertEquals(19, settings.buybackPrices.size)
    }

    @Test
    fun `excluded items are not in the price table`() {
        val prices = readSettings(bundledConfig()).buybackPrices

        for (excluded in listOf("minecraft:emerald", "minecraft:iron_ingot", "minecraft:gold_nugget", "minecraft:sugar_cane", "minecraft:iron_block")) {
            assertEquals(null, prices[excluded], excluded)
        }
    }

    @Test
    fun `missing or invalid values fail loudly with the key name`() {
        val noName = bundledConfig().apply { set("currency.name", null) }
        val zeroInterval = bundledConfig().apply { set("integrity.check-interval-hours", 0) }
        val negativePrice = bundledConfig().apply { set("buyback.prices.minecraft:coal", -1) }

        assertEquals("currency.name is missing", assertFailsWith<IllegalArgumentException> { readSettings(noName) }.message)
        assertFailsWith<IllegalArgumentException> { readSettings(zeroInterval) }
        assertEquals("buyback.prices.minecraft:coal must be a positive number", assertFailsWith<IllegalArgumentException> { readSettings(negativePrice) }.message)
    }
}
