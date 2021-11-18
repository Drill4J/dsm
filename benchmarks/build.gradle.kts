import kotlinx.benchmark.gradle.*
import org.jetbrains.kotlin.allopen.gradle.*

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    kotlin("plugin.allopen") version "1.4.21"
    id("org.jetbrains.kotlinx.benchmark") version "0.3.0"
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

noArg {
    annotation("kotlinx.serialization.Serializable")
}

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
}

dependencies {
    implementation(project(":"))
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.4.2")
    testImplementation("org.testcontainers:postgresql:$testContainerVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
}

kotlin {
    sourceSets.main {
        dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:$benchmarkVersion")
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.withType<JavaExec>{
    jvmArgs = listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5015")
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

