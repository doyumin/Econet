package dev.doyu.econet.core

/** 송금 금액. 0원 송금이나 음수 송금은 의미가 없으므로 생성 시점에 막는다. */
@JvmInline
value class Amount(val value: Long) : Comparable<Amount> {
    init {
        require(value > 0) { "Amount must be positive: $value" }
    }

    override fun compareTo(other: Amount): Int = value.compareTo(other.value)

    override fun toString(): String = value.toString()
}
