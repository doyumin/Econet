package dev.doyu.econet.paper.shop

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player

class ShopCommand(private val shop: GovernmentShop) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val player = source.sender as? Player
        if (player == null) {
            source.sender.sendMessage("플레이어만 상점을 열 수 있습니다.")
            return
        }
        shop.open(player)
    }
}
