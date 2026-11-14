package dev.doyu.econet.paper.cash

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.BundleContents
import io.papermc.paper.datacomponent.item.ItemContainerContents
import org.bukkit.inventory.ItemStack

/** 셜커 상자·꾸러미는 다른 셜커 상자·꾸러미를 담을 수 있으므로 어디까지 들여다볼지 정한다. 동전 집계와 보험이 같은 깊이를 쓴다. */
const val MAX_CONTAINER_NESTING = 4

/**
 * [item] 안쪽(셜커 상자·꾸러미, 그 안의 셜커 상자·꾸러미…)의 동전을 꺼내 돌려주고, [item]에는 동전만 뺀 내용을 남긴다.
 * 컨테이너를 통째로 떨구면 보험의 취지(동전만 위험하다)보다 벌이 커지므로 안의 동전만 꺼낸다.
 * 컨테이너 안 아이템의 칸 위치는 보존되지 않을 수 있다(Paper API가 위치를 노출하지 않는다).
 */
fun extractNestedCoins(item: ItemStack, isCoin: (ItemStack?) -> Boolean, depth: Int = 0): List<ItemStack> {
    if (item.isEmpty || depth >= MAX_CONTAINER_NESTING) return emptyList()
    val found = mutableListOf<ItemStack>()

    fun withoutCoins(contents: List<ItemStack>): List<ItemStack>? {
        val kept = mutableListOf<ItemStack>()
        var changed = false
        for (inner in contents) {
            if (isCoin(inner)) {
                found += inner
                changed = true
                continue
            }
            // contents()는 복사본을 돌려주므로, 안쪽을 고친 뒤 다시 담아야 반영된다.
            val deeper = extractNestedCoins(inner, isCoin, depth + 1)
            found += deeper
            changed = changed || deeper.isNotEmpty()
            kept += inner
        }
        return kept.takeIf { changed }
    }

    item.getData(DataComponentTypes.CONTAINER)?.let { container ->
        withoutCoins(container.contents())?.let { item.setData(DataComponentTypes.CONTAINER, ItemContainerContents.containerContents(it)) }
    }
    item.getData(DataComponentTypes.BUNDLE_CONTENTS)?.let { bundle ->
        withoutCoins(bundle.contents())?.let { item.setData(DataComponentTypes.BUNDLE_CONTENTS, BundleContents.bundleContents(it)) }
    }
    return scaledByStack(found, item.amount)
}

/** 컨테이너가 겹쳐 쌓여 있으면 그 안의 동전도 개수만큼이므로, 한 스택의 최대 크기를 넘지 않게 나눠서 곱한다. */
private fun scaledByStack(coins: List<ItemStack>, copies: Int): List<ItemStack> {
    if (copies == 1) return coins
    return coins.flatMap { coin ->
        var remaining = coin.amount.toLong() * copies
        buildList {
            while (remaining > 0) {
                val size = minOf(remaining, coin.maxStackSize.toLong()).toInt()
                add(coin.clone().apply { amount = size })
                remaining -= size
            }
        }
    }
}
