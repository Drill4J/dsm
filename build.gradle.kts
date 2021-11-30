import java.net.*

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.hierynomus.license")
    id("org.jetbrains.kotlin.plugin.noarg")
    id("kotlinx-atomicfu")
    `maven-publish`
}

val scriptUrl: String by extra

apply(from = "$scriptUrl/git-version.gradle.kts")

repositories {
    mavenLocal()
    mavenCentral()
    apply(from = "$scriptUrl/maven-repo.gradle.kts")
}

val coroutinesVersion: String by project
val serializationVersion: String by project
val exposedVersion: String by project
val postgresSqlVersion: String by project
val hikariVersion: String by project
val testContainerVersion: String by project
val loggerVersion: String by project
val zstVersion: String by project
val collectionImmutableVersion: String by extra

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    api("com.zaxxer:HikariCP:$hikariVersion")
    api("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation("io.github.microutils:kotlin-logging-jvm:$loggerVersion")

    api("org.postgresql:postgresql:$postgresSqlVersion")
    implementation("com.github.luben:zstd-jni:$zstVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:$collectionImmutableVersion")
    implementation("org.testcontainers:postgresql:$testContainerVersion")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.4.2")
    testImplementation("org.slf4j:slf4j-simple:1.7.32")
}

allprojects {
    tasks.withType<org.gradle.jvm.tasks.Jar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    target {
        compilations.all { kotlinOptions.jvmTarget = "${project.java.targetCompatibility}" }
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

noArg {
    annotation("kotlinx.serialization.Serializable")
}

publishing {
    publications {
        create<MavenPublication>("lib") {
            from(components["java"])
        }
    }
}

val licenseFormatSettings by tasks.registering(com.hierynomus.gradle.license.tasks.LicenseFormat::class) {
    source = fileTree(project.projectDir).also {
        include("**/*.kt", "**/*.java", "**/*.groovy")
        exclude("**/.idea")
    }.asFileTree
    headerURI = URI("https://raw.githubusercontent.com/Drill4J/drill4j/develop/COPYRIGHT")
}

license {
    skipExistingHeaders = true
}

tasks["licenseFormat"].dependsOn(licenseFormatSettings)

tasks.test {
    useJUnitPlatform()
}
