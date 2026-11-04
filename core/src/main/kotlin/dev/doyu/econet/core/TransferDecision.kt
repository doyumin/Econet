package dev.doyu.econet.core

/** Ledger 구현마다 저장 방식은 달라도 규칙과 검사 순서는 같아야 하므로, 판정만 한 곳에 둔다. */
sealed interface TransferDecision {
    data class Approved(val newFromBalance: Balance, val newToBalance: Balance) : TransferDecision

    data class Rejected(val failure: TransferResult.Failure) : TransferDecision
}

fun decideTransfer(
    from: AccountId,
    to: AccountId,
    amount: Amount,
    kind: TxKind,
    currentBalance: (AccountId) -> Balance,
): TransferDecision {
    if (from == to) return TransferDecision.Rejected(TransferResult.Failure.SameAccount)
    if (!kind.permits(from, to)) return TransferDecision.Rejected(TransferResult.Failure.InvalidRoute)

    val fromBalance = currentBalance(from)
    if (!from.allowsNegativeBalance && !fromBalance.covers(amount)) {
        return TransferDecision.Rejected(TransferResult.Failure.InsufficientFunds(fromBalance, amount))
    }

    return try {
        TransferDecision.Approved(fromBalance - amount, currentBalance(to) + amount)
    } catch (e: ArithmeticException) {
        TransferDecision.Rejected(TransferResult.Failure.Overflow)
    }
}
