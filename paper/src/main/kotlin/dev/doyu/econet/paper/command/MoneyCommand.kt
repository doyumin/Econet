package dev.doyu.econet.paper.command

import dev.doyu.econet.economy.AccountManager
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class MoneyCommand(
    private val accountManager: AccountManager
) : CommandExecutor {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("이 명령어는 플레이어만 사용할 수 있습니다.")
            return true
        }

        val account = accountManager.getAccountByOwner(sender.uniqueId)

        sender.sendMessage("당신의 계좌 잔액: ${account.balance}")
        return true
    }
}