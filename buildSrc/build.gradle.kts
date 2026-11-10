plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.sshj)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
