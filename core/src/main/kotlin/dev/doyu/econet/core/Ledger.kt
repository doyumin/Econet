package dev.doyu.econet.core

/**
 * 돈이 오가는 유일한 통로. 저장소(메모리, SQLite 등)를 갈아 끼울 경계라서 인터페이스로 둔다.
 * 잔액을 직접 바꾸는 API는 일부러 없다. 모든 변화는 transfer로만 일어나야 합이 0으로 유지된다.
 */
interface Ledger {
    suspend fun transfer(
        from: AccountId,
        to: AccountId,
        amount: Amount,
        kind: TxKind,
        actor: Actor,
        memo: String? = null,
    ): TransferResult

    /** 거래한 적 없는 계좌는 잔액 0이다. 계좌를 따로 개설하는 절차는 없다. */
    suspend fun balanceOf(id: AccountId): Balance

    /** 해당 계좌가 보내거나 받은 거래를 최신순으로 최대 [limit]개. */
    suspend fun history(id: AccountId, limit: Int): List<Transaction>

    suspend fun allBalances(): Map<AccountId, Balance>

    /** 모든 거래를 기록된 순서(오래된 것부터)로. 무결성 검사에서 재생할 때 쓴다. */
    suspend fun allTransactions(): List<Transaction>
}
