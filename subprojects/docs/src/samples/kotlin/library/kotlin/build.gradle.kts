plugins {
    kotlin("jvm") version "1.3.71"
}

version = "1.0.2"
group = "org.gradle.sample"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
}
