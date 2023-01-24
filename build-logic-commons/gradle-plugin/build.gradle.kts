plugins {
    `kotlin-dsl`
}

group = "gradlebuild"

description = "Provides plugins used to create a Gradle plugin with Groovy or Kotlin DSL within build-logic builds"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
        vendor.set(JvmVendorSpec.ADOPTIUM)
    }
}

dependencies {
    compileOnly("com.gradle:gradle-enterprise-gradle-plugin:3.12.2")

    implementation(project(":commons"))
    implementation("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:4.0.2")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.0")
    implementation("org.gradle.kotlin:gradle-kotlin-dsl-conventions:0.8.0")
}
