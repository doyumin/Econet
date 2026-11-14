package dev.doyu.econet.paper.integrity

import dev.doyu.econet.core.IntegrityReport
import dev.doyu.econet.paper.storage.SqliteLedger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Duration
import java.util.logging.Level
import java.util.logging.Logger

/** 서버를 자주 재시작하지 않으므로 시작 시 검사만으로는 부족해, 주기적으로도 검사한다. */
class IntegrityWatch(
    private val ledger: SqliteLedger,
    private val logger: Logger,
    private val alertAdmins: (String) -> Unit,
) {
    /**
     * 검사 결과가 불일치이거나 장부를 읽지 못하면 거래가 잠기고 관리자에게 알린다.
     * 읽지 못한 경우는 보고서를 만들 수 없으므로 예외를 그대로 던진다. 취소는 실패가 아니니 알리지 않는다.
     */
    suspend fun check(): IntegrityReport {
        val report = try {
            ledger.checkIntegrity()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.log(Level.SEVERE, "Ledger integrity check could not read the ledger. All transfers are locked.", e)
            alertAdmins("장부 무결성 검사를 하지 못했고 모든 거래가 잠겼습니다. 장부 파일이 손상되었을 수 있습니다. 서버 로그를 확인하세요.")
            throw e
        }
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
            // 한 번 실패했다고 반복을 끝내면 이후 검사가 조용히 사라진다. check가 이미 잠그고 알렸으니 다음 주기에 다시 시도한다.
            try {
                check()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }
}
