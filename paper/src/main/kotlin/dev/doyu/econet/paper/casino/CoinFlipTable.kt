package dev.doyu.econet.paper.casino

import dev.doyu.econet.core.AccountId
import dev.doyu.econet.core.Actor
import dev.doyu.econet.core.Amount
import dev.doyu.econet.core.Ledger
import dev.doyu.econet.core.TransferResult
import dev.doyu.econet.core.TxKind
import dev.doyu.econet.paper.cash.CoinItems
import dev.doyu.econet.paper.cash.Denomination
import dev.doyu.econet.paper.cash.breakDown
import dev.doyu.econet.paper.cash.totalValue
import dev.doyu.econet.paper.command.ADMIN_PERMISSION
import dev.doyu.econet.paper.command.failureMessage
import dev.doyu.econet.paper.config.CoinFlipSettings
import dev.doyu.econet.paper.fitsInInventory
import dev.doyu.econet.paper.format.MoneyFormat
import dev.doyu.econet.paper.receive
import kotlinx.coroutines.runBlocking
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import java.security.SecureRandom
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 동전 던지기. 판돈은 인벤토리의 동전으로만 낸다.
 *
 * 아이템과 돈이 함께 움직이므로 메인 스레드에서 장부를 기다린다(CashDesk와 같은 이유).
 * 판돈 동전을 먼저 꺼내 두고, 정산이 성공하지 못하면 어떤 경우든 `finally`에서 돌려준다.
 * 거래는 한 판에 한 건이다: 이기면 정부 금고 → 현금 유통(CashOut), 지면 현금 유통 → 정부 금고(CashIn).
 */
