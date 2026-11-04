package dev.doyu.econet.paper.command

import dev.doyu.econet.core.AccountId
import dev.doyu.econet.core.Ledger
import dev.doyu.econet.paper.format.MoneyFormat
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bukkit.Server
import org.bukkit.entity.Player

/** 자기 잔액은 누구나, 남의 잔액은 관리자만 본다. 예금은 사생활이기 때문이다. */
class BalanceCommand(
    private val ledger: Ledger,
    private val money: MoneyFormat,
    private val scope: CoroutineScope,
    private val server: Server,
) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val sender = source.sender
        if (args.isEmpty()) {
            if (sender !is Player) {
                sender.sendMessage("사용법: /balance <플레이어>")
                return
            }
            scope.launch {
                sender.sendMessage("잔액: ${money.display(ledger.balanceOf(AccountId.Player(sender.uniqueId)))}")
            }
            return
        }
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage("다른 플레이어의 잔액은 관리자만 볼 수 있습니다.")
            return
        }
        val target = server.findKnownPlayer(args[0])
        if (target == null) {
            sender.sendMessage("${args[0]}님은 이 서버에 접속한 적이 없습니다.")
            return
        }
        scope.launch {
            sender.sendMessage("${target.name}님의 잔액: ${money.display(ledger.balanceOf(AccountId.Player(target.uniqueId)))}")
        }
    }

    override fun suggest(source: CommandSourceStack, args: Array<String>): Collection<String> =
        if (args.size <= 1 && source.sender.hasPermission(ADMIN_PERMISSION)) {
            server.onlinePlayerNames(args.firstOrNull().orEmpty())
        } else {
            emptyList()
        }
}
