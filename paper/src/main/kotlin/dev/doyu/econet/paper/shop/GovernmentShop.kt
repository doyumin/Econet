package dev.doyu.econet.paper.shop

import dev.doyu.econet.core.AccountId
import dev.doyu.econet.core.Actor
import dev.doyu.econet.core.Ledger
import dev.doyu.econet.core.TransferResult
import dev.doyu.econet.core.TxKind
import dev.doyu.econet.paper.command.failureMessage
import dev.doyu.econet.paper.fitsInInventory
import dev.doyu.econet.paper.format.MoneyFormat
import dev.doyu.econet.paper.receive
import kotlinx.coroutines.runBlocking
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.ItemStack
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 정부 상점. 지금은 틀만 있다: 설정의 품목을 진열하고, 클릭하면 예금으로 1개 산다.
 * 돈을 받은 뒤에 아이템을 주므로 메인 스레드에서 장부를 기다린다.
 */
class GovernmentShop(
    private val ledger: Ledger,
    private val items: List<ShopItem>,
    private val money: MoneyFormat,
    private val server: Server,
    private val logger: Logger,
) : Listener {
    fun open(player: Player) {
        if (items.isEmpty()) {
            player.sendMessage("상점에 진열된 물건이 없습니다.")
            return
        }
        player.openInventory(ShopMenu(items, server, money).inventory)
    }

    /** 진열품이 플레이어 인벤토리로 새어 나가지 않도록, 상점이 열려 있는 동안의 클릭은 모두 막고 구매만 처리한다. */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onClick(event: InventoryClickEvent) {
        val menu = event.view.topInventory.getHolder(false) as? ShopMenu ?: return
        event.isCancelled = true
        if (!event.isLeftClick && !event.isRightClick) return
        val item = menu.itemAt(event.rawSlot) ?: return
        val player = event.whoClicked as? Player ?: return
        buy(player, item)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.getHolder(false) is ShopMenu) event.isCancelled = true
    }

    private fun buy(player: Player, item: ShopItem) {
        val goods = ItemStack.of(item.material)
        if (!server.fitsInInventory(player, listOf(goods))) {
            player.sendMessage("인벤토리에 공간이 없습니다.")
            return
        }
        val result = try {
            runBlocking {
                ledger.transfer(AccountId.Player(player.uniqueId), AccountId.Government, item.price, TxKind.Purchase, Actor.Player(player.uniqueId), "shop ${item.material.key}")
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Shop purchase failed for ${player.name}", e)
            player.sendMessage("오류로 구매하지 못했습니다. 잔액은 바뀌지 않았습니다.")
            return
        }
        when (result) {
            is TransferResult.Success -> {
                player.receive(listOf(goods))
                player.sendMessage("${money.display(item.price)}에 구매했습니다.")
            }
            is TransferResult.Failure -> player.sendMessage(failureMessage(result, money))
        }
    }
}
