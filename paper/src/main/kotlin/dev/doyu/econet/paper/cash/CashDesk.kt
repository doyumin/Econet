package dev.doyu.econet.paper.cash

import dev.doyu.econet.core.AccountId
import dev.doyu.econet.core.Actor
import dev.doyu.econet.core.Amount
import dev.doyu.econet.core.Ledger
import dev.doyu.econet.core.TransferResult
import dev.doyu.econet.core.TxKind
import dev.doyu.econet.paper.command.failureMessage
import dev.doyu.econet.paper.format.MoneyFormat
import kotlinx.coroutines.runBlocking
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 예금과 현금을 바꾸는 창구. 모든 일은 메인 스레드에서 한다.
 *
 * 아이템과 돈이 함께 움직이므로 판매 상자 정산처럼 메인 스레드에서 장부를 기다린다.
 * 비동기로 하면 장부는 움직였는데 동전은 아직 없는(또는 그 반대) 순간이 생기고, 그 사이 종료되면 되돌릴 수 없다.
 */
class CashDesk(
    private val ledger: Ledger,
    private val coins: CoinItems,
    private val money: MoneyFormat,
    private val server: Server,
    private val logger: Logger,
    private val alertAdmins: (String) -> Unit,
) {
    /** 예금(또는 정부 금고)에서 돈을 빼 동전으로 준다. 장부가 성공한 뒤에만 동전을 만든다. */
    fun withdraw(player: Player, amount: Amount, from: AccountId, actor: Actor) {
        val breakdown = breakDown(amount.value)
        // 동전 목록을 만들기 전에 막는다. 큰 금액이면 목록 자체가 메모리를 다 먹는다.
        if (breakdown.values.sum() > MAX_COINS_PER_WITHDRAWAL) {
            player.sendMessage("한 번에 인출할 수 있는 동전은 ${MAX_COINS_PER_WITHDRAWAL}개까지입니다.")
            return
        }
        val items = coins.create(breakdown)
        if (!fits(player, items)) {
            player.sendMessage("인벤토리에 동전을 받을 공간이 부족합니다.")
            return
        }

        val result = try {
            runBlocking { ledger.transfer(from, AccountId.CashPool, amount, TxKind.CashOut, actor) }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Withdrawal failed for ${player.name}", e)
            player.sendMessage("오류로 인출하지 못했습니다. 잔액은 바뀌지 않았습니다.")
            return
        }
        when (result) {
            is TransferResult.Success -> {
                give(player, items)
                player.sendMessage("${money.display(amount)}을 현금으로 인출했습니다.")
            }
            is TransferResult.Failure -> player.sendMessage(failureMessage(result, money))
        }
    }

    /** 인벤토리의 모든 동전을 예금으로 넣는다. 동전을 먼저 꺼내 두고, 입금이 실패하면 어떤 경우든 되돌려 놓는다. */
    fun deposit(player: Player) {
        val taken = takeAllCoins(player)
        if (taken.isEmpty()) {
            player.sendMessage("인벤토리에 동전이 없습니다.")
            return
        }
        var deposited = false
        try {
            val amount = Amount(totalValue(coins.count(taken)))
            val result = runBlocking {
                ledger.transfer(AccountId.CashPool, AccountId.Player(player.uniqueId), amount, TxKind.CashIn, Actor.Player(player.uniqueId))
            }
            when (result) {
                is TransferResult.Success -> {
                    deposited = true
                    player.sendMessage("${money.display(amount)}을 예금했습니다.")
                }
                is TransferResult.Failure -> {
                    player.sendMessage("${failureMessage(result, money)} 동전을 돌려드렸습니다.")
                    // 현금 유통 계좌보다 많은 동전이 들어오려 했다는 것은 그만큼의 동전이 장부 밖에서 생겼다는 뜻이다.
                    if (result is TransferResult.Failure.InsufficientFunds) {
                        alertAdmins("동전 복사 의심: ${player.name}님이 ${money.plain(amount)} 입금을 시도했지만 현금 유통 계좌에는 ${money.plain(result.balance)}뿐입니다.")
                    }
                }
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Deposit failed for ${player.name}; coins were returned", e)
            player.sendMessage("오류로 예금하지 못했습니다. 동전을 돌려드렸습니다.")
        } finally {
            if (!deposited) give(player, taken)
        }
    }

    /** 손에 든 동전 하나를 한 단계 아래 동전 열 개로 바꾼다. 장부와 무관하다(현금 총액이 그대로다). */
    fun split(player: Player) {
        val held = player.inventory.itemInMainHand
        val denomination = coins.denominationOf(held)
        if (denomination == null) {
            player.sendMessage("쪼갤 동전을 손에 들어 주세요.")
            return
        }
        val smaller = denomination.smaller
        if (smaller == null) {
            player.sendMessage("${denomination.displayName}는 더 쪼갤 수 없습니다.")
            return
        }
        val before = held.clone()
        player.inventory.setItemInMainHand(if (held.amount > 1) held.clone().apply { amount -= 1 } else null)
        val change = coins.create(mapOf(smaller to denomination.value / smaller.value))
        if (!fits(player, change)) {
            player.inventory.setItemInMainHand(before)
            player.sendMessage("인벤토리에 잔돈을 받을 공간이 부족합니다.")
            return
        }
        give(player, change)
        player.sendMessage("${denomination.displayName} 1개를 ${smaller.displayName} ${change.sumOf { it.amount }}개로 바꿨습니다.")
    }

    /** 인벤토리의 동전을 가장 적은 개수로 다시 묶는다. */
    fun merge(player: Player) {
        val taken = takeAllCoins(player)
        if (taken.isEmpty()) {
            player.sendMessage("인벤토리에 동전이 없습니다.")
            return
        }
        var merged = false
        try {
            val total = totalValue(coins.count(taken))
            val regrouped = coins.create(breakDown(total))
            if (fits(player, regrouped)) {
                give(player, regrouped)
                merged = true
                player.sendMessage("동전 ${money.display(total)}을 다시 묶었습니다.")
            } else {
                player.sendMessage("인벤토리에 공간이 부족합니다.")
            }
        } finally {
            if (!merged) give(player, taken)
        }
    }

    private fun takeAllCoins(player: Player): List<ItemStack> {
        val inventory = player.inventory
        val taken = mutableListOf<ItemStack>()
        for (slot in 0 until inventory.size) {
            val item = inventory.getItem(slot)
            if (coins.isCoin(item)) {
                taken += item!!.clone()
                inventory.setItem(slot, null)
            }
        }
        return taken
    }

    /** 실제 인벤토리를 건드리지 않고, 복사본에 넣어 보아 다 들어가는지 확인한다. */
    private fun fits(player: Player, items: List<ItemStack>): Boolean {
        val probe = server.createInventory(null, PLAYER_STORAGE_SIZE)
        probe.storageContents = player.inventory.storageContents.map { it?.clone() }.toTypedArray()
        return probe.addItem(*items.map { it.clone() }.toTypedArray()).isEmpty()
    }

    private fun give(player: Player, items: List<ItemStack>) {
        if (items.isEmpty()) return
        val leftovers = player.inventory.addItem(*items.toTypedArray()).values
        for (item in leftovers) player.world.dropItemNaturally(player.location, item)
    }

    private companion object {
        const val PLAYER_STORAGE_SIZE = 36
        const val MAX_COINS_PER_WITHDRAWAL = PLAYER_STORAGE_SIZE * 64L
    }
}
