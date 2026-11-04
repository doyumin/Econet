package dev.doyu.econet.paper.buyback

import dev.doyu.econet.paper.storage.SqliteLedger
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player

class SellCommand(private val ledger: SqliteLedger, private val sellBoxes: SellBoxes) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val player = source.sender as? Player
        if (player == null) {
            source.sender.sendMessage("플레이어만 판매할 수 있습니다.")
            return
        }
        // 어차피 정산이 거절되어 돌려받을 것이므로, 아이템을 넣는 수고를 미리 막는다.
        if (ledger.isLocked) {
            player.sendMessage("장부 점검 중이라 매입을 잠시 중단했습니다.")
            return
        }
        sellBoxes.open(player)
    }
}
