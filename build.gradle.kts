import java.net.*

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.hierynomus.license")
    id("org.jetbrains.kotlin.plugin.noarg")
    `maven-publish`
}

val scriptUrl: String by extra

allprojects {
    apply(from = "$scriptUrl/git-version.gradle.kts")

    repositories {
        mavenLocal()
        mavenCentral()
        apply(from = "$scriptUrl/maven-repo.gradle.kts")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }
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

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.4.2")
    testImplementation("ch.qos.logback:logback-classic:1.2.3")
    testImplementation(project(":test-framework"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
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
            artifactId = "core"
            from(components["java"])
        }
    }
}

val licenseFormatSettings by tasks.registering(com.hierynomus.gradle.license.tasks.LicenseFormat::class) {
    source = fileTree(project.projectDir).also {
        include("**/*.kt", "**/*.java", "**/*.groovy")
        exclude("**/.idea")
    }.asFileTree
}

license {
    headerURI = URI("https://raw.githubusercontent.com/Drill4J/drill4j/develop/COPYRIGHT")
}

tasks["licenseFormat"].dependsOn(licenseFormatSettings)

tasks.test {
    useJUnitPlatform()
}
