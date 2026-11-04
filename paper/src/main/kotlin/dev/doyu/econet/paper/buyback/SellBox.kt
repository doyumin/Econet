package dev.doyu.econet.paper.buyback

import dev.doyu.econet.paper.format.MoneyFormat
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.math.RoundingMode

/**
 * 판매 상자 한 개. 위 다섯 줄은 아이템을 넣는 칸, 맨 아랫줄은 견적을 보여 주는 칸이다.
 * 아랫줄 아이템은 고유 태그를 달아, 플레이어 아이템과 절대 합쳐지지 않게 한다.
 * 합쳐지면 정산 때 돌려주지 않는 칸으로 플레이어 아이템이 섞여 들어가 사라진다.
 */
class SellBox(val player: Player, server: Server, private val uiTag: NamespacedKey) : InventoryHolder {
    private val inventory: Inventory = server.createInventory(this, SIZE, Component.text("정부 매입"))

    init {
        for (slot in RESERVED_SLOTS) inventory.setItem(slot, uiItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), emptyList()))
    }

    override fun getInventory(): Inventory = inventory

    fun deposits(): List<ItemStack> = DEPOSIT_SLOTS.mapNotNull { slot -> inventory.getItem(slot)?.takeUnless { it.isEmpty }?.clone() }

    /** 아이템을 꺼내 상자를 비운다. 이후 이 아이템의 책임은 호출자에게 있다. */
    fun takeDeposits(): List<ItemStack> {
        val items = deposits()
        for (slot in DEPOSIT_SLOTS) inventory.setItem(slot, null)
        return items
    }

    fun showOffer(offer: SaleOffer<ItemStack>, money: MoneyFormat) {
        val lines = buildList {
            for (quote in offer.quotes) {
                val sample = offer.sellable.first { it.type.key.toString() == quote.itemKey }
                val unit = quote.currentUnitPrice.setScale(2, RoundingMode.FLOOR).toPlainString()
                add(
                    Component.translatable(sample.translationKey())
                        .append(Component.text(" ×${quote.count}: 개당 ${unit} → 합 ${quote.total.setScale(2, RoundingMode.FLOOR).toPlainString()}"))
                        .color(NamedTextColor.GRAY),
                )
            }
            if (offer.rejected.isNotEmpty()) {
                add(Component.text("매입하지 않는 아이템 ${offer.rejected.size}칸: 닫으면 돌려드립니다", NamedTextColor.RED))
            }
            add(Component.empty())
            add(Component.text("예상 수령액: ${money.display(offer.payout)}", NamedTextColor.GOLD))
            add(Component.text("상자를 닫으면 정산합니다", NamedTextColor.DARK_GRAY))
        }
        inventory.setItem(INFO_SLOT, uiItem(Material.BOOK, Component.text("견적", NamedTextColor.YELLOW), lines))
    }

    private fun uiItem(material: Material, name: Component, lore: List<Component>): ItemStack =
        ItemStack.of(material).apply {
            editMeta { meta ->
                meta.displayName(name.decoration(TextDecoration.ITALIC, false))
                meta.lore(lore.map { it.decoration(TextDecoration.ITALIC, false) })
                meta.persistentDataContainer.set(uiTag, PersistentDataType.BOOLEAN, true)
            }
        }

    companion object {
        private const val SIZE = 54
        private const val INFO_SLOT = 49
        val DEPOSIT_SLOTS = 0 until 45
        val RESERVED_SLOTS = 45 until SIZE
    }
}
