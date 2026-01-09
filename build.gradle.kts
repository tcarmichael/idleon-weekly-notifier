plugins {
    kotlin("jvm") version "1.9.22"
    id("io.ktor.plugin") version "2.3.8"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.idleon.bot"
version = "0.0.1"

application {
    mainClass.set("com.idleon.bot.ApplicationKt")
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    // Kotlin
    implementation(kotlin("stdlib"))

    // Ktor Server
    implementation("io.ktor:ktor-server-core-jvm:2.3.8")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.8")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.8")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.8")
    implementation("io.ktor:ktor-client-core-jvm:2.3.8")
    implementation("io.ktor:ktor-client-cio-jvm:2.3.8")

    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Kord (Discord) - using core for models/rest, we might not need gateway if we are pure HTTP
    // but Kord is great for types.
    implementation("dev.kord:kord-core:0.12.0")

    // Google Cloud
    implementation(platform("com.google.cloud:libraries-bom:26.32.0"))
    implementation("com.google.cloud:google-cloud-firestore")
    implementation("com.google.apis:google-api-services-sheets:v4-rev20230815-2.0.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.21.0")
    
    // Testing
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
