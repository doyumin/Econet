package dev.doyu.econet.paper.insurance

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

/** 보험 가입자 명단. 돈이 아니라 약정이라서 장부가 아니라 파일에 둔다. 메인 스레드에서만 쓴다. */
class InsurancePolicies(private val file: File) {
    private val enrolled: MutableSet<UUID> = load()

    fun isEnrolled(player: UUID): Boolean = player in enrolled

    fun enroll(player: UUID) {
        if (enrolled.add(player)) save()
    }

    fun cancel(player: UUID) {
        if (enrolled.remove(player)) save()
    }

    private fun load(): MutableSet<UUID> {
        if (!file.exists()) return mutableSetOf()
        return YamlConfiguration.loadConfiguration(file).getStringList("enrolled").map(UUID::fromString).toMutableSet()
    }

    private fun save() {
        val yaml = YamlConfiguration()
        yaml.set("enrolled", enrolled.map(UUID::toString).sorted())
        yaml.save(file)
    }
}
