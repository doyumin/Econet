package dev.doyu.econet.paper.buyback

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.math.BigDecimal
import java.time.Instant

/**
 * 시세 압력은 돈이 아니라서 장부에 넣지 않고 별도 파일에 둔다.
 * 잃어도 시세가 기준가로 돌아갈 뿐 잔액에는 영향이 없다.
 */
class MarketStateFile(private val file: File) {
    fun load(): Map<String, SalesPressure> {
        if (!file.exists()) return emptyMap()
        val yaml = YamlConfiguration.loadConfiguration(file)
        return yaml.getKeys(false).mapNotNull { key ->
            val section = yaml.getConfigurationSection(key) ?: return@mapNotNull null
            val units = section.getString("units")?.toBigDecimalOrNull() ?: return@mapNotNull null
            key to SalesPressure(units, Instant.ofEpochMilli(section.getLong("at")))
        }.toMap()
    }

    fun save(state: Map<String, SalesPressure>) {
        val yaml = YamlConfiguration()
        for ((key, pressure) in state) {
            // BigDecimal을 문자열로 남겨야 다시 읽을 때 double로 바뀌며 생기는 오차가 없다.
            yaml.set("$key.units", pressure.units.toPlainString())
            yaml.set("$key.at", pressure.at.toEpochMilli())
        }
        yaml.save(file)
    }
}
