package dev.doyu.econet.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

/** 테스트·개발용 장부. 영속성은 없다. */
class InMemoryLedger(private val clock: Clock) : Ledger {
    // 잔액 확인과 갱신 사이에 다른 송금이 끼어들면 잔액 부족 검사가 무의미해지므로, 읽기까지 포함해 한 줄로 세운다.
    private val mutex = Mutex()
    private val balances = mutableMapOf<AccountId, Balance>()
    private val transactions = mutableListOf<Transaction>()

    override suspend fun transfer(
        from: AccountId,
        to: AccountId,
        amount: Amount,
        kind: TxKind,
        actor: Actor,
        memo: String?,
    ): TransferResult = mutex.withLock {
        val decision = decideTransfer(from, to, amount, kind) { balances[it] ?: Balance.ZERO }
        val (newFromBalance, newToBalance) = when (decision) {
            is TransferDecision.Rejected -> return decision.failure
            is TransferDecision.Approved -> decision
        }

        val transaction = Transaction(
            id = TransactionId(transactions.size + 1L),
            from = from,
            to = to,
            amount = amount,
            kind = kind,
            actor = actor,
            memo = memo,
            at = clock.instant(),
        )
        balances[from] = newFromBalance
        balances[to] = newToBalance
        transactions += transaction
        TransferResult.Success(transaction)
    }

    override suspend fun balanceOf(id: AccountId): Balance = mutex.withLock {
        balances[id] ?: Balance.ZERO
    }

    override suspend fun history(id: AccountId, limit: Int): List<Transaction> {
        require(limit >= 0) { "limit must not be negative: $limit" }
        return mutex.withLock {
            transactions.asReversed()
                .asSequence()
                .filter { it.from == id || it.to == id }
                .take(limit)
                .toList()
        }
    }

    override suspend fun allBalances(): Map<AccountId, Balance> = mutex.withLock {
        balances.toMap()
    }

    override suspend fun allTransactions(): List<Transaction> = mutex.withLock {
        transactions.toList()
    }
}
