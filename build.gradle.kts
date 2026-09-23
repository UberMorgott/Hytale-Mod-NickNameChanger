plugins {
    id("java-library")
    id("com.gradleup.shadow") version "9.3.1"
}

group = "com.nickname"
version = "0.0.17"

repositories {
    mavenCentral()
    maven("https://maven.hytale.com/release")
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:0.6.8")
    // LuckPerms-Hytale ships the standard LuckPerms API (net.luckperms.api)
    compileOnly("net.luckperms:api:5.4")
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release = 21
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()
    }

    shadowJar {
        archiveBaseName.set(rootProject.name)
        archiveClassifier.set("")
        relocate("com.google.gson", "com.nickname.libs.gson")
        minimize()
    }

    build {
        dependsOn(shadowJar)
    }
}

java {
    // Server 0.6.8 classes are Java 25 bytecode (major 69); compile with JDK 25, emit Java 21 bytecode
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
