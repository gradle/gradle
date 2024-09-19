import org.apache.commons.codec.binary.Base64

plugins {
    id("org.barfuin.gradle.taskinfo") version "2.2.0"
    id("com.github.spotbugs") version "6.0.22"
    id("java")
}

allprojects {
    apply(plugin = "org.barfuin.gradle.taskinfo")

    // Additional configuration for all projects
    repositories {
        mavenCentral()
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("commons-codec:commons-codec:1.17.1")
}

buildscript {
    repositories {
        mavenCentral()
        google()
    }
    dependencies {
        classpath("commons-codec:commons-codec:1.15") // This will be available only for the build script
    }
}

tasks.register("encode") {
    doLast {
        val encodedString: ByteArray = Base64().encode("hello world\n".toByteArray())
        println(String(encodedString))
    }
}



