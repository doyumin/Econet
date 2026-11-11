package econet.deploy

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeploySettingsTest {
    @Test
    fun `property keys map to upper snake case environment variables`() {
        assertEquals("ECONET_SFTP_HOST", environmentNameOf("econet.sftp.host"))
        assertEquals("ECONET_SFTP_KEY_PATH", environmentNameOf("econet.sftp.keyPath"))
        assertEquals("ECONET_PANEL_SERVER_ID", environmentNameOf("econet.panel.serverId"))
        assertEquals("ECONET_PANEL_API_KEY", environmentNameOf("econet.panel.apiKey"))
    }

    @Test
    fun `sftp addresses accept the panel url form`() {
        assertEquals(SftpAddress("mc11.space-node.net", 2022), parseSftpAddress("sftp://mc11.space-node.net:2022"))
        assertEquals(SftpAddress("mc11.space-node.net", null), parseSftpAddress("mc11.space-node.net"))
        assertFailsWith<GradleException> { parseSftpAddress("sftp://host:99999") }
        assertFailsWith<GradleException> { parseSftpAddress("sftp://") }
    }

    @Test
    fun `only this plugin's jars and unfinished uploads are selected`() {
        val names = listOf(
            "Econet.jar", "Econet.jar.uploading", "Econet-1.0.jar", "Econet-0.9.jar", "Econet-1.0.jar.uploading",
            "EconetAddon-1.0.jar", "EconetAddon.jar", "Vault.jar", "Econet", "Econet-notes.txt", "Econet.jar.bak", "LuckPerms-5.4.jar",
        )

        assertEquals(
            listOf("Econet-0.9.jar", "Econet-1.0.jar", "Econet-1.0.jar.uploading", "Econet.jar", "Econet.jar.uploading"),
            ownedRemoteFiles(names, "Econet"),
        )
    }
}
