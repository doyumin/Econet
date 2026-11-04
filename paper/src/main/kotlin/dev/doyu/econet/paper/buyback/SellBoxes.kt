package dev.doyu.econet.paper.buyback

import dev.doyu.econet.core.AccountId
import dev.doyu.econet.core.Actor
import dev.doyu.econet.core.Ledger
import dev.doyu.econet.core.TransferResult
import dev.doyu.econet.core.TxKind
import dev.doyu.econet.paper.command.failureMessage
import dev.doyu.econet.paper.format.MoneyFormat
import dev.doyu.econet.paper.receive
import kotlinx.coroutines.runBlocking
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.time.Clock
import java.util.UUID
import java.util.logging.Level

/**
 * 열린 판매 상자를 관리하고 닫힐 때 정산한다. 모든 일은 메인 스레드에서 한다.
 *
 * 정산에서만 메인 스레드에서 장부를 기다린다(runBlocking). 비동기로 하면 지급을 기다리는 동안 아이템이 어디에도 없는
 * 상태가 생기고, 그 사이 서버가 종료되면 아이템을 돌려줄 수 없다. 상자를 닫을 때 한 번, 커밋 한 번이라 멈춤은 짧다.
 */
class SellBoxes(
    private val plugin: Plugin,
    private val ledger: Ledger,
    private val market: BuybackMarket,
    private val stateFile: MarketStateFile,
    private val money: MoneyFormat,
    private val clock: Clock,
    private val alertAdmins: (String) -> Unit,
    private val isCoin: (ItemStack) -> Boolean,
) : Listener {
    private val uiTag = NamespacedKey(plugin, "sell_box_ui")
    private val open = mutableMapOf<UUID, SellBox>()
    private val settlement = SaleSettlement<ItemStack>(market, ::buybackKeyOf, ItemStack::getAmount)

    fun open(player: Player) {
        val box = SellBox(player, plugin.server, uiTag)
        box.showOffer(settlement.offer(emptyList(), clock.instant()), money)
        player.openInventory(box.inventory)
        // openInventory가 이전 화면을 닫으며 정산을 부를 수 있으므로, 새 상자는 열린 뒤에 등록한다.
        open[player.uniqueId] = box
    }

    /** 플러그인이 꺼질 때는 닫힘 이벤트를 받을 수 없으므로 직접 정산한다. 플레이어 저장은 이보다 나중에 일어난다. */
    fun settleAll() {
        for (box in open.values.toList()) {
            settle(box, InventoryCloseEvent.Reason.PLUGIN)
            box.player.closeInventory()
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onClick(event: InventoryClickEvent) {
        val box = event.view.topInventory.getHolder(false) as? SellBox ?: return
        if (event.rawSlot in SellBox.RESERVED_SLOTS) {
            event.isCancelled = true
            return
        }
        refreshNextTick(box)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDrag(event: InventoryDragEvent) {
        val box = event.view.topInventory.getHolder(false) as? SellBox ?: return
        if (event.rawSlots.any { it in SellBox.RESERVED_SLOTS }) {
            event.isCancelled = true
            return
        }
        refreshNextTick(box)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onClose(event: InventoryCloseEvent) {
        val box = event.inventory.getHolder(false) as? SellBox ?: return
        settle(box, event.reason)
    }

    private fun refreshNextTick(box: SellBox) {
        // 클릭 이벤트 시점에는 아직 아이템이 옮겨지지 않았으므로 다음 틱에 다시 계산한다.
        plugin.server.scheduler.runTask(plugin) { _ ->
            if (open[box.player.uniqueId] === box) box.showOffer(settlement.offer(box.deposits(), clock.instant()), money)
        }
    }

    private fun settle(box: SellBox, reason: InventoryCloseEvent.Reason) {
        // 닫힘이 두 번 오더라도(직접 정산 후 closeInventory 등) 한 번만 정산해야 아이템이 복제되지 않는다.
        if (!open.remove(box.player.uniqueId, box)) return

        val player = box.player
        val items = box.takeDeposits()
        if (items.isEmpty()) return

        val outcome = try {
            runBlocking {
                settlement.settle(items, clock.instant()) { amount, memo ->
                    ledger.transfer(AccountId.Government, AccountId.Player(player.uniqueId), amount, TxKind.Sale, Actor.Player(player.uniqueId), memo)
                }
            }
        } catch (e: Exception) {
            // settle은 예외를 결과로 바꾸지만, runBlocking 자체가 실패하는 경우까지 아이템을 지킨다.
            SaleOutcome.Failed(e, items)
        }
        giveBack(player, outcome.returned, reason)

        when (outcome) {
            is SaleOutcome.Sold -> {
                saveMarketState()
                player.sendMessage("정부에 판매했습니다. 받은 금액: ${money.display(outcome.payout)}")
                if (outcome.returned.isNotEmpty()) player.sendMessage("매입하지 않는 아이템은 돌려드렸습니다.")
                // 실수로 동전을 시세보다 싸게 팔지 않도록 매입하지 않고, 대신 제 값에 바꾸는 방법을 알려 준다.
                if (outcome.returned.any(isCoin)) player.sendMessage("동전은 매입하지 않습니다. /입금 으로 예금하세요.")
            }
            is SaleOutcome.NothingToSell -> {
                player.sendMessage("판매할 아이템이 없거나 1듄 미만이라 모두 돌려드렸습니다.")
                if (outcome.returned.any(isCoin)) player.sendMessage("동전은 매입하지 않습니다. /입금 으로 예금하세요.")
            }
            is SaleOutcome.Refused -> {
                val failure = outcome.failure
                if (failure is TransferResult.Failure.InsufficientFunds) {
                    player.sendMessage("정부 예산이 부족해 매입하지 못했습니다. 아이템을 모두 돌려드렸습니다.")
                    alertAdmins("정부 금고가 부족해 매입이 멈췄습니다. 금고 ${money.display(failure.balance)}, 요청 ${money.display(failure.required)}")
                } else {
                    player.sendMessage("${failureMessage(failure, money)} 아이템을 모두 돌려드렸습니다.")
                }
            }
            is SaleOutcome.Failed -> {
                plugin.logger.log(Level.SEVERE, "Buyback settlement failed for ${player.name}; items were returned", outcome.error)
                player.sendMessage("오류로 판매하지 못했습니다. 아이템을 모두 돌려드렸습니다.")
            }
        }
    }

    /**
     * 사망으로 닫힐 때는 사망 드롭이 이미 정해진 뒤라 인벤토리에 넣으면 사라질 수 있으므로, 그 자리에 떨군다.
     * 접속 종료로 닫힐 때는 플레이어 저장보다 먼저 일어나므로 인벤토리에 넣으면 저장된다(Paper 26.3 PlayerList.remove 순서 확인).
     */
    private fun giveBack(player: Player, items: List<ItemStack>, reason: InventoryCloseEvent.Reason) {
        if (reason == InventoryCloseEvent.Reason.DEATH) {
            for (item in items) player.world.dropItemNaturally(player.location, item)
        } else {
            player.receive(items)
        }
    }

    private fun saveMarketState() {
        try {
            stateFile.save(market.snapshot())
        } catch (e: Exception) {
            plugin.logger.log(Level.WARNING, "Could not save buyback market state", e)
        }
    }
}

/** 손대지 않은 기본 아이템만 매입한다. 이름·인챈트·태그가 붙은 아이템(금화 포함)은 기본 아이템과 같지 않으므로 걸러진다. */
fun buybackKeyOf(item: ItemStack): String? =
    if (item.isSimilar(ItemStack.of(item.type))) item.type.key.toString() else null
