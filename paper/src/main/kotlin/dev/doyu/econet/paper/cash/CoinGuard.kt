package dev.doyu.econet.paper.cash

import org.bukkit.block.Crafter
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.CrafterCraftEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent

/**
 * 동전이 제작 재료로 쓰이지 않게 한다.
 * 레시피 책의 자동 채우기는 같은 아이템이면 동전도 집어 넣으므로, 다이아화 아홉 개(900듄)가 다이아 블록 하나로 사라질 수 있다.
 */
class CoinGuard(private val coins: CoinItems) : Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPrepareCraft(event: PrepareItemCraftEvent) {
        if (event.inventory.matrix.any(coins::isCoin)) event.inventory.result = null
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCrafterCraft(event: CrafterCraftEvent) {
        val crafter = event.block.state as? Crafter ?: return
        if (crafter.inventory.contents.any(coins::isCoin)) event.isCancelled = true
    }
}
