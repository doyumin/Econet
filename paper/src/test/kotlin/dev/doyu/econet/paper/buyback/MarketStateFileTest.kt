package dev.doyu.econet.paper.buyback

import java.math.BigDecimal
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class MarketStateFileTest {
    @Test
    fun `pressure survives a round trip without losing decimal precision`() {
        val file = Files.createTempDirectory("econet-market").resolve("market.yml").toFile()
        val state = mapOf(
            "minecraft:diamond" to SalesPressure(BigDecimal("123.456789012345678901"), Instant.ofEpochMilli(1_700_000_000_123)),
            "minecraft:wheat" to SalesPressure(BigDecimal("0.1"), Instant.ofEpochMilli(1)),
        )

        MarketStateFile(file).save(state)

        assertEquals(state, MarketStateFile(file).load())
        file.parentFile.deleteRecursively()
    }

    @Test
    fun `a missing file means no pressure`() {
        assertEquals(emptyMap(), MarketStateFile(Files.createTempDirectory("econet-market").resolve("none.yml").toFile()).load())
    }
}
