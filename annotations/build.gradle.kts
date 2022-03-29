plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    `maven-publish`
}

repositories {
    mavenCentral()
}

val serializationVersion: String by extra

kotlin {
    targets {
        mingwX64()
        macosX64()
        linuxX64()
        jvm()
    }
    sourceSets.commonMain {
        dependencies {
            implementation(kotlin("stdlib-common"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
        }
    }

    listOf(
        "kotlinx.serialization.ExperimentalSerializationApi",
    ).let { annotations ->
        sourceSets.all { annotations.forEach(languageSettings::optIn) }
    }
}
