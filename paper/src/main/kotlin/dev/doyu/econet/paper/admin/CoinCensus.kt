package dev.doyu.econet.paper.admin

import dev.doyu.econet.paper.cash.CoinItems
import dev.doyu.econet.paper.cash.totalValue
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.Server
import org.bukkit.inventory.ItemStack
import java.util.logging.Level
import java.util.logging.Logger

data class CensusResult(
    val counted: Long,
    val playersCounted: Int,
    val unreadablePlayers: Int,
)

/**
 * 모든 플레이어의 인벤토리와 엔더상자에 든 동전을 센다. 셜커 상자와 꾸러미 안까지 센다.
 * 월드의 상자는 세지 않으므로 CashPool과의 차이는 참고용이다(DESIGN 9장).
 * 개인별 보유량은 보여 주지 않는다. 손으로 건넨 현금은 기록되지 않는다는 성격(DESIGN 6장)을 평상시에 해치지 않기 위해서다.
 */
class CoinCensus(
    private val server: Server,
    private val coins: CoinItems,
    private val offline: OfflinePlayerItems,
    private val logger: Logger,
) {
    /** 접속 중인 플레이어는 파일보다 최신인 실제 인벤토리를 센다. 메인 스레드에서 호출한다. */
    fun count(): CensusResult {
        var total = 0L
        var counted = 0
        var unreadable = 0
        val online = server.onlinePlayers.associateBy { it.uniqueId }
        for (player in online.values) {
            total = Math.addExact(total, valueIn(player.inventory.contents.toList() + player.enderChest.contents.toList()))
            counted++
        }
        for (id in offline.knownPlayerIds() - online.keys) {
            try {
                val items = offline.read(id) ?: continue
                total = Math.addExact(total, valueIn(items.inventory.values + items.enderChest.values + items.equipment.values))
                counted++
                if (items.unreadable > 0) unreadable++
            } catch (e: Exception) {
                unreadable++
                logger.log(Level.WARNING, "Could not read player data $id for the coin census", e)
            }
        }
        return CensusResult(total, counted, unreadable)
    }

    private fun valueIn(items: List<ItemStack?>, depth: Int = 0): Long {
        var value = 0L
        for (item in items) {
            if (item == null || item.isEmpty) continue
            val denomination = coins.denominationOf(item)
            if (denomination != null) value = Math.addExact(value, totalValue(mapOf(denomination to item.amount.toLong())))
            // 꾸러미는 꾸러미를 담을 수 있으므로 깊이를 제한한다.
            if (depth < MAX_NESTING) value = Math.addExact(value, Math.multiplyExact(valueIn(nestedContents(item), depth + 1), item.amount.toLong()))
        }
        return value
    }

    private fun nestedContents(item: ItemStack): List<ItemStack> =
        item.getData(DataComponentTypes.CONTAINER)?.contents().orEmpty() +
            item.getData(DataComponentTypes.BUNDLE_CONTENTS)?.contents().orEmpty()

    private companion object {
        const val MAX_NESTING = 4
    }
}
