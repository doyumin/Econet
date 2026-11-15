package dev.doyu.econet.paper.casino

import dev.doyu.econet.paper.command.ADMIN_PERMISSION
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.entity.Player

/** 바라보는 블록을 도박 기계로 등록하거나 지운다. 건물은 직접 짓고, 이 명령은 "이 블록이 기계"라고만 알려 준다. */
class CasinoCommand(private val machines: CasinoMachines) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val sender = source.sender
        when (args.firstOrNull()) {
            "place", "설치" -> placeOrRemove(sender as? Player, place = true)
            "remove", "철거" -> placeOrRemove(sender as? Player, place = false)
            "list", "목록" -> {
                val all = machines.all()
                if (all.isEmpty()) sender.sendMessage("등록된 도박 기계가 없습니다.")
                else all.forEach { (spot, game) -> sender.sendMessage("$game  $spot") }
            }
            else -> sender.sendMessage("사용법: /카지노 설치|철거|목록  (설치·철거는 바라보는 블록 기준)")
        }
    }

    override fun suggest(source: CommandSourceStack, args: Array<String>): Collection<String> =
        if (args.size <= 1) SUBCOMMANDS.filter { it.startsWith(args.firstOrNull().orEmpty()) } else emptyList()

    override fun permission(): String = ADMIN_PERMISSION

    private fun placeOrRemove(player: Player?, place: Boolean) {
        if (player == null) return
        val block = player.getTargetBlockExact(REACH)
        if (block == null || block.type.isAir) {
            player.sendMessage("기계로 쓸 블록을 바라보고 입력해 주세요.")
            return
        }
        val spot = block.toSpot()
        if (place) {
            player.sendMessage(if (machines.add(spot, CasinoMachines.COIN_FLIP)) "동전 던지기 기계로 등록했습니다: $spot" else "이미 등록된 기계입니다.")
        } else {
            player.sendMessage(if (machines.remove(spot)) "기계 등록을 지웠습니다: $spot" else "등록된 기계가 아닙니다.")
        }
    }

    private companion object {
        val SUBCOMMANDS = listOf("설치", "철거", "목록", "place", "remove", "list")
        const val REACH = 6
    }
}
