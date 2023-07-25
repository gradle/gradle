plugins {
    `kotlin-dsl`
}

group = "gradlebuild"

description = "Provides common code used to create a Gradle plugin with Groovy or Kotlin DSL within build-logic builds"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}
