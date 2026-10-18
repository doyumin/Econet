package dev.doyu.econet.paper.command

import dev.doyu.econet.economy.AccountManager
import dev.doyu.econet.economy.Money
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

class EcoCommand(
    private val accountManager: AccountManager
) : CommandExecutor {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (args.size != 3 || !args[0].equals("give", ignoreCase = true)) {
            sender.sendMessage("§c사용법: /eco give <플레이어> <금액>")
            return true
        }

        val player = Bukkit.getOfflinePlayerIfCached(args[1])

        if (player == null) {
            sender.sendMessage("§c해당 플레이어를 찾을 수 없습니다.")
            return true
        }

        val amount = args[2].toLongOrNull()

        if (amount == null || amount <= 0) {
            sender.sendMessage("§c금액은 0보다 큰 정수여야 합니다.")
            return true
        }

        val account = accountManager.getAccountByOwner(player.uniqueId)

        try {
            accountManager.deposit(account, Money(amount))
        } catch (e: ArithmeticException) {
            sender.sendMessage("§c잔액 한도를 초과하여 지급할 수 없습니다.")
            return true
        }

        sender.sendMessage("§a${player.name ?: args[1]}님에게 ${Money(amount)} 지급 완료.")
        return true
    }
}