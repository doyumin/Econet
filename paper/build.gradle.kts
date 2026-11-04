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

tasks {
    runServer {
        minecraftVersion(libs.versions.minecraft.get())
        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
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