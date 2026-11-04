package dev.doyu.econet.paper.cash

import dev.doyu.econet.paper.format.MoneyFormat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

/**
 * 동전 아이템을 만들고 알아본다.
 * 진짜 아이템에 custom_data 태그를 붙여 구분하므로 리소스팩이 필요 없고, 모루로는 태그를 붙일 수 없어 서바이벌에서 위조할 수 없다.
 */
class CoinItems(plugin: Plugin, private val money: MoneyFormat) {
    private val coinKey = NamespacedKey(plugin, "coin")

    fun create(coins: Map<Denomination, Long>): List<ItemStack> = buildList {
        for ((denomination, count) in coins) {
            val template = template(denomination)
            var remaining = count
            while (remaining > 0) {
                val stackSize = minOf(remaining, template.maxStackSize.toLong()).toInt()
                add(template.clone().apply { amount = stackSize })
                remaining -= stackSize
            }
        }
    }

    /** 태그와 베이스 아이템이 둘 다 맞아야 동전이다. 다른 아이템에 태그만 옮겨 붙인 경우를 막기 위해서다. */
    fun denominationOf(item: ItemStack?): Denomination? {
        if (item == null || item.isEmpty) return null
        val tag = item.persistentDataContainer.get(coinKey, PersistentDataType.STRING) ?: return null
        val denomination = Denomination.entries.firstOrNull { it.name == tag } ?: return null
        return denomination.takeIf { item.type == materialOf(it) }
    }

    fun isCoin(item: ItemStack?): Boolean = denominationOf(item) != null

    fun count(items: List<ItemStack>): Map<Denomination, Long> =
        items.groupingBy { requireNotNull(denominationOf(it)) { "not a coin: $it" } }
            .fold(0L) { sum, item -> sum + item.amount }

    private fun template(denomination: Denomination): ItemStack = ItemStack.of(materialOf(denomination)).apply {
        editMeta { meta ->
            meta.displayName(Component.text(denomination.displayName, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
            meta.lore(listOf(Component.text("액면 ${money.display(denomination.value)}", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
            meta.setEnchantmentGlintOverride(true)
            meta.persistentDataContainer.set(coinKey, PersistentDataType.STRING, denomination.name)
        }
    }

    private fun materialOf(denomination: Denomination): Material =
        requireNotNull(Material.matchMaterial(denomination.itemKey)) { "unknown coin base ${denomination.itemKey}" }
}
