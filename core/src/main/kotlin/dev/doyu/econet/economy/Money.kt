package dev.doyu.econet.economy

@JvmInline
value class Money(val amount: Long) : Comparable<Money> {
    init {
        require(amount >= 0) {
            "돈은 음수가 될 수 없음: ${this.amount}"
        }
    }

    operator fun plus(other: Money): Money =
        // overflow 방지
        Money(Math.addExact(this.amount, other.amount))

    operator fun minus(other: Money): Money {
        require(amount >= other.amount) {
            "돈은 음수가 될 수 없음: ${this.amount} < ${other.amount}"
        }
        // 얘는 굳이
        return Money(this.amount - other.amount)
    }

    override fun compareTo(other: Money): Int =
        this.amount.compareTo(other.amount)

    override fun toString(): String =
        "%,d G".format(this.amount)

    companion object {
        val ZERO = Money(0L)
    }
}

val Int.coins: Money
    get() = Money(this.toLong())

val Long.coins: Money
    get() = Money(this)