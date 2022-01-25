import kotlinx.benchmark.gradle.*
import org.jetbrains.kotlin.allopen.gradle.*

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.allopen")
    id("org.jetbrains.kotlinx.benchmark")
    id("org.jetbrains.kotlin.plugin.noarg")
}

configure<AllOpenExtension> {
    annotation("org.openjdk.jmh.annotations.State")
}

configurations {
    all { resolutionStrategy.cacheDynamicVersionsFor(5, TimeUnit.MINUTES) }
}


val benchmarkVersion: String by rootProject
val testContainerVersion: String by rootProject
val coroutinesVersion: String by rootProject
val serializationVersion: String by rootProject
val exposedVersion: String by rootProject

noArg {
    annotation("kotlinx.serialization.Serializable")
}

dependencies {
    implementation(project(":"))
    testImplementation(project(":test-framework"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.4.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    testImplementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    testImplementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    testImplementation(project(":test-framework"))
}

kotlin {
    sourceSets.main {
        dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:$benchmarkVersion")
        }
    }
    listOf(
        "kotlin.Experimental",
        "kotlinx.serialization.ImplicitReflectionSerializer",
        "kotlinx.serialization.InternalSerializationApi",
        "kotlinx.serialization.ExperimentalSerializationApi",
        "kotlin.time.ExperimentalTime"
    ).let { annotations ->
        sourceSets.all { annotations.forEach(languageSettings::optIn) }
    }
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}


tasks {
    test {
        useJUnitPlatform()
        systemProperty("plugin.feature.drealtime", false)
    }
}
benchmark {
    configurations {
        named("main") {
            iterationTime = 5
            iterationTimeUnit = "ms"
        }
    }
    targets {
        register("test") {
            this as JvmBenchmarkTarget
            jmhVersion = "1.21"
        }
    }
}

