package dev.doyu.econet.paper.command

import dev.doyu.econet.core.AccountId
import dev.doyu.econet.core.Balance
import dev.doyu.econet.paper.admin.CensusResult
import dev.doyu.econet.paper.admin.CoinCensus
import dev.doyu.econet.paper.admin.InventoryInspector
import dev.doyu.econet.paper.admin.SnapshotBackup
import dev.doyu.econet.paper.cash.CashDesk
import dev.doyu.econet.paper.format.MoneyFormat
import dev.doyu.econet.paper.format.parseAmount
import dev.doyu.econet.paper.integrity.IntegrityWatch
import dev.doyu.econet.paper.storage.SqliteLedger
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bukkit.Server
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.logging.Level
import java.util.logging.Logger

/** 관리자용 장부 점검 명령어. 하위 명령어는 영어와 한글 둘 다 받는다. */
class EconetCommand(
    private val ledger: SqliteLedger,
    private val integrity: IntegrityWatch,
    private val cashDesk: CashDesk,
    private val inspector: InventoryInspector,
    private val census: CoinCensus,
    private val backup: SnapshotBackup,
    private val money: MoneyFormat,
    private val scope: CoroutineScope,
    private val server: Server,
    private val logger: Logger,
) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val sender = source.sender
        when (args.firstOrNull()) {
            "status", "상태" -> scope.launch { showStatus(sender) }
            "verify", "검사" -> scope.launch { verify(sender) }
            "repair", "복구" -> scope.launch { repair(sender) }
            // 동전을 손에 쥐여 줘야 하므로 메인 스레드에서 바로 처리한다.
            "coins", "현금" -> issueCoins(sender, args.getOrNull(1))
            // 아래는 월드·인벤토리를 읽으므로 메인 스레드에서 바로 처리한다.
            "inv", "인벤토리" -> inspect(sender, args.getOrNull(1), inspector::openInventory)
            "ender", "엔더상자" -> inspect(sender, args.getOrNull(1), inspector::openEnderChest)
            "census", "동전집계" -> {
                val result = census.count()
                scope.launch { compareCoins(sender, result) }
            }
            "backup", "백업" -> runBackup(sender)
            else -> sender.sendMessage("사용법: /econet <status|verify|repair|coins|inv|ender|census|backup>")
        }
    }

    override fun suggest(source: CommandSourceStack, args: Array<String>): Collection<String> = when {
        args.size <= 1 -> SUBCOMMANDS.filter { it.startsWith(args.firstOrNull().orEmpty()) }
        args.size == 2 && args[0] in PLAYER_SUBCOMMANDS -> server.onlinePlayerNames(args[1])
        else -> emptyList()
    }

    override fun permission(): String = ADMIN_PERMISSION

    private suspend fun showStatus(sender: CommandSender) {
        // Issuance 잔액의 부호를 뒤집은 값이 서버 전체 통화량이다.
        val supply = -ledger.balanceOf(AccountId.Issuance).value
        sender.sendMessage("정부 금고: ${money.display(ledger.balanceOf(AccountId.Government))}")
        sender.sendMessage("총 통화량: ${money.display(supply)}")
        sender.sendMessage("현금 유통량: ${money.display(ledger.balanceOf(AccountId.CashPool))}")
        sender.sendMessage(if (ledger.isLocked) "거래 상태: 잠김 (복구 필요)" else "거래 상태: 정상")
    }

    private suspend fun verify(sender: CommandSender) {
        val report = integrity.check()
        if (report.isIntact) {
            sender.sendMessage("장부 이상 없음.")
            return
        }
        sender.sendMessage("장부 불일치 ${report.mismatches.size}건 (잔액 합 ${report.totalBalance}):")
        for (mismatch in report.mismatches.take(MAX_LISTED_MISMATCHES)) {
            sender.sendMessage(" - ${describe(mismatch.account)}: 저장 ${money.plain(mismatch.stored)}, 장부 ${money.plain(mismatch.replayed)}, 차이 ${difference(mismatch.stored, mismatch.replayed)}")
        }
        if (report.mismatches.size > MAX_LISTED_MISMATCHES) sender.sendMessage(" ... 나머지는 서버 로그를 확인하세요.")
    }

    private suspend fun repair(sender: CommandSender) {
        val report = ledger.repair()
        logger.warning("${sender.name} repaired the balance table from the ledger. Intact: ${report.isIntact}")
        sender.sendMessage(if (report.isIntact) "잔액을 장부 기준으로 복구하고 거래 잠금을 풀었습니다." else "복구 후에도 불일치가 남아 있습니다. 서버 로그를 확인하세요.")
    }

    private fun inspect(sender: CommandSender, targetName: String?, open: (Player, String) -> Unit) {
        if (sender !is Player) {
            sender.sendMessage("화면을 열 수 있는 플레이어만 쓸 수 있습니다.")
            return
        }
        if (targetName == null) {
            sender.sendMessage("사용법: /econet <inv|ender> <플레이어>")
            return
        }
        open(sender, targetName)
    }

    private suspend fun compareCoins(sender: CommandSender, result: CensusResult) {
        val pool = ledger.balanceOf(AccountId.CashPool)
        sender.sendMessage("플레이어 ${result.playersCounted}명의 인벤토리·엔더상자 동전: ${money.plain(result.counted)}")
        sender.sendMessage("현금 유통 계좌: ${money.plain(pool)}, 차이: ${pool.value.toBigInteger() - result.counted.toBigInteger()}")
        sender.sendMessage("월드 상자에 둔 동전은 세지 않으므로 차이는 참고용입니다. 차이가 음수면 장부보다 동전이 많다는 뜻입니다.")
        if (result.unreadablePlayers > 0) sender.sendMessage("저장 파일을 다 읽지 못한 플레이어 ${result.unreadablePlayers}명이 있습니다. 서버 로그를 확인하세요.")
    }

    private fun runBackup(sender: CommandSender) {
        val target = try {
            backup.run()
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Backup failed", e)
            sender.sendMessage("백업 실패: ${e.message}")
            return
        }
        sender.sendMessage("플레이어 데이터와 장부를 함께 백업했습니다: ${target.fileName}")
    }

    /** 이벤트용 동전은 관리자 개인 예금이 아니라 정부 금고에서 꺼낸다(DESIGN 2장). */
    private fun issueCoins(sender: CommandSender, amountText: String?) {
        if (sender !is Player) {
            sender.sendMessage("동전을 받을 플레이어만 쓸 수 있습니다.")
            return
        }
        val amount = amountText?.let(::parseAmount)
        if (amount == null) {
            sender.sendMessage("사용법: /econet coins <금액>  (정부 금고에서 꺼내 동전으로 받습니다)")
            return
        }
        cashDesk.withdraw(sender, amount, AccountId.Government, sender.asAdminActor())
        logger.info("${sender.name} issued ${money.plain(amount)} in coins from the treasury")
    }

    private fun describe(account: AccountId): String = when (account) {
        is AccountId.Player -> server.getOfflinePlayer(account.uuid).name ?: account.uuid.toString()
        AccountId.Government -> "정부 금고"
        AccountId.Issuance -> "발행 계좌"
        AccountId.CashPool -> "현금 유통 계좌"
    }

    private fun difference(stored: Balance, replayed: Balance): String =
        (stored.value.toBigInteger() - replayed.value.toBigInteger()).toString()

    private companion object {
        const val MAX_LISTED_MISMATCHES = 10
        val PLAYER_SUBCOMMANDS = setOf("inv", "ender", "인벤토리", "엔더상자")
        val SUBCOMMANDS = listOf("status", "verify", "repair", "coins", "inv", "ender", "census", "backup", "상태", "검사", "복구", "현금", "인벤토리", "엔더상자", "동전집계", "백업")
    }
}
