package econet.deploy

import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.transport.verification.OpenSSHKnownHosts
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.method.AuthMethod
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.AuthPublickey
import net.schmizz.sshj.userauth.password.PasswordUtils
import net.schmizz.sshj.xfer.FileSystemFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64

/**
 * 플러그인 jar를 SFTP로 서버의 plugins 폴더에 올린다. Pterodactyl은 SFTP 서브시스템만 열어 주므로 셸 명령 없이 SFTP 연산만 쓴다.
 *
 * 순서: 임시 이름으로 업로드 → 크기 확인 → 이 플러그인의 옛 jar 삭제 → 최종 이름으로 rename.
 * 업로드를 삭제보다 먼저 하는 이유: 업로드가 실패해도 서버에 돌아가던 jar가 그대로 남는다.
 * 임시 파일은 `.jar`로 끝나지 않으므로 그사이 서버가 재시작돼도 반쯤 쓴 파일을 플러그인으로 읽지 않는다.
 */
abstract class SftpDeployTask : DefaultTask() {
    @get:InputFile
    abstract val jar: RegularFileProperty

    /** 원격 jar 이름의 앞부분. `Econet`이면 `Econet.jar`로 올리고 옛 `Econet.jar`·`Econet-*.jar`를 지운다. */
    @get:Input
    abstract val remoteBaseName: Property<String>

    @get:Input
    abstract val remoteDirectory: Property<String>

    // 접속 정보는 비밀이 섞여 있어 입력 지문(@Input)에 남기지 않는다. 이 태스크는 출력이 없어 어차피 매번 실행된다.
    @get:Internal
    abstract val host: Property<String>

    @get:Internal
    abstract val port: Property<String>

    @get:Internal
    abstract val user: Property<String>

    @get:Internal
    abstract val keyPath: Property<String>

    @get:Internal
    abstract val keyPassphrase: Property<String>

    @get:Internal
    abstract val password: Property<String>

    @get:Internal
    abstract val hostKey: Property<String>

    @TaskAction
    fun upload() {
        val address = parseSftpAddress(host.required("econet.sftp.host"))
        val port = port.optional()?.let { text ->
            text.toIntOrNull()?.takeIf { it in 1..65535 } ?: throw GradleException("econet.sftp.port must be 1-65535: $text")
        } ?: address.port ?: DEFAULT_PTERODACTYL_SFTP_PORT
        val user = user.required("econet.sftp.user")
        val keyPath = keyPath.optional()
        val password = password.optional()
        if (keyPath == null && password == null) {
            throw GradleException("Set econet.sftp.keyPath (SSH private key) or econet.sftp.password (ECONET_SFTP_KEY_PATH / ECONET_SFTP_PASSWORD).")
        }

        val localJar = jar.get().asFile
        val directory = remoteDirectory.get().trimEnd('/').ifEmpty { "/" }
        val finalName = "${remoteBaseName.get()}.jar"
        val tempName = finalName + UPLOADING_SUFFIX
        logger.lifecycle("Deploying ${localJar.name} (${localJar.length()} bytes) to $user@${address.host}:$port$directory/$finalName")

        SSHClient().use { ssh ->
            val hostKeys = HostKeyCheck(hostKey.optional())
            ssh.addHostKeyVerifier(hostKeys)
            try {
                ssh.connect(address.host, port)
            } catch (e: TransportException) {
                throw hostKeys.explain(e)
            }
            authenticate(ssh, user, keyPath, password)

            ssh.newSFTPClient().use { sftp ->
                val existing = sftp.ls(directory).map { it.name }
                sftp.put(FileSystemFile(localJar), "$directory/$tempName")
                val uploadedSize = sftp.size("$directory/$tempName")
                if (uploadedSize != localJar.length()) {
                    throw GradleException("Upload incomplete: $tempName is $uploadedSize bytes, expected ${localJar.length()}. The old jar was left in place.")
                }
                logger.lifecycle("Uploaded $tempName ($uploadedSize bytes)")

                val stale = ownedRemoteFiles(existing, remoteBaseName.get()) - tempName
                for (name in stale) {
                    sftp.rm("$directory/$name")
                    logger.lifecycle("Deleted $directory/$name")
                }
                if (stale.isEmpty()) logger.lifecycle("No old ${remoteBaseName.get()}-*.jar to delete")

                sftp.rename("$directory/$tempName", "$directory/$finalName")
                logger.lifecycle("Upload done: $directory/$finalName")
            }
        }
    }

