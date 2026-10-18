plugins {
    kotlin("jvm") version libs.versions.kotlin.get() apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.run.paper) apply false
}

//repositories {
//    mavenCentral()
//    maven("https://repo.papermc.io/repository/maven-public/")
//}
//
//dependencies {
//    compileOnly(libs.paper.api)
//    implementation(libs.kotlin.stdlib)
//}
//
//kotlin {
//    jvmToolchain(25)
//}
//
//tasks {
//    build {
//        dependsOn(shadowJar)
//    }
//
//    runServer {
//        minecraftVersion(libs.versions.minecraft.get())
//        jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
//    }
//
//    processResources {
//        val props = mapOf("version" to version, "description" to project.description)
//        filesMatching("plugin.yml") {
//            expand(props)
//        }
//    }
//}
