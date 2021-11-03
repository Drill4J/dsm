import java.net.*

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("com.github.hierynomus.license")
    id("org.jetbrains.kotlin.plugin.noarg")
    `maven-publish`
}

val scriptUrl: String by extra

apply(from = "$scriptUrl/git-version.gradle.kts")

repositories {
    mavenLocal()
    apply(from = "$scriptUrl/maven-repo.gradle.kts")
    jcenter()
}

val coroutinesVersion: String by project
val serializationVersion: String by project
val exposedVersion: String by project
val postgresSqlVersion: String by project
val hikariVersion: String by project
val loggerVersion: String by project

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
    implementation ("io.github.microutils:kotlin-logging-jvm:$loggerVersion")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.4.2")

}

dependencies {
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
}

dependencies {
    implementation("org.testng:testng:7.1.0")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.postgresql:postgresql:$postgresSqlVersion")
    testImplementation("ru.yandex.qatools.embed:postgresql-embedded:2.10")
}

java.targetCompatibility = JavaVersion.VERSION_1_8

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
        sourceSets.all { annotations.forEach(languageSettings::useExperimentalAnnotation) }
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
