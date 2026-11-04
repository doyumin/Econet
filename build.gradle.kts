plugins {
    kotlin("jvm") version libs.versions.kotlin.get() apply false
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.run.paper) apply false
}
