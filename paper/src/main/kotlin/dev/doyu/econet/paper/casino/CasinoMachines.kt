package dev.doyu.econet.paper.casino

import org.bukkit.block.Block
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

data class MachineSpot(val world: String, val x: Int, val y: Int, val z: Int) {
    override fun toString(): String = "$world $x $y $z"
}

fun Block.toSpot(): MachineSpot = MachineSpot(world.name, x, y, z)

/**
 * 도박 기계로 등록된 블록 목록. 돈이 아니라 설정이라서 장부가 아니라 파일에 둔다. 메인 스레드에서만 쓴다.
 * 게임 종류를 함께 저장해 두어, 다른 게임이 생겨도 파일 형식을 바꾸지 않는다.
 */
class CasinoMachines(private val file: File) {
    private val spots: MutableMap<MachineSpot, String> = load()

    fun gameAt(spot: MachineSpot): String? = spots[spot]

    /** 이미 등록된 자리면 false. */
    fun add(spot: MachineSpot, game: String): Boolean {
        if (spot in spots) return false
        spots[spot] = game
        save()
        return true
    }

    fun remove(spot: MachineSpot): Boolean {
        if (spots.remove(spot) == null) return false
        save()
        return true
    }

    fun all(): Map<MachineSpot, String> = spots.toMap()

    private fun load(): MutableMap<MachineSpot, String> {
        if (!file.exists()) return linkedMapOf()
        val result = linkedMapOf<MachineSpot, String>()
        for (entry in YamlConfiguration.loadConfiguration(file).getMapList("machines")) {
            val spot = MachineSpot(entry["world"] as String, (entry["x"] as Number).toInt(), (entry["y"] as Number).toInt(), (entry["z"] as Number).toInt())
            result[spot] = entry["game"] as String
        }
        return result
    }

    private fun save() {
        val yaml = YamlConfiguration()
        yaml.set("machines", spots.map { (spot, game) -> mapOf("game" to game, "world" to spot.world, "x" to spot.x, "y" to spot.y, "z" to spot.z) })
        yaml.save(file)
    }

    companion object {
        const val COIN_FLIP = "coinflip"
    }
}
