package dev.doyu.econet.paper.shop

import dev.doyu.econet.paper.command.ADMIN_PERMISSION
import dev.doyu.econet.paper.format.MoneyFormat
import dev.doyu.econet.paper.format.parseAmount
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Material
import org.bukkit.command.CommandSender
import java.util.logging.Logger

/** 상점 품목을 명령어로 바꾼다. 아이템 이름은 `lead`와 `minecraft:lead` 둘 다 받는다. */
class ShopAdminCommand(
    private val catalog: ShopCatalog,
    private val money: MoneyFormat,
    private val logger: Logger,
) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val sender = source.sender
        when (args.firstOrNull()) {
            "add", "추가" -> add(sender, args.getOrNull(1), args.getOrNull(2))
            "remove", "삭제" -> remove(sender, args.getOrNull(1))
            "list", "목록" -> list(sender)
            else -> sender.sendMessage(USAGE)
        }
    }

    override fun suggest(source: CommandSourceStack, args: Array<String>): Collection<String> = when {
        args.size <= 1 -> SUBCOMMANDS.filter { it.startsWith(args.firstOrNull().orEmpty()) }
        args.size == 2 && args[0] in ADD_SUBCOMMANDS -> itemNames(args[1])
        args.size == 2 && args[0] in REMOVE_SUBCOMMANDS -> catalog.prices().keys.map { it.removePrefix("minecraft:") }.filter { it.startsWith(args[1]) }
        else -> emptyList()
    }

    override fun permission(): String = ADMIN_PERMISSION

    private fun add(sender: CommandSender, itemText: String?, priceText: String?) {
        val price = priceText?.let(::parseAmount)
        if (itemText == null || price == null) {
            sender.sendMessage("사용법: /상점관리 추가 <아이템> <가격>  (이미 있는 품목이면 가격을 바꿉니다)")
            return
        }
        val key = itemKeyOf(itemText)
        if (key == null) {
            sender.sendMessage("알 수 없는 아이템입니다: $itemText")
            return
        }
        when (val result = catalog.put(key, price)) {
            ShopPut.Added -> sender.sendMessage("상점에 $key 을(를) ${money.display(price)}에 추가했습니다.")
            is ShopPut.PriceChanged -> sender.sendMessage("$key 가격을 ${money.display(result.previous)}에서 ${money.display(price)}으로 바꿨습니다.")
            ShopPut.Full -> {
                sender.sendMessage("상점은 최대 ${ShopCatalog.MAX_ITEMS}종까지 진열합니다. 먼저 하나를 삭제하세요.")
                return
            }
        }
        logger.info("${sender.name} set shop item $key to ${money.plain(price)}")
    }

    private fun remove(sender: CommandSender, itemText: String?) {
        if (itemText == null) {
            sender.sendMessage("사용법: /상점관리 삭제 <아이템>")
            return
        }
        // 아이템 종류로 못 바꾸는 이름(오타 난 옛 품목)도 지울 수 있어야 해서, 먼저 입력 그대로 찾아본다.
        val key = listOfNotNull(itemText, itemKeyOf(itemText)).firstOrNull { it in catalog.prices() }
        if (key == null || !catalog.remove(key)) {
            sender.sendMessage("상점에 없는 품목입니다: $itemText")
            return
        }
        sender.sendMessage("상점에서 $key 을(를) 뺐습니다.")
        logger.info("${sender.name} removed shop item $key")
    }

    private fun list(sender: CommandSender) {
        val prices = catalog.prices()
        if (prices.isEmpty()) {
            sender.sendMessage("상점에 진열된 물건이 없습니다.")
            return
        }
        sender.sendMessage("상점 품목 ${prices.size}/${ShopCatalog.MAX_ITEMS}종:")
        for ((key, price) in prices) sender.sendMessage(" - $key: ${money.plain(price)}")
    }

    private fun itemKeyOf(text: String): String? =
        Material.matchMaterial(text)?.takeIf { it.isItem }?.key?.toString()

    private fun itemNames(prefix: String): List<String> =
        Material.entries.asSequence()
            .filter { it.isItem && !it.isLegacy }
            .map { it.key.key }
            .filter { it.startsWith(prefix.removePrefix("minecraft:")) }
            .take(MAX_SUGGESTIONS)
            .toList()

    private companion object {
        const val USAGE = "사용법: /상점관리 <추가|삭제|목록>"
        const val MAX_SUGGESTIONS = 30
        val ADD_SUBCOMMANDS = setOf("add", "추가")
        val REMOVE_SUBCOMMANDS = setOf("remove", "삭제")
        val SUBCOMMANDS = listOf("add", "remove", "list")
    }
}
