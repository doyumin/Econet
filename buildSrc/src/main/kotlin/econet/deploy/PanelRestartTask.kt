package econet.deploy

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Pterodactyl Client API로 서버 재시작 신호를 보낸다. 성공하면 패널은 204 No Content를 돌려준다. */
abstract class PanelRestartTask : DefaultTask() {
    @get:Internal
    abstract val panelUrl: Property<String>

    @get:Internal
    abstract val serverId: Property<String>

    @get:Internal
    abstract val apiKey: Property<String>

    @TaskAction
    fun restart() {
        val panel = panelUrl.required("econet.panel.url").trim().trimEnd('/')
        val id = serverId.required("econet.panel.serverId").trim()
        val key = apiKey.required("econet.panel.apiKey").trim()
        val uri = URI.create("$panel/api/client/servers/$id/power")
        // API 키가 평문으로 오가지 않게 https만 허용한다. 로컬 시험용 서버만 예외다.
        if (uri.scheme != "https" && uri.host !in LOCAL_HOSTS) {
            throw GradleException("econet.panel.url must use https: $panel")
        }

        logger.lifecycle("Requesting restart of server $id at $panel")
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"signal":"restart"}"""))
            .build()
        val response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
            .send(request, HttpResponse.BodyHandlers.ofString())

        logger.lifecycle("Restart response: HTTP ${response.statusCode()}")
        if (response.statusCode() !in 200..299) {
            val hint = when (response.statusCode()) {
                401, 403 -> " Check econet.panel.apiKey: it must be a Client API key (Account → API Credentials), not an Application key."
                404 -> " Check econet.panel.serverId and econet.panel.url."
                409 -> " The server may be busy (e.g. installing or already changing power state)."
                else -> ""
            }
            throw GradleException("Restart failed with HTTP ${response.statusCode()}: ${response.body().take(500)}$hint")
        }
    }

    private companion object {
        val LOCAL_HOSTS = setOf("localhost", "127.0.0.1")
    }
}
