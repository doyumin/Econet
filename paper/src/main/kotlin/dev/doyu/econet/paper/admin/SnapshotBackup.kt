package dev.doyu.econet.paper.admin

import dev.doyu.econet.paper.storage.SqliteLedger
import kotlinx.coroutines.runBlocking
import org.bukkit.Server
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 플레이어 데이터와 장부를 같은 시점으로 함께 백업한다. 메인 스레드에서 처음부터 끝까지 한 번에 한다.
 *
 * 둘을 따로 복구하면 장부와 실제 동전이 어긋난다(DESIGN 9장). 메인 스레드가 멈춰 있는 동안에는 인출·입금·판매처럼
 * 아이템과 돈을 함께 옮기는 처리가 끼어들 수 없으므로, 저장 → 장부 복사 → 파일 복사 사이에 둘이 어긋나지 않는다.
 * 송금처럼 돈만 움직이는 거래는 끼어들 수 있지만, 아이템과 무관하므로 둘의 일관성을 깨지 않는다.
 */
class SnapshotBackup(
    private val server: Server,
    private val ledger: SqliteLedger,
    private val offline: OfflinePlayerItems,
    private val backupRoot: File,
    private val keep: Int,
    private val clock: Clock,
) {
    fun run(): Path {
        val target = backupRoot.toPath().resolve(FOLDER_NAME.format(clock.instant().atOffset(ZoneOffset.UTC)))
        Files.createDirectories(target)

        server.savePlayers()
        runBlocking { ledger.backupTo(target.resolve("econet.db")) }
        val playerData = Files.createDirectories(target.resolve("playerdata"))
        for (file in offline.dataFolder().listFiles { file -> file.extension == "dat" }.orEmpty()) {
            Files.copy(file.toPath(), playerData.resolve(file.name))
        }

        val existing = backupRoot.listFiles { file -> file.isDirectory }.orEmpty().map { it.name }
        for (old in backupsToDelete(existing, keep)) backupRoot.resolve(old).deleteRecursively()
        return target
    }

    private companion object {
        val FOLDER_NAME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss'Z'")
    }
}
