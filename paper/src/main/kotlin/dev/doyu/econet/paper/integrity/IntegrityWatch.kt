package dev.doyu.econet.paper.integrity

import dev.doyu.econet.core.IntegrityReport
import dev.doyu.econet.paper.storage.SqliteLedger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.logging.Logger

/** 서버를 자주 재시작하지 않으므로 시작 시 검사만으로는 부족해, 주기적으로도 검사한다. */
class IntegrityWatch(
    private val ledger: SqliteLedger,
    private val logger: Logger,
    private val alertAdmins: (String) -> Unit,
) {
    suspend fun check(): IntegrityReport {
        val report = ledger.checkIntegrity()
        if (report.isIntact) return report

        logger.severe("Ledger integrity check failed. All transfers are locked until /econet repair.")
        logger.severe("Total balance: ${report.totalBalance}, mismatched accounts: ${report.mismatches.size}")
        for (mismatch in report.mismatches) {
            logger.severe("  ${mismatch.account}: stored ${mismatch.stored}, replayed ${mismatch.replayed}")
        }
        alertAdmins("장부 무결성 검사 실패: 모든 거래가 잠겼습니다. /econet verify 로 차이를 확인하고 /econet repair 로 복구하세요.")
        return report
    }

    fun runEvery(interval: Duration, scope: CoroutineScope): Job = scope.launch {
        while (isActive) {
            delay(interval.toMillis())
            check()
        }
    }
}
