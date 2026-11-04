package dev.doyu.econet.paper.insurance

import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InsurancePoliciesTest {
    @Test
    fun `enrollment survives a restart and can be cancelled`() {
        val file = Files.createTempDirectory("econet-insurance").resolve("insurance.yml").toFile()
        val alice = UUID.randomUUID()
        val bob = UUID.randomUUID()

        InsurancePolicies(file).apply {
            enroll(alice)
            enroll(bob)
            cancel(bob)
        }
        val reloaded = InsurancePolicies(file)

        assertTrue(reloaded.isEnrolled(alice))
        assertFalse(reloaded.isEnrolled(bob))
        file.parentFile.deleteRecursively()
    }
}
