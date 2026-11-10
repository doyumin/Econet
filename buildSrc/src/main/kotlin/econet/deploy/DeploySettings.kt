package econet.deploy

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * 배포 설정은 저장소에 두지 않는다(비밀번호·API 키가 섞여 있다).
 * Gradle 속성(~/.gradle/gradle.properties, -P)을 먼저 보고, 없으면 같은 이름의 환경 변수를 본다.
 */
fun Project.deploySetting(key: String): Provider<String> =
    providers.gradleProperty(key).orElse(providers.environmentVariable(environmentNameOf(key)))

/** `econet.panel.serverId` → `ECONET_PANEL_SERVER_ID` */
fun environmentNameOf(key: String): String =
    key.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").replace('.', '_').uppercase()

/** 값이 없으면 어디에 무엇을 넣어야 하는지까지 알려 주며 멈춘다. 실행 시점에만 검사해서, 배포하지 않는 빌드는 설정 없이도 돈다. */
internal fun Property<String>.required(key: String): String =
    orNull?.takeIf { it.isNotBlank() } ?: throw GradleException(
        "Missing deploy setting '$key'. Add `$key=...` to ~/.gradle/gradle.properties " +
            "or set the environment variable ${environmentNameOf(key)}.",
    )

internal fun Property<String>.optional(): String? = orNull?.takeIf { it.isNotBlank() }

data class SftpAddress(val host: String, val port: Int?)

/** 패널이 보여 주는 `sftp://host:2022` 형태를 그대로 붙여 넣어도 되게 한다. */
fun parseSftpAddress(text: String): SftpAddress {
    val withoutScheme = text.trim().removePrefix("sftp://").trimEnd('/')
    val host = withoutScheme.substringBeforeLast(':', withoutScheme)
    val port = withoutScheme.substringAfterLast(':', "").takeIf { it.isNotEmpty() }?.let { portText ->
        portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: throw GradleException("Invalid port in SFTP host '$text'")
    }
    if (host.isBlank()) throw GradleException("Invalid SFTP host '$text'")
    return SftpAddress(host, port)
}

/**
 * 원격 폴더에서 지울 파일: 이 플러그인의 jar(`Econet-*.jar`)와 이전에 끊긴 업로드의 임시 파일(`Econet-*.jar.uploading`)뿐이다.
 * 다른 플러그인 파일은 절대 건드리지 않도록 이름 접두사까지 정확히 맞춘다.
 */
fun ownedRemoteFiles(names: Collection<String>, baseName: String): List<String> =
    names.filter { name ->
        '/' !in name && name.startsWith("$baseName-") && (name.endsWith(".jar") || name.endsWith(".jar$UPLOADING_SUFFIX"))
    }.sorted()

const val UPLOADING_SUFFIX = ".uploading"
