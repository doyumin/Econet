package dev.doyu.econet.core

import java.time.Instant

/** 장부에 기록된 순번. 재생 순서가 곧 이 순서다. */
@JvmInline
value class TransactionId(val value: Long) : Comparable<TransactionId> {
    override fun compareTo(other: TransactionId): Int = value.compareTo(other.value)

    override fun toString(): String = value.toString()
}

data class Transaction(
    val id: TransactionId,
    val from: AccountId,
    val to: AccountId,
    val amount: Amount,
    val kind: TxKind,
    val actor: Actor,
    val memo: String?,
    val at: Instant,
)
