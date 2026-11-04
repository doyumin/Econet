package dev.doyu.econet.paper.format

import dev.doyu.econet.core.Amount
import dev.doyu.econet.core.Balance
import dev.doyu.econet.paper.config.CurrencySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MoneyFormatTest {
    private val money = MoneyFormat(CurrencySettings(name = "듄", code = "DYN", format = "{amount}{name}"))

    @Test
    fun `display groups thousands and appends the name`() {
        assertEquals("12,500듄", money.display(Amount(12_500)))
        assertEquals("-1,000듄", money.display(Balance(-1_000)))
        assertEquals("0듄", money.display(Balance.ZERO))
    }

    @Test
    fun `plain output can be pasted back into a command`() {
        assertEquals("12500 DYN", money.plain(Amount(12_500)))
    }

    @Test
    fun `format pattern is configurable`() {
        val custom = MoneyFormat(CurrencySettings(name = "Dune", code = "DYN", format = "{code} {amount}"))

        assertEquals("DYN 1,234,567", custom.display(1_234_567))
    }

    @Test
    fun `amount parsing accepts commas and rejects everything that is not a positive whole number`() {
        assertEquals(Amount(12_500), parseAmount("12,500"))
        assertEquals(Amount(1), parseAmount("1"))
        assertNull(parseAmount("0"))
        assertNull(parseAmount("-5"))
        assertNull(parseAmount("1.5"))
        assertNull(parseAmount("abc"))
        assertNull(parseAmount(""))
        assertNull(parseAmount("99999999999999999999"))
    }
}
