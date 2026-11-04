package dev.doyu.econet.core

/**
 * 계좌 잔액. 발행 계좌가 음수가 될 수 있으므로 부호가 있다.
 * 오버플로가 나면 돈이 생기거나 사라지므로 조용히 넘기지 않고 ArithmeticException을 던진다.
 */
@JvmInline
value class Balance(val value: Long) : Comparable<Balance> {
    operator fun plus(amount: Amount): Balance = Balance(Math.addExact(value, amount.value))

    operator fun minus(amount: Amount): Balance = Balance(Math.subtractExact(value, amount.value))

    fun covers(amount: Amount): Boolean = value >= amount.value

    override fun compareTo(other: Balance): Int = value.compareTo(other.value)

    override fun toString(): String = value.toString()

    companion object {
        val ZERO = Balance(0)
    }
}
