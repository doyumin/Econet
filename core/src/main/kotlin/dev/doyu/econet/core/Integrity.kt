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
 * 저장된 잔액은 캐시일 뿐이고 진실은 거래 기록이므로, 기록을 처음부터 재생한 결과와 비교한다.
 * 거래 기록은 Sequence로 받는다. 저장소 구현이 장부 전체를 메모리에 올리지 않고 흘려보낼 수 있게 하기 위해서다.
 */
fun integrityReportOf(stored: Map<AccountId, Balance>, transactions: Sequence<Transaction>): IntegrityReport {
    val replayed = replayBalances(transactions)
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

/** 복구는 잔액 테이블을 이 결과로 덮어쓰는 것이므로, 검사와 복구가 같은 재생 규칙을 쓰도록 공개한다. */
fun replayBalances(transactions: Sequence<Transaction>): Map<AccountId, Balance> {
    val replayed = mutableMapOf<AccountId, Balance>()
    for (tx in transactions) {
        replayed[tx.from] = (replayed[tx.from] ?: Balance.ZERO) - tx.amount
        replayed[tx.to] = (replayed[tx.to] ?: Balance.ZERO) + tx.amount
    }
    return replayed
}

/**
 * 잔액과 기록을 따로 읽으므로, 검사 도중 송금이 끼어들면 두 값이 서로 다른 시점을 가리킬 수 있다.
 * 송금이 없을 때 호출하거나, 한 시점에 묶어 검사할 수 있는 구현은 그쪽 검사를 쓴다.
 */
suspend fun Ledger.verifyIntegrity(): IntegrityReport =
    integrityReportOf(allBalances(), allTransactions().asSequence())
