plugins {
    `kotlin-dsl`
}

description = "Provides a plugin that configures code quality plugins in the Gradle build"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

group = "gradlebuild"

dependencies {
    implementation("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:2.1.7")
    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions:0.7.0")
}
