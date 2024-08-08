plugins {
    `kotlin-dsl`
}

group = "gradlebuild"

description = "Provides plugins used to create a Gradle plugin with Groovy or Kotlin DSL within build-logic builds"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

dependencies {
    compileOnly("com.gradle:develocity-gradle-plugin:3.17.6")

    api(platform(projects.buildPlatform))

    implementation(projects.basics)
    implementation(projects.moduleIdentity)
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:4.0.1")

    implementation("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:5.1.0")
    // This Kotlin version should only be updated when updating the above kotlin-dsl version
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.10")
    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions")
    implementation("org.gradle:test-retry-gradle-plugin:1.5.2")

    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.6")
}
