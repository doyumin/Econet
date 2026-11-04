package dev.doyu.econet.paper.bounty

import dev.doyu.econet.core.AccountId
import dev.doyu.econet.core.Actor
import dev.doyu.econet.core.Amount
import dev.doyu.econet.core.Ledger
import dev.doyu.econet.core.TransferResult
import dev.doyu.econet.core.TxKind
import dev.doyu.econet.paper.command.failureMessage
import dev.doyu.econet.paper.config.RAID_CAPTAIN
import dev.doyu.econet.paper.format.MoneyFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Raider
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent

/** 보스급 몹을 처치한 플레이어에게 정부 금고에서 현상금을 준다. 아이템이 오가지 않으므로 비동기로 처리한다. */
class BountyOffice(
    private val ledger: Ledger,
    private val rewards: Map<String, Amount>,
    private val money: MoneyFormat,
    private val scope: CoroutineScope,
    private val alertAdmins: (String) -> Unit,
) : Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: EntityDeathEvent) {
        val killer = event.entity.killer ?: return
        val target = bountyKeyOf(event.entity)
        val reward = rewards[target] ?: return

        scope.launch {
            val result = ledger.transfer(AccountId.Government, AccountId.Player(killer.uniqueId), reward, TxKind.Reward, Actor.System("bounty"), "bounty $target")
            when (result) {
                is TransferResult.Success -> killer.sendMessage("현상금 ${money.display(reward)}을 받았습니다.")
                is TransferResult.Failure -> {
                    killer.sendMessage("현상금을 지급하지 못했습니다. ${failureMessage(result, money)}")
                    alertAdmins("${killer.name}님의 현상금(${money.plain(reward)}, $target) 지급 실패: ${failureMessage(result, money)}")
                }
            }
        }
    }

    /** 습격 대장은 따로 정해진 엔티티 종류가 아니라 깃발을 든 약탈자이므로, 종류와 별개의 키로 구분한다. */
    private fun bountyKeyOf(entity: LivingEntity): String =
        if (entity is Raider && entity.isPatrolLeader) RAID_CAPTAIN else entity.type.key.toString()
}
