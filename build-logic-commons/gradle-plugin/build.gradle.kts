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
    compileOnly("com.gradle:develocity-gradle-plugin:3.17.4")

    api(platform(project(":build-platform")))

    implementation(project(":basics"))
    implementation(project(":module-identity"))
    implementation("net.ltgt.gradle:gradle-errorprone-plugin:3.1.0")

    implementation("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:4.4.0")
    // This Kotlin version should only be updated when updating the above kotlin-dsl version
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.23")
    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions")
    implementation("org.gradle:test-retry-gradle-plugin:1.5.2")
}
