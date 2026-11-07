plugins {
    kotlin("jvm")
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
}

group = "dev.doyu"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation(libs.paper.api)
    implementation(project(":core"))
    implementation(libs.kotlin.stdlib)
    compileOnly(libs.paper.api)
    implementation(libs.sqlite.jdbc)
    implementation(libs.kotlinx.coroutines.core)
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

// 테스트 서버: runServer 하나로 평지·가벼운 설정의 서버가 뜬다. 포트는 -Pport=25566 으로 바꾼다(한 번 정하면 남는다).
// 맵 종류는 월드를 처음 만들 때만 적용되므로, 새 평지로 다시 만들려면 resetTestServer를 먼저 돌린다.
val testServerDir = layout.projectDirectory.dir("run")
val testServerPort = providers.gradleProperty("port")

val prepareTestServer by tasks.registering {
    group = "run paper"
    description = "Writes flat-world test settings into run/server.properties."
    val propertiesFile = testServerDir.file("server.properties").asFile
    val port = testServerPort.orNull
    outputs.upToDateWhen { false }
    doLast {
        if (port != null) require(port.toIntOrNull() in 1..65535) { "port must be 1-65535: $port" }
        val settings = linkedMapOf(
            "level-type" to "minecraft\\:flat",
            // 26.3은 generator-settings가 비어 있으면({}) 평지 레이어가 없다며 오버월드를 만들지 못하므로 레이어를 직접 적는다.
            "generator-settings" to """{"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"structure_overrides":[],"features":false,"lakes":false}""",
            "generate-structures" to "false",
            "view-distance" to "6",
            "simulation-distance" to "4",
            "spawn-protection" to "0",
            "difficulty" to "easy",
            "max-players" to "8",
            "motd" to "Econet test server",
        )
        if (port != null) settings["server-port"] = port
        // 서버가 시작할 때 이 파일을 다시 쓰지만 이미 있는 값은 유지하므로, 나머지 줄은 건드리지 않고 이 키들만 덮어쓴다.
        propertiesFile.parentFile.mkdirs()
        val lines = if (propertiesFile.exists()) propertiesFile.readLines().toMutableList() else mutableListOf()
        for ((key, value) in settings) {
            val index = lines.indexOfFirst { it.startsWith("$key=") }
            if (index >= 0) lines[index] = "$key=$value" else lines += "$key=$value"
        }
        propertiesFile.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}

val resetTestServer by tasks.registering(Delete::class) {
    group = "run paper"
    description = "Deletes the test world and Econet data (ledger included) so the next runServer starts fresh."
    delete(testServerDir.dir("world"), testServerDir.dir("plugins/Econet"))
}

tasks {
    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
        dependsOn(prepareTestServer)
        mustRunAfter(resetTestServer)
    }
}

tasks.shadowJar {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version.toString(),
        "description" to (project.description ?: "")
    )

    inputs.properties(props)

    filesMatching("plugin.yml") {
        expand(props)
    }
}