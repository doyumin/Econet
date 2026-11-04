package dev.doyu.econet.paper.command

import dev.doyu.econet.core.AccountId
import dev.doyu.econet.core.Ledger
import dev.doyu.econet.core.TransferResult
import dev.doyu.econet.core.TxKind
import dev.doyu.econet.paper.format.MoneyFormat
import dev.doyu.econet.paper.format.parseAmount
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.logging.Logger

/** 발행은 언제나 정부 금고로만 들어간다. 플레이어에게 직접 주면 그 돈이 어떤 명목으로 풀렸는지 장부에서 사라진다. */
class MintCommand(
    private val ledger: Ledger,
    private val money: MoneyFormat,
    private val scope: CoroutineScope,
    private val logger: Logger,
) : BasicCommand {
    override fun execute(source: CommandSourceStack, args: Array<String>) {
        val sender = source.sender
        val amount = args.firstOrNull()?.let(::parseAmount)
        if (amount == null) {
            sender.sendMessage("사용법: /mint <금액> [메모]")
            return
        }
        val memo = args.drop(1).joinToString(" ").ifBlank { null }

        scope.launch {
            val result = ledger.transfer(AccountId.Issuance, AccountId.Government, amount, TxKind.Mint, sender.asAdminActor(), memo)
            when (result) {
                is TransferResult.Success -> {
                    val treasury = ledger.balanceOf(AccountId.Government)
                    logger.info("${sender.name} minted ${money.plain(amount)} (tx ${result.transaction.id}, memo: $memo)")
                    sender.sendMessage("정부 금고에 ${money.display(amount)}을 발행했습니다. 금고 잔액: ${money.display(treasury)}")
                }
                is TransferResult.Failure -> sender.sendMessage(failureMessage(result, money))
            }
        }
    }

    override fun permission(): String = ADMIN_PERMISSION
}
