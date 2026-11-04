package dev.doyu.econet.paper.command

import dev.doyu.econet.core.Amount
import dev.doyu.econet.core.Balance
import dev.doyu.econet.core.TransferResult.Failure
import dev.doyu.econet.paper.config.CurrencySettings
import dev.doyu.econet.paper.format.MoneyFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class FailureMessagesTest {
    private val money = MoneyFormat(CurrencySettings("듄", "DYN", "{amount}{name}"))

    @Test
    fun `insufficient funds shows both amounts in the display format`() {
        assertEquals(
            "잔액이 부족합니다. 현재 1,000듄, 필요 2,500듄",
            failureMessage(Failure.InsufficientFunds(Balance(1_000), Amount(2_500)), money),
        )
    }
}
