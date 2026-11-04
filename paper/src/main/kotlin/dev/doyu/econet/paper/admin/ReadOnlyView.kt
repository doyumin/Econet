package dev.doyu.econet.paper.admin

import net.kyori.adventure.text.Component
import org.bukkit.Server
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

/** 오프라인 플레이어 아이템의 사본을 보여 주는 화면. 사본을 꺼낼 수 있으면 그대로 복제가 되므로 모든 조작을 막는다. */
class ReadOnlyView(size: Int, title: Component, server: Server, items: Map<Int, ItemStack>) : InventoryHolder {
    private val inventory: Inventory = server.createInventory(this, size, title)

    init {
        for ((slot, item) in items) if (slot in 0 until size) inventory.setItem(slot, item)
    }

    override fun getInventory(): Inventory = inventory

    companion object {
        /** 위 세 줄 = 인벤토리 9~35, 넷째 줄 = 단축바 0~8, 다섯째 줄 = 머리·가슴·다리·발·왼손·몸·안장. */
        fun inventoryLayout(items: OfflineItems): Map<Int, ItemStack> = buildMap {
            for ((slot, item) in items.inventory) {
                when (slot) {
                    in 9..35 -> put(slot - 9, item)
                    in 0..8 -> put(27 + slot, item)
                }
            }
            EQUIPMENT_ORDER.forEachIndexed { index, slotName -> items.equipment[slotName]?.let { put(36 + index, it) } }
        }

        private val EQUIPMENT_ORDER = listOf("head", "chest", "legs", "feet", "offhand", "body", "saddle")
    }
}

class ReadOnlyGuard : Listener {
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onClick(event: InventoryClickEvent) {
        if (event.view.topInventory.getHolder(false) is ReadOnlyView) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.getHolder(false) is ReadOnlyView) event.isCancelled = true
    }
}
