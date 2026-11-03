package dev.doyu.econet.paper.command

import dev.doyu.econet.economy.AccountManager
import dev.doyu.econet.economy.Money
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class PayCommand(
    private val accountManager: AccountManager
) : CommandExecutor, TabCompleter {

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

        if (args.size != 2) {
            sender.sendMessage("§c사용법: /pay <플레이어> <금액>")
            return true
        }

        val recipient = Bukkit.getOfflinePlayerIfCached(args[0])
        if (recipient == null) {
            sender.sendMessage("§c해당 플레이어를 찾을 수 없습니다.")
            return true
        }

        if (recipient.uniqueId == sender.uniqueId) {
            sender.sendMessage("§c자기 자신에게 이체할 수 없습니다.")
            return true
        }

        val amount = args[1].toLongOrNull()
        if (amount == null || amount <= 0) {
            sender.sendMessage("§c금액은 0보다 큰 정수여야 합니다.")
            return true
        }

        val from = accountManager.getAccountByOwner(sender.uniqueId)
        val to = accountManager.getAccountByOwner(recipient.uniqueId)

        try {
            if (!accountManager.transfer(from, to, Money(amount))) {
                sender.sendMessage("§c잔액이 부족합니다.")
                return true
            }
        } catch (e: ArithmeticException) {
            sender.sendMessage("§c상대방 잔액 한도를 초과하여 이체할 수 없습니다.")
            return true
        }

        val recipientName = recipient.name ?: args[0]
        sender.sendMessage("§a${recipientName}님에게 ${Money(amount)} 이체 완료.")
        Bukkit.getPlayer(recipient.uniqueId)?.sendMessage(
            "§a${sender.name}님이 ${Money(amount)}을(를) 보냈습니다."
        )
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> =
        when (args.size) {
            1 -> Bukkit.getOfflinePlayers()
                .asSequence()
                .mapNotNull { it.name }
                .filterNot { it.equals((sender as? Player)?.name, ignoreCase = true) }
                .filter { it.startsWith(args[0], ignoreCase = true) }
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
                .toList()

            2 -> listOf("100", "1000", "10000")
                .filter { it.startsWith(args[1]) }

            else -> emptyList()
        }
}