    /** 키를 먼저 시도하고, 키가 거절되거나 없으면 비밀번호로 넘어간다. sshj는 목록 순서대로 시도한다. */
    private fun authenticate(ssh: SSHClient, user: String, keyPath: String?, password: String?) {
        val methods = mutableListOf<AuthMethod>()
        if (keyPath != null) {
            val keyFile = File(keyPath)
            if (!keyFile.isFile) throw GradleException("econet.sftp.keyPath does not point to a file: $keyPath")
            val passphrase = keyPassphrase.optional()
            val keys = if (passphrase == null) ssh.loadKeys(keyFile.path) else ssh.loadKeys(keyFile.path, passphrase)
            methods += AuthPublickey(keys)
        }
        if (password != null) methods += AuthPassword(PasswordUtils.createOneOff(password.toCharArray()))
        try {
            ssh.auth(user, methods)
        } catch (e: UserAuthException) {
            throw GradleException("SFTP login failed for '$user' (tried: ${methods.joinToString { it.name }}). Check the username and credentials.", e)
        }
        logger.lifecycle("Logged in as $user")
    }

    private companion object {
        /** Pterodactyl(Wings)의 기본 SFTP 포트. */
        const val DEFAULT_PTERODACTYL_SFTP_PORT = 2022
    }
}

/**
 * 서버 신원을 확인한다. 확인 없이 접속하면 중간에서 가로챈 서버에 비밀번호를 넘길 수 있다.
 * ~/.ssh/known_hosts에 있거나 econet.sftp.hostKey(SHA256 지문)와 맞으면 통과다. 둘 다 아니면 서버 지문을 알려 주며 멈춘다.
 */
private class HostKeyCheck(private val expectedFingerprint: String?) : HostKeyVerifier {
    private val knownHosts: HostKeyVerifier? = File(System.getProperty("user.home"), ".ssh/known_hosts")
        .takeIf { it.isFile }
        ?.let { OpenSSHKnownHosts(it) }

    var seenFingerprint: String? = null
        private set

    override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
        val fingerprint = sha256Fingerprint(key)
        seenFingerprint = fingerprint
        if (expectedFingerprint != null) return normalize(expectedFingerprint) == normalize(fingerprint)
        return knownHosts?.verify(hostname, port, key) == true
    }

    override fun findExistingAlgorithms(hostname: String, port: Int): List<String> =
        knownHosts?.findExistingAlgorithms(hostname, port).orEmpty()

    fun explain(error: TransportException): GradleException {
        val seen = seenFingerprint ?: return GradleException("Could not connect to the SFTP server: ${error.message}", error)
        val message = if (expectedFingerprint != null) {
            "SFTP host key mismatch! Expected ${expectedFingerprint.trim()}, but the server presented $seen. " +
                "If the server was not reinstalled, do not continue: someone may be intercepting the connection."
        } else {
            "SFTP host key is not trusted yet. The server presented $seen. " +
                "If this matches what your panel shows (or you trust this network), add `econet.sftp.hostKey=$seen` to ~/.gradle/gradle.properties."
        }
        return GradleException(message, error)
    }

    private fun normalize(fingerprint: String): String = fingerprint.trim().removePrefix("SHA256:").trimEnd('=')

    private fun sha256Fingerprint(key: PublicKey): String {
        val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(blob))
    }
}
