package dev.doyu.econet.paper.admin

import kotlin.test.Test
import kotlin.test.assertEquals

class BackupRetentionTest {
    @Test
    fun `only the newest backups are kept`() {
        val names = listOf("2026-01-03T00-00-00Z", "2026-01-01T00-00-00Z", "2026-01-02T00-00-00Z")

        assertEquals(listOf("2026-01-01T00-00-00Z"), backupsToDelete(names, keep = 2))
        assertEquals(emptyList(), backupsToDelete(names, keep = 5))
    }
}
