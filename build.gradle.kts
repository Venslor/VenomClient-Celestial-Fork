import java.text.SimpleDateFormat
import java.util.*

plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization") version "2.1.20"
    id("com.github.johnrengelman.shadow") version "8.+"
    id ("com.gorylenko.gradle-git-properties") version "2.4.1"
    id("com.google.protobuf") version "0.9.5"
}

group = "net.pvproom.client"
version = "1.0-SNAPSHOT"

val isGitHubActions = System.getenv("GITHUB_ACTIONS") == "true"

if (isGitHubActions) {
    val timeStamp = SimpleDateFormat("yyyyMMdd-HHmm").format(Date())
    version = "nightly-$timeStamp"
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
//    java.sourceCompatibility = JavaVersion.VERSION_17
//    java.targetCompatibility = JavaVersion.VERSION_17
}


repositories {
    maven("https://oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.typesafe.com/typesafe/maven-releases/")
    mavenCentral()
}

dependencies {
    implementation("com.auth0:java-jwt:4.5.0")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("org.slf4j:slf4j-log4j12:2.0.17")
    implementation("org.apache.logging.log4j:log4j-api:2.24.3")
    implementation("org.apache.logging.log4j:log4j-core:2.24.3")

    implementation("com.squareup.okhttp3:okhttp-coroutines:5.1.0")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:5.1.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("com.formdev:flatlaf:3.6")
    implementation("commons-io:commons-io:2.19.0")
    implementation("cn.hutool:hutool-crypto:5.8.37")
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
    implementation("com.google.protobuf:protobuf-kotlin:4.30.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}

tasks.shadowJar {
    archiveClassifier.set("fatjar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    exclude("native-binaries/**")

    exclude("LICENSE.txt")

    exclude("META-INF/maven/**")
    exclude("META-INF/versions/**")

    exclude("org/junit/**")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.30.2"
    }
}

application {
    mainClass = "net.pvproom.client.RoomKt"
}

tasks.jar {
    dependsOn("shadowJar")

    manifest {
        attributes(
            "Main-Class" to "net.pvproom.client.RoomKt",
        )
        attributes(
            "Charset" to "UTF-8"
        )
    }
}