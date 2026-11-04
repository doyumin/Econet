package dev.doyu.econet.paper.command

import org.bukkit.OfflinePlayer
import org.bukkit.Server

/**
 * 이름으로 계좌 주인을 찾는다. 접속한 적 없는 이름은 거절한다.
 * Server.getOfflinePlayer(name)는 모르는 이름에도 가짜 UUID를 만들어 주므로, 오타 낸 이름으로 돈이 사라지는 것을 막기 위해 캐시된 플레이어만 쓴다.
 */
fun Server.findKnownPlayer(name: String): OfflinePlayer? =
    getPlayerExact(name) ?: getOfflinePlayerIfCached(name)?.takeIf { it.hasPlayedBefore() }

fun Server.onlinePlayerNames(prefix: String): List<String> =
    onlinePlayers.map { it.name }.filter { it.startsWith(prefix, ignoreCase = true) }
