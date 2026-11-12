package dev.doyu.econet.paper.shop

import dev.doyu.econet.core.Amount
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

sealed interface ShopPut {
    data object Added : ShopPut
    data class PriceChanged(val previous: Amount) : ShopPut
    data object Full : ShopPut
}

/**
 * 정부 상점의 품목과 가격. 명령어로 바뀌는 값이라 config.yml이 아니라 shop.yml에 둔다.
 * 파일이 없을 때만 config.yml의 `shop.items`를 시작값으로 복사하고, 그 뒤로는 파일만 본다. 메인 스레드에서만 쓴다.
 *
 * 키는 `minecraft:lead` 같은 네임스페이스 이름이다. 아이템 종류로 바꾸는 일은 Bukkit이 필요해서 쓰는 쪽에서 한다.
 */
class ShopCatalog(private val file: File, seed: Map<String, Amount>) {
    private val prices: MutableMap<String, Amount> = if (file.exists()) load() else seed.toMutableMap()

    init {
        if (!file.exists()) save()
    }

    fun prices(): Map<String, Amount> = prices.toMap()

    fun put(key: String, price: Amount): ShopPut {
        val previous = prices[key]
        if (previous == null && prices.size >= MAX_ITEMS) return ShopPut.Full
        prices[key] = price
        save()
        return if (previous == null) ShopPut.Added else ShopPut.PriceChanged(previous)
    }

    fun remove(key: String): Boolean {
        if (prices.remove(key) == null) return false
        save()
        return true
    }

    private fun load(): MutableMap<String, Amount> {
        val section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("items") ?: return mutableMapOf()
        val loaded = linkedMapOf<String, Amount>()
        for (key in section.getKeys(false)) {
            require(section.isLong(key) || section.isInt(key)) { "${file.name}: items.$key must be a whole number" }
            val value = section.getLong(key)
            require(value > 0) { "${file.name}: items.$key must be positive" }
            loaded[key] = Amount(value)
        }
        return loaded
    }

    private fun save() {
        val yaml = YamlConfiguration()
        val items = yaml.createSection("items")
        for ((key, price) in prices) items.set(key, price.value)
        yaml.save(file)
    }

    companion object {
        /** 상자 화면 한 개(6줄)에 다 들어가는 수. */
        const val MAX_ITEMS = 54
    }
}
