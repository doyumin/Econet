package dev.doyu.econet.core

sealed interface TransferResult {
    data class Success(val transaction: Transaction) : TransferResult

    sealed interface Failure : TransferResult {
        data class InsufficientFunds(val balance: Balance, val required: Amount) : Failure

        data object SameAccount : Failure

        data object Overflow : Failure

        data object InvalidRoute : Failure

        /** 무결성 검사가 실패한 장부 위에 거래를 더 쌓으면 복구가 어려워지므로, 복구 전까지 모든 거래를 막는다. */
        data object Locked : Failure
    }
}
