package dev.doyu.econet.paper.command

import dev.doyu.econet.core.AccountId
import dev.doyu.econet.core.Actor
import dev.doyu.econet.paper.cash.CashDesk
import dev.doyu.econet.paper.format.parseAmount
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player

class WithdrawCommand(private val cashDesk: CashDesk) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val player = source.sender as? Player
        if (player == null) {
            source.sender.sendMessage("플레이어만 인출할 수 있습니다.")
            return
        }
        val amount = args.singleOrNull()?.let(::parseAmount)
        if (amount == null) {
            player.sendMessage("사용법: /withdraw <금액>")
            return
        }
        cashDesk.withdraw(player, amount, AccountId.Player(player.uniqueId), Actor.Player(player.uniqueId))
    }
}

class DepositCommand(private val cashDesk: CashDesk) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val player = source.sender as? Player
        if (player == null) {
            source.sender.sendMessage("플레이어만 예금할 수 있습니다.")
            return
        }
        cashDesk.deposit(player)
    }
}

class ChangeCommand(private val cashDesk: CashDesk) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val player = source.sender as? Player
        if (player == null) {
            source.sender.sendMessage("플레이어만 쓸 수 있습니다.")
            return
        }
        when (args.firstOrNull()) {
            "split", "쪼개기" -> cashDesk.split(player)
            "merge", "합치기" -> cashDesk.merge(player)
            else -> player.sendMessage("사용법: /change <split|merge> (/잔돈 <쪼개기|합치기>)")
        }
    }

    override fun suggest(source: CommandSourceStack, args: Array<String>): Collection<String> =
        if (args.size <= 1) listOf("split", "merge", "쪼개기", "합치기").filter { it.startsWith(args.firstOrNull().orEmpty()) } else emptyList()
}
