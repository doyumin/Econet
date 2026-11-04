package dev.doyu.econet.paper.admin

import dev.doyu.econet.paper.command.findKnownPlayer
import net.kyori.adventure.text.Component
import org.bukkit.Server
import org.bukkit.entity.Player
import java.util.UUID

/**
 * 접속 중인 플레이어는 실제 인벤토리를 그대로 연다(실시간, 수정 가능).
 * 오프라인 플레이어는 저장 파일의 사본을 읽기 전용으로 연다. 고친 내용은 접속 시 서버가 덮어써 사라지거나 아이템이 복제되기 때문이다.
 */
class InventoryInspector(private val server: Server, private val offline: OfflinePlayerItems) {
    fun openInventory(admin: Player, targetName: String) {
        val target = server.findKnownPlayer(targetName)
        if (target == null) {
            admin.sendMessage("${targetName}님은 이 서버에 접속한 적이 없습니다.")
            return
        }
        val online = target.player
        if (online != null) {
            admin.openInventory(online.inventory)
            return
        }
        val items = readOffline(admin, target.uniqueId, targetName) ?: return
        admin.openInventory(ReadOnlyView(54, Component.text("$targetName 인벤토리 (읽기 전용)"), server, ReadOnlyView.inventoryLayout(items)).inventory)
    }

    fun openEnderChest(admin: Player, targetName: String) {
        val target = server.findKnownPlayer(targetName)
        if (target == null) {
            admin.sendMessage("${targetName}님은 이 서버에 접속한 적이 없습니다.")
            return
        }
        val online = target.player
        if (online != null) {
            admin.openInventory(online.enderChest)
            return
        }
        val items = readOffline(admin, target.uniqueId, targetName) ?: return
        admin.openInventory(ReadOnlyView(27, Component.text("$targetName 엔더상자 (읽기 전용)"), server, items.enderChest).inventory)
    }

    private fun readOffline(admin: Player, id: UUID, name: String): OfflineItems? {
        val items = try {
            offline.read(id)
        } catch (e: Exception) {
            admin.sendMessage("${name}님의 저장 파일을 읽지 못했습니다: ${e.message}")
            return null
        }
        if (items == null) admin.sendMessage("${name}님의 저장 파일이 없습니다.")
        if (items != null && items.unreadable > 0) admin.sendMessage("아이템 ${items.unreadable}개를 읽지 못해 화면에서 빠졌습니다. 서버 로그를 확인하세요.")
        return items
    }
}
