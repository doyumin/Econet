package dev.doyu.econet.paper.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PlayerDataFileTest {
    private fun item(id: String, count: Int, slot: Int? = null): NbtTag.CompoundTag {
        val values = linkedMapOf<String, NbtTag>("id" to NbtTag.StringTag(id), "count" to NbtTag.IntTag(count))
        if (slot != null) values["Slot"] = NbtTag.ByteTag(slot.toByte())
        return NbtTag.CompoundTag(values)
    }

    private val playerFile = NbtTag.CompoundTag(
        mapOf(
            "DataVersion" to NbtTag.IntTag(4_700),
            "Health" to NbtTag.FloatTag(20f),
            "Pos" to NbtTag.ListTag(6, listOf(NbtTag.DoubleTag(1.0), NbtTag.DoubleTag(64.0), NbtTag.DoubleTag(-3.5))),
            "UUID" to NbtTag.IntArrayTag(listOf(1, 2, 3, 4)),
            "Inventory" to NbtTag.ListTag(10, listOf(item("minecraft:diamond", 3, slot = 0), item("minecraft:gold_nugget", 64, slot = 35))),
            "EnderItems" to NbtTag.ListTag(10, listOf(item("minecraft:iron_nugget", 5, slot = 26))),
            "equipment" to NbtTag.CompoundTag(mapOf("head" to item("minecraft:iron_helmet", 1), "offhand" to item("minecraft:shield", 1))),
            "Empty" to NbtTag.ListTag(0, emptyList()),
            "Name" to NbtTag.StringTag("도윤 ✓"),
        ),
    )

    @Test
    fun `nbt survives a compressed round trip including unicode and empty lists`() {
        assertEquals(playerFile, readCompressedNbt(writeCompressedNbt(playerFile)))
    }

    @Test
    fun `items are read by slot from inventory, ender chest and equipment`() {
        val items = storedPlayerItemsOf(readCompressedNbt(writeCompressedNbt(playerFile)))

        assertEquals(4_700, items.dataVersion)
        assertEquals(setOf(0, 35), items.inventory.keys)
        assertEquals(NbtTag.StringTag("minecraft:gold_nugget"), items.inventory.getValue(35)["id"])
        assertEquals(setOf(26), items.enderChest.keys)
        assertEquals(setOf("head", "offhand"), items.equipment.keys)
    }

    @Test
    fun `serialized item bytes drop the slot and carry the data version`() {
        val bytes = serializedItemBytes(item("minecraft:diamond", 3, slot = 7), dataVersion = 4_700)

        val root = readCompressedNbt(bytes)
        assertEquals(setOf("id", "count", "DataVersion"), root.values.keys)
        assertEquals(NbtTag.IntTag(4_700), root["DataVersion"])
    }

    @Test
    fun `files without a data version are rejected`() {
        assertFailsWith<IllegalArgumentException> { storedPlayerItemsOf(NbtTag.CompoundTag(emptyMap())) }
    }
}
