package dev.doyu.econet.paper.admin

/**
 * 오프라인 플레이어 파일(`<월드>/players/data/<uuid>.dat`, Paper 26.3)에서 아이템 부분만 꺼낸다.
 *
 * 26.3 형식(서버 코드에서 확인): 인벤토리는 `Inventory`, 엔더상자는 `EnderItems` 목록이고 각 원소는 `Slot`이 붙은 아이템,
 * 갑옷과 왼손은 `equipment` compound에 슬롯 이름(head, chest, legs, feet, offhand, ...)으로 들어 있다.
 */
data class StoredPlayerItems(
    val dataVersion: Int,
    val inventory: Map<Int, NbtTag.CompoundTag>,
    val enderChest: Map<Int, NbtTag.CompoundTag>,
    val equipment: Map<String, NbtTag.CompoundTag>,
)

fun storedPlayerItemsOf(root: NbtTag.CompoundTag): StoredPlayerItems {
    val dataVersion = requireNotNull(root["DataVersion"] as? NbtTag.IntTag) { "player data has no DataVersion" }.value
    val equipment = (root["equipment"] as? NbtTag.CompoundTag)?.values.orEmpty()
        .mapNotNull { (slot, item) -> (item as? NbtTag.CompoundTag)?.let { slot to it } }
        .toMap()
    return StoredPlayerItems(
        dataVersion = dataVersion,
        inventory = slottedItems(root["Inventory"]),
        enderChest = slottedItems(root["EnderItems"]),
        equipment = equipment,
    )
}

/**
 * 아이템 하나를 ItemStack.deserializeBytes가 받는 형식으로 바꾼다: 아이템 compound + DataVersion, gzip.
 * DataVersion을 붙여야 Paper가 옛 버전에서 저장된 아이템도 현재 형식으로 변환해 준다.
 */
fun serializedItemBytes(item: NbtTag.CompoundTag, dataVersion: Int): ByteArray =
    writeCompressedNbt(NbtTag.CompoundTag(item.values - "Slot" + ("DataVersion" to NbtTag.IntTag(dataVersion))))

private fun slottedItems(tag: NbtTag?): Map<Int, NbtTag.CompoundTag> {
    val list = tag as? NbtTag.ListTag ?: return emptyMap()
    return list.values.filterIsInstance<NbtTag.CompoundTag>().mapNotNull { item ->
        val slot = item["Slot"] as? NbtTag.ByteTag ?: return@mapNotNull null
        slot.value.toInt() to item
    }.toMap()
}
