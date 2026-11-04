package dev.doyu.econet.paper.integrity

import dev.doyu.econet.paper.command.ADMIN_PERMISSION
import dev.doyu.econet.paper.storage.SqliteLedger
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

/** 검사 실패 경고를 놓친 관리자도 접속하자마자 알 수 있게 한다. */
class LockNotice(private val ledger: SqliteLedger) : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (ledger.isLocked && event.player.hasPermission(ADMIN_PERMISSION)) {
            event.player.sendMessage("장부가 잠겨 있습니다. /econet verify 로 확인하고 /econet repair 로 복구하세요.")
        }
    }
}
