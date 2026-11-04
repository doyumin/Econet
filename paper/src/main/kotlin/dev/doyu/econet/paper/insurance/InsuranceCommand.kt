package dev.doyu.econet.paper.insurance

import dev.doyu.econet.core.Amount
import dev.doyu.econet.paper.format.MoneyFormat
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player

class InsuranceCommand(
    private val policies: InsurancePolicies,
    private val premium: Amount,
    private val money: MoneyFormat,
) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val player = source.sender as? Player
        if (player == null) {
            source.sender.sendMessage("플레이어만 보험에 가입할 수 있습니다.")
            return
        }
        when (args.firstOrNull()) {
            "join", "가입" -> {
                policies.enroll(player.uniqueId)
                player.sendMessage("사망 보험에 가입했습니다. 죽을 때마다 ${money.display(premium)}이 예금에서 빠지고 인벤토리를 지킵니다. 동전은 떨어집니다.")
            }
            "leave", "해지" -> {
                policies.cancel(player.uniqueId)
                player.sendMessage("사망 보험을 해지했습니다.")
            }
            else -> {
                val state = if (policies.isEnrolled(player.uniqueId)) "가입 중" else "미가입"
                player.sendMessage("사망 보험: $state, 보험료 ${money.display(premium)} (사망 시). /insurance <join|leave>")
            }
        }
    }

    override fun suggest(source: CommandSourceStack, args: Array<String>): Collection<String> =
        if (args.size <= 1) listOf("join", "leave", "가입", "해지").filter { it.startsWith(args.firstOrNull().orEmpty()) } else emptyList()
}
