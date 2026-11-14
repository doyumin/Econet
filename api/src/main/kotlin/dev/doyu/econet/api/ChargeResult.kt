package dev.doyu.econet.api

/** 예상 가능한 실패는 예외가 아니라 값이다. 호출한 쪽이 `when`으로 빠짐없이 처리한다. */
sealed interface ChargeResult {
    data class Success(val transactionId: Long) : ChargeResult

    /** [balance]는 모자란 쪽 계좌의 잔액이다. charge면 플레이어, reward면 정부 금고. */
    data class InsufficientFunds(val balance: Long, val required: Long) : ChargeResult

    /** 금액이 0 이하이다. */
    data object InvalidAmount : ChargeResult

    /** 장부 무결성 검사가 실패해 거래가 잠겨 있다. 관리자가 복구할 때까지 풀리지 않는다. */
    data object Locked : ChargeResult

    /** 금액이 너무 커서 잔액이 넘친다. */
    data object Overflow : ChargeResult

    /** 플레이어와 정부 사이 거래에서는 나올 수 없는 사유. 나왔다면 Econet 쪽 버그다. */
    data object Rejected : ChargeResult
}
