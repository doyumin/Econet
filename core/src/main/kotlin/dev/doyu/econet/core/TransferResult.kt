package dev.doyu.econet.core

sealed interface TransferResult {
    data class Success(val transaction: Transaction) : TransferResult

    sealed interface Failure : TransferResult {
        data class InsufficientFunds(val balance: Balance, val required: Amount) : Failure

        data object SameAccount : Failure

        data object Overflow : Failure

        data object InvalidRoute : Failure
    }
}
