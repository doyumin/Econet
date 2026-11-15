package dev.doyu.econet.paper.casino

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CasinoMachinesTest {
    private val file = Files.createTempDirectory("casino").resolve("casino.yml").toFile()
    private val spot = MachineSpot("world", 10, 64, -5)

    @Test
    fun `registered machines survive a reload`() {
        val machines = CasinoMachines(file)
        assertTrue(machines.add(spot, CasinoMachines.COIN_FLIP))

        assertEquals(CasinoMachines.COIN_FLIP, CasinoMachines(file).gameAt(spot))
    }

    @Test
    fun `adding twice and removing a missing machine report false`() {
        val machines = CasinoMachines(file)
        assertTrue(machines.add(spot, CasinoMachines.COIN_FLIP))
        assertFalse(machines.add(spot, CasinoMachines.COIN_FLIP))

        assertTrue(machines.remove(spot))
        assertFalse(machines.remove(spot))
        assertNull(CasinoMachines(file).gameAt(spot))
    }
}
