// Evoluta: tiered, elemental champion mobs for Fabric 1.21.1.
//
//   ./gradlew build           compile, unit tests, game tests, remapped jar and its content check
//   ./gradlew runGametest     server-side game tests on a headless 1.21.1 server
//   ./gradlew runServer       a dev server with spark for tick profiling

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "evoluta"
