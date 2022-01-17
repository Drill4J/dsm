plugins {
    kotlin("jvm")
    `maven-publish`
}

val testContainerVersion: String by extra
val hikariVersion: String by extra
val exposedVersion: String by extra

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(project(":"))
    implementation("org.testcontainers:postgresql:$testContainerVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

val sourcesJar by tasks.registering(Jar::class) {
    from(sourceSets.main.get().allSource)
    archiveClassifier.set("sources")
}

publishing {
    publications {
        create<MavenPublication>("lib") {
            from(components["java"])
        }
    }
}
