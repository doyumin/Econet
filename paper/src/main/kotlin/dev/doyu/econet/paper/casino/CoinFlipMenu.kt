package dev.doyu.econet.paper.casino

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

/** 판돈 버튼이 한 줄로 놓인 화면. 버튼은 견본일 뿐 꺼낼 수 없다. */
class CoinFlipMenu(private val bets: List<Amount>, winChance: Double, server: Server, money: MoneyFormat) : InventoryHolder {
    private val inventory: Inventory = server.createInventory(this, ROWS * 9, Component.text("동전 던지기"))

    init {
        require(bets.size <= 9) { "at most 9 bets fit in one row" }
        val chance = percentText(winChance)
        bets.forEachIndexed { index, bet ->
            inventory.setItem(FIRST_BET_SLOT + index, ItemStack.of(Material.SUNFLOWER).apply {
                editMeta { meta ->
                    meta.displayName(Component.text("판돈 ${money.display(bet)}", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
                    meta.lore(
                        listOf(
                            Component.text("이기면 +${money.display(bet)}, 지면 -${money.display(bet)}", NamedTextColor.GRAY),
                            Component.text("승률 $chance · 동전으로만 걸 수 있습니다", NamedTextColor.GRAY),
                            Component.text("클릭하면 바로 던집니다", NamedTextColor.GRAY),
                        ).map { it.decoration(TextDecoration.ITALIC, false) },
                    )
                }
            })
        }
    }

    override fun getInventory(): Inventory = inventory

    fun betAt(rawSlot: Int): Amount? = bets.getOrNull(rawSlot - FIRST_BET_SLOT)

    private companion object {
        const val ROWS = 3
        const val FIRST_BET_SLOT = 9

        fun percentText(chance: Double): String {
            val percent = chance * 100
            return if (percent % 1.0 == 0.0) "${percent.toInt()}%" else "%.1f%%".format(percent)
        }
    }
}
