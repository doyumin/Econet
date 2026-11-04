package dev.doyu.econet.core

import java.math.BigInteger

data class BalanceMismatch(
    val account: AccountId,
    val stored: Balance,
    val replayed: Balance,
)

data class IntegrityReport(
    /** Long 합은 중간에 넘칠 수 있어서, 넘쳐도 정확한 값을 보여 주도록 BigInteger로 더한다. */
    val totalBalance: BigInteger,
    val mismatches: List<BalanceMismatch>,
) {
    val isIntact: Boolean
        get() = totalBalance == BigInteger.ZERO && mismatches.isEmpty()
}

/**
 * 서버 시작 시 장부를 믿어도 되는지 확인한다.
 * 저장된 잔액은 캐시일 뿐이고 진실은 거래 기록이므로, 기록을 처음부터 재생한 결과와 비교한다.
 *
 * 검사 도중 다른 송금이 끼어들면 잔액과 기록이 서로 다른 시점을 가리킬 수 있으므로, 송금이 없을 때 호출해야 한다.
 */
suspend fun Ledger.verifyIntegrity(): IntegrityReport {
    val stored = allBalances()
    val replayed = mutableMapOf<AccountId, Balance>()
    for (tx in allTransactions()) {
        replayed[tx.from] = (replayed[tx.from] ?: Balance.ZERO) - tx.amount
        replayed[tx.to] = (replayed[tx.to] ?: Balance.ZERO) + tx.amount
    }

    val mismatches = (stored.keys + replayed.keys).mapNotNull { account ->
        val storedBalance = stored[account] ?: Balance.ZERO
        val replayedBalance = replayed[account] ?: Balance.ZERO
        if (storedBalance == replayedBalance) null else BalanceMismatch(account, storedBalance, replayedBalance)
    }

    return IntegrityReport(
        totalBalance = stored.values.sumOf { it.value.toBigInteger() },
        mismatches = mismatches,
    )
}
