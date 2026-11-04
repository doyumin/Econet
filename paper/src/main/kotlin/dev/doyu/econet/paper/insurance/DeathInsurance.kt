package dev.doyu.econet.paper.insurance

import dev.doyu.econet.core.AccountId
import dev.doyu.econet.core.Actor
import dev.doyu.econet.core.Amount
import dev.doyu.econet.core.Ledger
import dev.doyu.econet.core.TransferResult
import dev.doyu.econet.core.TxKind
import dev.doyu.econet.paper.command.failureMessage
import dev.doyu.econet.paper.format.MoneyFormat
import kotlinx.coroutines.runBlocking
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.inventory.ItemStack
import java.util.logging.Level
import java.util.logging.Logger

/**
 * 가입자가 죽으면 보험료를 받고 인벤토리를 지킨다. 단, 동전은 떨군다.
 * 현금은 위험하고 예금은 안전하다는 구분을 유지하기 위해서다(DESIGN 5장).
 *
 * 보험료를 받았는지에 따라 이 이벤트 안에서 드롭이 정해지므로, 메인 스레드에서 장부를 기다린다.
 */
class DeathInsurance(
    private val ledger: Ledger,
    private val policies: InsurancePolicies,
    private val premium: Amount,
    private val isCoin: (ItemStack?) -> Boolean,
    private val money: MoneyFormat,
    private val logger: Logger,
) : Listener {
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onDeath(event: PlayerDeathEvent) {
        // 서버가 이미 인벤토리를 지켜 주는 경우(keepInventory 게임 규칙 등)에는 보험료를 받을 이유가 없다.
        if (event.keepInventory) return
        val player = event.player
        if (!policies.isEnrolled(player.uniqueId)) return

        val result = try {
            runBlocking {
                ledger.transfer(AccountId.Player(player.uniqueId), AccountId.Government, premium, TxKind.Purchase, Actor.System("insurance"), "death insurance premium")
            }
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Could not charge the insurance premium for ${player.name}", e)
            player.sendMessage("오류로 보험이 적용되지 않았습니다.")
            return
        }
        when (result) {
            is TransferResult.Success -> {
                keepEverythingButCoins(event)
                player.sendMessage("사망 보험이 적용되었습니다. 보험료 ${money.display(premium)}. 동전은 떨어졌습니다.")
            }
            is TransferResult.Failure -> player.sendMessage("보험료를 낼 수 없어 보험이 적용되지 않았습니다. ${failureMessage(result, money)}")
        }
    }

    /**
     * keepInventory를 켜면 인벤토리는 비워지지 않지만, drops 목록은 그와 상관없이 떨어진다(Paper 26.3 CraftEventFactory 확인).
     * 그래서 drops를 비우고, 동전만 인벤토리에서 빼서 drops에 넣는다.
     */
    private fun keepEverythingButCoins(event: PlayerDeathEvent) {
        event.keepInventory = true
        event.drops.clear()
        val inventory = event.player.inventory
        for (slot in 0 until inventory.size) {
            val item = inventory.getItem(slot)
            if (isCoin(item)) {
                event.drops += item!!.clone()
                inventory.setItem(slot, null)
            }
        }
    }
}