class CoinFlipTable(
    private val ledger: Ledger,
    private val machines: CasinoMachines,
    private val coins: CoinItems,
    private val settings: CoinFlipSettings,
    private val money: MoneyFormat,
    private val server: Server,
    private val logger: Logger,
    private val alertAdmins: (String) -> Unit,
) : Listener {
    private val random = SecureRandom()
    private val lastPlayed = mutableMapOf<UUID, Long>()

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK || event.hand != EquipmentSlot.HAND) return
        val block = event.clickedBlock ?: return
        if (machines.gameAt(block.toSpot()) != CasinoMachines.COIN_FLIP) return
        event.isCancelled = true
        event.player.openInventory(CoinFlipMenu(settings.bets, settings.winChance, server, money).inventory)
    }

    /** 기계가 사라지면 등록만 남으므로 부수지 못하게 한다. 관리자는 `/카지노 철거`로 등록을 먼저 지운다. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        if (machines.gameAt(event.block.toSpot()) == null) return
        event.isCancelled = true
        val isAdmin = event.player.hasPermission(ADMIN_PERMISSION)
        event.player.sendMessage(if (isAdmin) "도박 기계입니다. 먼저 /카지노 철거로 등록을 지워 주세요." else "도박 기계는 부술 수 없습니다.")
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onClick(event: InventoryClickEvent) {
        val menu = event.view.topInventory.getHolder(false) as? CoinFlipMenu ?: return
        event.isCancelled = true
        if (!event.isLeftClick && !event.isRightClick) return
        val bet = menu.betAt(event.rawSlot) ?: return
        val player = event.whoClicked as? Player ?: return
        flip(player, bet)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.getHolder(false) is CoinFlipMenu) event.isCancelled = true
    }

    private fun flip(player: Player, bet: Amount) {
        val now = System.nanoTime()
        val previous = lastPlayed[player.uniqueId]
        if (previous != null && now - previous < settings.cooldown.toNanos()) {
            player.sendMessage("잠시 후에 다시 던져 주세요.")
            return
        }

        val plan = planStake(heldCoins(player), bet.value)
        if (plan == null) {
            player.sendMessage("판돈 ${money.display(bet)}만큼의 동전이 없습니다. 예금은 걸 수 없으니 /인출로 동전을 받아 오세요.")
            return
        }
        // 이긴 판만 취소되는 편향을 막으려고, 결과를 정하기 전에 금고가 지급할 수 있는지 먼저 확인한다.
        val treasury = runBlocking { ledger.balanceOf(AccountId.Government) }
        if (treasury.value < bet.value) {
            player.sendMessage("정부 금고가 모자라 이 판은 열 수 없습니다. 더 작은 판돈을 골라 보세요.")
            return
        }
        val taken = takeCoins(player, plan.take)
        if (taken == null) {
            player.sendMessage("동전을 꺼내지 못했습니다. 다시 시도해 주세요.")
            return
        }

        var settled = false
        try {
            val win = random.nextDouble() < settings.winChance
            val change = totalValue(plan.change)
            val payout = if (win) Math.addExact(change, Math.multiplyExact(bet.value, 2L)) else change
            val returned = coins.create(breakDown(payout))
            // 동전을 꺼낸 뒤의 인벤토리로 확인한다. 받을 공간이 없는 판은 시작하지 않는다.
            if (!server.fitsInInventory(player, returned)) {
                player.sendMessage("인벤토리에 동전을 받을 공간이 부족합니다.")
                return
            }
            val actor = Actor.Player(player.uniqueId)
            val result = runBlocking {
                if (win) ledger.transfer(AccountId.Government, AccountId.CashPool, bet, TxKind.CashOut, actor, "coinflip win")
                else ledger.transfer(AccountId.CashPool, AccountId.Government, bet, TxKind.CashIn, actor, "coinflip lose")
            }
            when (result) {
                is TransferResult.Success -> {
                    settled = true
                    lastPlayed[player.uniqueId] = now
                    player.receive(returned)
                    player.sendMessage(if (win) "앞면! ${money.display(bet)}을 땄습니다." else "뒷면... ${money.display(bet)}을 잃었습니다.")
                }
                is TransferResult.Failure -> {
                    player.sendMessage("${failureMessage(result, money)} 판돈을 돌려드렸습니다.")
                    // 현금 유통 계좌보다 많은 동전이 들어오려 했다는 것은 장부 밖에서 동전이 생겼다는 뜻이다.
                    if (!win && result is TransferResult.Failure.InsufficientFunds) {
                        alertAdmins("동전 복사 의심: ${player.name}님이 ${money.plain(bet)} 판돈을 냈지만 현금 유통 계좌에는 ${money.plain(result.balance)}뿐입니다.")
                    }
                }
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Coin flip failed for ${player.name}; the stake was returned", e)
            player.sendMessage("오류로 판이 취소되었습니다. 판돈을 돌려드렸습니다.")
        } finally {
            if (!settled) player.receive(taken)
        }
    }

    /** 동전을 두는 칸은 가방 36칸뿐이다. 갑옷·보조 손에 든 동전은 쓰지 않는다. */
    private fun heldCoins(player: Player): Map<Denomination, Long> {
        val held = mutableMapOf<Denomination, Long>()
        for (slot in 0 until STORAGE_SIZE) {
            val item = player.inventory.getItem(slot)
            val denomination = coins.denominationOf(item) ?: continue
            held.merge(denomination, item!!.amount.toLong(), Long::plus)
        }
        return held
    }

    /** 계획한 개수만큼 꺼낸다. 꺼낸 뒤 모자란 것이 있으면 모두 되돌리고 null. */
    private fun takeCoins(player: Player, need: Map<Denomination, Long>): List<ItemStack>? {
        val remaining = need.toMutableMap()
        val taken = mutableListOf<ItemStack>()
        for (slot in 0 until STORAGE_SIZE) {
            val item = player.inventory.getItem(slot)
            val denomination = coins.denominationOf(item) ?: continue
            val want = remaining[denomination] ?: continue
            if (want <= 0) continue
            val count = minOf(want, item!!.amount.toLong()).toInt()
            taken += item.clone().apply { amount = count }
            if (count == item.amount) player.inventory.setItem(slot, null) else item.amount -= count
            remaining[denomination] = want - count
        }
        if (remaining.values.any { it > 0 }) {
            player.receive(taken)
            return null
        }
        return taken
    }

    private companion object {
        const val STORAGE_SIZE = 36
    }
}
