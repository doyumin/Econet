package dev.doyu.econet.paper.admin

import dev.doyu.econet.paper.storage.SqliteLedger
import kotlinx.coroutines.runBlocking
import org.bukkit.Server
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 플레이어 데이터와 장부를 같은 시점으로 함께 백업한다. 메인 스레드에서 처음부터 끝까지 한 번에 한다.
 *
 * 둘을 따로 복구하면 장부와 실제 동전이 어긋난다(DESIGN 9장). 메인 스레드가 멈춰 있는 동안에는 인출·입금·판매처럼
 * 아이템과 돈을 함께 옮기는 처리가 끼어들 수 없으므로, 저장 → 장부 복사 → 파일 복사 사이에 둘이 어긋나지 않는다.
 * 송금처럼 돈만 움직이는 거래는 끼어들 수 있지만, 아이템과 무관하므로 둘의 일관성을 깨지 않는다.
 *
 * [settleOpenSellBoxes]는 저장보다 먼저 부른다. 열린 판매 상자의 아이템은 플레이어 파일에도 장부에도 없어서,
 * 정산해 인벤토리로 돌려놓지 않으면 그 시점의 백업에서 사라진다.
 */
class SnapshotBackup(
    private val server: Server,
    private val ledger: SqliteLedger,
    private val offline: OfflinePlayerItems,
    private val backupRoot: File,
    private val keep: Int,
    private val clock: Clock,
    private val settleOpenSellBoxes: () -> Unit,
) {
    fun run(): Path {
        val name = FOLDER_NAME.format(clock.instant().atOffset(ZoneOffset.UTC))
        val target = backupRoot.toPath().resolve(name)
        // 다 쓰기 전에는 완료된 이름을 갖지 않게 한다. 중간에 실패하면 반쪽 백업이 보존 개수를 차지해 정상 백업을 밀어낸다.
        val partial = backupRoot.toPath().resolve("$name$PARTIAL_SUFFIX")

        backupRoot.listFiles { file -> file.name.endsWith(PARTIAL_SUFFIX) }.orEmpty().forEach { it.deleteRecursively() }
        try {
            Files.createDirectories(partial)
            settleOpenSellBoxes()
            server.savePlayers()
            runBlocking { ledger.backupTo(partial.resolve("econet.db")) }
            val playerData = Files.createDirectories(partial.resolve("playerdata"))
            for (file in offline.dataFolder().listFiles { file -> file.extension == "dat" }.orEmpty()) {
                Files.copy(file.toPath(), playerData.resolve(file.name))
            }
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE)
        } catch (e: Exception) {
            partial.toFile().deleteRecursively()
            throw e
        }

        val existing = backupRoot.listFiles { file -> file.isDirectory }.orEmpty().map { it.name }
        for (old in backupsToDelete(existing, keep)) backupRoot.resolve(old).deleteRecursively()
        return target
    }

    private companion object {
        val FOLDER_NAME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'")
        const val PARTIAL_SUFFIX = ".partial"
    }
}
