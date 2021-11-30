rootProject.name = "dsm"

val scriptUrl: String by extra
apply(from = "$scriptUrl/maven-repo.settings.gradle.kts")

pluginManagement {
    val kotlinVersion: String by settings
    val licenseVersion: String by extra
    val benchmarkVersion: String by extra
    val atomicFuVersion: String by extra
    plugins {
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.serialization") version kotlinVersion
        kotlin("plugin.allopen") version kotlinVersion
        id("kotlinx-atomicfu") version atomicFuVersion
        id("com.github.hierynomus.license") version licenseVersion
        id("org.jetbrains.kotlin.plugin.noarg") version kotlinVersion
        id("org.jetbrains.kotlinx.benchmark") version benchmarkVersion
    }

    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://dl.bintray.com/kotlin/kotlinx")
        maven(url = "https://oss.jfrog.org/artifactory/list/oss-release-local")
    }
}

//TODO remove after plugin marker is added to the atomicfu artifacts
mapOf(
    "kotlinx-atomicfu" to "org.jetbrains.kotlinx:atomicfu-gradle-plugin"
).let { substitutions ->
    pluginManagement.resolutionStrategy.eachPlugin {
        substitutions["${requested.id}"]?.let { useModule("$it:${target.version}") }
    }
}

include(":benchmarks")
