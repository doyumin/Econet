package dev.doyu.econet.paper.shop

import dev.doyu.econet.core.Amount
import dev.doyu.econet.paper.format.MoneyFormat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Server
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

data class ShopItem(val material: Material, val price: Amount)

/** 정부 상점 진열대. 진열된 아이템은 견본일 뿐 꺼낼 수 없고, 클릭하면 새 아이템을 산다. */
class ShopMenu(val items: List<ShopItem>, server: Server, money: MoneyFormat) : InventoryHolder {
    private val inventory: Inventory = server.createInventory(this, rowsFor(items.size) * 9, Component.text("정부 상점"))

    init {
        items.forEachIndexed { slot, item ->
            inventory.setItem(slot, ItemStack.of(item.material).apply {
                editMeta { meta ->
                    meta.lore(
                        listOf(
                            Component.text("가격: ${money.display(item.price)}", NamedTextColor.GOLD),
                            Component.text("클릭하면 1개 삽니다", NamedTextColor.GRAY),
                        ).map { it.decoration(TextDecoration.ITALIC, false) },
                    )
                }
            })
        }
    }

    override fun getInventory(): Inventory = inventory

    fun itemAt(rawSlot: Int): ShopItem? = items.getOrNull(rawSlot)

    private companion object {
        fun rowsFor(count: Int): Int = ((count + 8) / 9).coerceIn(1, 6)
    }
}
