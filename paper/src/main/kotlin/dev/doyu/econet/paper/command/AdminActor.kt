package dev.doyu.econet.paper.command

import dev.doyu.econet.core.Actor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** 관리자 개입도 누가 했는지 장부에 남아야 하므로, 콘솔에서 실행했다면 그 사실을 주체로 남긴다. */
fun CommandSender.asAdminActor(): Actor = when (this) {
    is Player -> Actor.Admin(uniqueId)
    else -> Actor.System("console:$name")
}
