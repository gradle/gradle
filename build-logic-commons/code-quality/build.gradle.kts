plugins {
    `kotlin-dsl`
}

description = "Provides a plugin that configures code quality plugins in the Gradle build"

group = "gradlebuild"

dependencies {
    implementation("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:2.2.0")
    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions:0.7.0")
}
