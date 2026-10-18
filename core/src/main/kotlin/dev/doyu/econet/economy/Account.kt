package dev.doyu.econet.economy

import java.util.UUID

class Account(
    val id: UUID,
    val ownerId: UUID,
    initialBalance: Money = Money.ZERO
) {
    var balance: Money = initialBalance
        private set

    fun canWithdraw(amount: Money): Boolean =
        balance >= amount

    fun deposit(amount: Money) {
        require(amount > Money.ZERO) {
            "입금액은 양수여야 함"
        }

        balance += amount
    }

    fun withdraw(amount: Money): Boolean {
        require(amount > Money.ZERO) {
            "출금액은 양수여야 함"
        }

        if (!canWithdraw(amount)) return false

        balance -= amount
        return true
    }

    override fun toString(): String =
        "Account(id=$id, ownerId=$ownerId, balance=$balance)"

    internal fun restoreBalance(balance: Money) {
        this.balance = balance
    }
}