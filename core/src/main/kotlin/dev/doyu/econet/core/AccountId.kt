package dev.doyu.econet.core

import java.util.UUID

sealed interface AccountId {
    data class Player(val uuid: UUID) : AccountId

    data object Government : AccountId

    /**
     * 발행 계좌. 돈을 무에서 만들지 않고 이 계좌의 잔액을 음수로 내리는 방식으로 발행하기 때문에,
     * 유일하게 음수 잔액이 허용된다. 그 결과 모든 잔액의 합은 언제나 0이고, 이 계좌 잔액의 절댓값이 총 통화량이 된다.
     */
    data object Issuance : AccountId

    /** 현금(아이템 등 계좌 밖의 돈)으로 나가 있는 금액을 장부 안에 묶어 두는 계좌. */
    data object CashPool : AccountId

    val allowsNegativeBalance: Boolean
        get() = this == Issuance
}
