package dev.doyu.econet.paper.command

import dev.doyu.econet.core.AccountId
import dev.doyu.econet.core.Actor
import dev.doyu.econet.core.Ledger
import dev.doyu.econet.core.TransferResult
import dev.doyu.econet.core.TxKind
import dev.doyu.econet.paper.format.MoneyFormat
import dev.doyu.econet.paper.format.parseAmount
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bukkit.Server
import org.bukkit.entity.Player

class PayCommand(
    private val ledger: Ledger,
    private val money: MoneyFormat,
    private val scope: CoroutineScope,
    private val server: Server,
) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val sender = source.sender as? Player
        if (sender == null) {
            source.sender.sendMessage("플레이어만 송금할 수 있습니다.")
            return
        }
        if (args.size != 2) {
            sender.sendMessage("사용법: /pay <플레이어> <금액>")
            return
        }
        val target = server.findKnownPlayer(args[0])
        if (target == null) {
            sender.sendMessage("${args[0]}님은 이 서버에 접속한 적이 없습니다.")
            return
        }
        val amount = parseAmount(args[1])
        if (amount == null) {
            sender.sendMessage("금액은 1 이상의 정수여야 합니다.")
            return
        }

        scope.launch {
            val result = ledger.transfer(
                from = AccountId.Player(sender.uniqueId),
                to = AccountId.Player(target.uniqueId),
                amount = amount,
                kind = TxKind.Transfer,
                actor = Actor.Player(sender.uniqueId),
            )
            when (result) {
                is TransferResult.Success -> {
                    val remaining = ledger.balanceOf(AccountId.Player(sender.uniqueId))
                    sender.sendMessage("${target.name}님에게 ${money.display(amount)}을 보냈습니다. 남은 잔액: ${money.display(remaining)}")
                    target.player?.sendMessage("${sender.name}님에게서 ${money.display(amount)}을 받았습니다.")
                }
                is TransferResult.Failure -> sender.sendMessage(failureMessage(result, money))
            }
        }
    }

    override fun suggest(source: CommandSourceStack, args: Array<String>): Collection<String> =
        if (args.size <= 1) server.onlinePlayerNames(args.firstOrNull().orEmpty()) else emptyList()
}
