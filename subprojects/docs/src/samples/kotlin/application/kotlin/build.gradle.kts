plugins {
    application
    kotlin("jvm") version "1.3.61"
}

version = "1.0.2"
group = "org.gradle.sample"

application {
    mainClassName = "org.gradle.sample.MainKt"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}
