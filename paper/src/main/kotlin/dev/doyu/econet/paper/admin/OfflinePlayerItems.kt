package dev.doyu.econet.paper.admin

import org.bukkit.Server
import org.bukkit.inventory.ItemStack
import java.io.File
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

data class OfflineItems(
    val inventory: Map<Int, ItemStack>,
    val enderChest: Map<Int, ItemStack>,
    val equipment: Map<String, ItemStack>,
    /** 읽지 못한 아이템 수. 0이 아니면 화면이 실제와 다를 수 있음을 알려야 한다. */
    val unreadable: Int,
)

/** 오프라인 플레이어의 저장 파일을 읽는다. 읽기만 한다: 파일을 고치면 접속 시 서버가 덮어써 수정이 사라지거나 아이템이 복제된다. */
class OfflinePlayerItems(private val server: Server, private val logger: Logger) {
    /**
     * Paper 26.3은 `<월드 루트>/players/data`에 둔다. 옛 버전에서 옮겨 온 서버를 위해 `playerdata`도 본다.
     * 26.3의 World.getWorldFolder()는 루트가 아니라 차원 폴더(`world/dimensions/minecraft/overworld`)를 돌려주므로,
     * level.dat가 있는 폴더까지 올라가 루트를 찾는다(실제 서버에서 확인).
     */
    fun dataFolder(): File {
        val dimensionFolder = server.worlds.first().worldFolder.absoluteFile
        val root = generateSequence(dimensionFolder) { it.parentFile }.firstOrNull { it.resolve("level.dat").isFile } ?: dimensionFolder
        return root.resolve("players/data").takeIf { it.isDirectory } ?: root.resolve("playerdata")
    }

    fun knownPlayerIds(): List<UUID> =
        dataFolder().listFiles { file -> file.extension == "dat" }.orEmpty()
            .mapNotNull { runCatching { UUID.fromString(it.nameWithoutExtension) }.getOrNull() }

    fun read(player: UUID): OfflineItems? {
        val file = dataFolder().resolve("$player.dat")
        if (!file.isFile) return null
        val stored = storedPlayerItemsOf(readCompressedNbt(file.readBytes()))
        var unreadable = 0
        fun convert(item: NbtTag.CompoundTag): ItemStack? = try {
            ItemStack.deserializeBytes(serializedItemBytes(item, stored.dataVersion))
        } catch (e: Exception) {
            unreadable++
            logger.log(Level.WARNING, "Could not read an item of $player", e)
            null
        }
        return OfflineItems(
            inventory = stored.inventory.mapNotNull { (slot, item) -> convert(item)?.let { slot to it } }.toMap(),
            enderChest = stored.enderChest.mapNotNull { (slot, item) -> convert(item)?.let { slot to it } }.toMap(),
            equipment = stored.equipment.mapNotNull { (slot, item) -> convert(item)?.let { slot to it } }.toMap(),
            unreadable = unreadable,
        )
    }
}
