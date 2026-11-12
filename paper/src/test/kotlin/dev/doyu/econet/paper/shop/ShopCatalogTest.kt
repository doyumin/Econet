package dev.doyu.econet.paper.shop

import dev.doyu.econet.core.Amount
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShopCatalogTest {
    private fun shopFile(): File = Files.createTempDirectory("econet-shop").resolve("shop.yml").toFile()

    @Test
    fun `a missing file is seeded from the config and the seed is saved`() {
        val file = shopFile()
        val seed = linkedMapOf("minecraft:name_tag" to Amount(50), "minecraft:saddle" to Amount(80))

        val first = ShopCatalog(file, seed)
        val reloaded = ShopCatalog(file, emptyMap())

        assertEquals(seed.toList(), first.prices().toList())
        assertEquals(seed.toList(), reloaded.prices().toList())
        file.parentFile.deleteRecursively()
    }

    @Test
    fun `once the file exists the config seed is ignored`() {
        val file = shopFile()
        ShopCatalog(file, mapOf("minecraft:saddle" to Amount(80))).remove("minecraft:saddle")

        val reloaded = ShopCatalog(file, mapOf("minecraft:saddle" to Amount(80)))

        assertTrue(reloaded.prices().isEmpty())
        file.parentFile.deleteRecursively()
    }

    @Test
    fun `added items and price changes survive a restart in display order`() {
        val file = shopFile()
        val catalog = ShopCatalog(file, mapOf("minecraft:name_tag" to Amount(50)))

        assertEquals(ShopPut.Added, catalog.put("minecraft:lead", Amount(20)))
        assertEquals(ShopPut.PriceChanged(Amount(50)), catalog.put("minecraft:name_tag", Amount(60)))
        val reloaded = ShopCatalog(file, emptyMap())

        assertEquals(listOf("minecraft:name_tag" to Amount(60), "minecraft:lead" to Amount(20)), reloaded.prices().toList())
        file.parentFile.deleteRecursively()
    }

    @Test
    fun `removing reports whether the item was there`() {
        val file = shopFile()
        val catalog = ShopCatalog(file, mapOf("minecraft:lead" to Amount(20)))

        assertTrue(catalog.remove("minecraft:lead"))
        assertFalse(catalog.remove("minecraft:lead"))
        assertTrue(ShopCatalog(file, emptyMap()).prices().isEmpty())
        file.parentFile.deleteRecursively()
    }

    @Test
    fun `a full shop refuses a new item but still lets a price change`() {
        val file = shopFile()
        val seed = (1..ShopCatalog.MAX_ITEMS).associate { "minecraft:item_$it" to Amount(1) }
        val catalog = ShopCatalog(file, seed)

        assertEquals(ShopPut.Full, catalog.put("minecraft:lead", Amount(20)))
        assertEquals(ShopPut.PriceChanged(Amount(1)), catalog.put("minecraft:item_1", Amount(9)))
        assertEquals(ShopCatalog.MAX_ITEMS, catalog.prices().size)
        file.parentFile.deleteRecursively()
    }

    @Test
    fun `a broken price in the file fails instead of being skipped`() {
        val file = shopFile()
        file.writeText("items:\n  minecraft:lead: 1.5\n")

        val error = assertFailsWith<IllegalArgumentException> { ShopCatalog(file, emptyMap()) }

        assertEquals("shop.yml: items.minecraft:lead must be a whole number", error.message)
        file.parentFile.deleteRecursively()
    }
}
