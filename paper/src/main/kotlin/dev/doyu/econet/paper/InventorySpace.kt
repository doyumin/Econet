package dev.doyu.econet.paper

import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * 실제 인벤토리를 건드리지 않고, 복사본에 넣어 보아 다 들어가는지 확인한다.
 * 돈을 받기 전에 확인해야 "돈은 냈는데 아이템은 바닥에" 같은 일이 생기지 않는다.
 */
fun Server.fitsInInventory(player: Player, items: List<ItemStack>): Boolean {
    val storage = player.inventory.storageContents
    val probe = createInventory(null, storage.size)
    probe.storageContents = storage.map { it?.clone() }.toTypedArray()
    return probe.addItem(*items.map { it.clone() }.toTypedArray()).isEmpty()
}

/** 넘치는 것은 발밑에 떨군다. */
fun Player.receive(items: List<ItemStack>) {
    if (items.isEmpty()) return
    for (leftover in inventory.addItem(*items.toTypedArray()).values) world.dropItemNaturally(location, leftover)
}
