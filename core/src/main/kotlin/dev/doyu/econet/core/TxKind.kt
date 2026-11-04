package dev.doyu.econet.core

import dev.doyu.econet.core.AccountId.CashPool
import dev.doyu.econet.core.AccountId.Government
import dev.doyu.econet.core.AccountId.Issuance
import dev.doyu.econet.core.AccountId.Player

enum class TxKind {
    Transfer,
    Mint,
    Burn,
    CashOut,
    CashIn,
    Reward,
    Purchase,
    Sale,
    Tax;

    /**
     * 특수 계좌(Issuance, CashPool)는 정해진 종류의 거래로만 드나들 수 있다.
     * 그래야 Issuance 잔액이 곧 총 통화량이고 CashPool 잔액이 곧 현금 유통량이라는 의미가 유지된다.
     * 나머지 종류는 분류용 라벨이라 일반 계좌끼리라면 방향을 따지지 않는다.
     */
    fun permits(from: AccountId, to: AccountId): Boolean = when (this) {
        Mint -> from == Issuance && to == Government
        Burn -> from == Government && to == Issuance
        CashOut -> from.isRegular() && to == CashPool
        CashIn -> from == CashPool && to.isRegular()
        Transfer, Reward, Purchase, Sale, Tax -> from.isRegular() && to.isRegular()
    }

    private fun AccountId.isRegular(): Boolean = when (this) {
        is Player, Government -> true
        Issuance, CashPool -> false
    }
}
