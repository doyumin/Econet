package dev.doyu.econet.paper.format

import dev.doyu.econet.core.Amount
import dev.doyu.econet.core.Balance
import dev.doyu.econet.paper.config.CurrencySettings
import java.util.Locale

/** core는 화폐 이름을 모르므로, 숫자를 사람이 읽는 금액으로 바꾸는 일은 전부 여기서 한다. */
class MoneyFormat(private val currency: CurrencySettings) {
    /** 플레이어 화면용. 예: `12,500듄` */
    fun display(value: Long): String = currency.format
        .replace("{amount}", String.format(Locale.ROOT, "%,d", value))
        .replace("{name}", currency.name)
        .replace("{code}", currency.code)

    fun display(amount: Amount): String = display(amount.value)

    fun display(balance: Balance): String = display(balance.value)

    /** 로그·관리자 출력용. 쉼표 없이 그대로 복사해 명령어에 다시 넣을 수 있게 한다. 예: `12500 DYN` */
    fun plain(value: Long): String = "$value ${currency.code}"

    fun plain(amount: Amount): String = plain(amount.value)

    fun plain(balance: Balance): String = plain(balance.value)
}

/** 명령어 입력 금액. 화면에 보이는 그대로 복사해 넣어도 되도록 쉼표를 허용한다. 0 이하, 소수, Long 범위 밖은 거절한다. */
fun parseAmount(text: String): Amount? {
    val digits = text.replace(",", "")
    if (digits.isEmpty() || !digits.all { it.isDigit() }) return null
    val value = digits.toLongOrNull() ?: return null
    return if (value > 0) Amount(value) else null
}
