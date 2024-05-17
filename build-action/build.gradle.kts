plugins {
    java
}

dependencies {
    implementation(libs.gradle.tooling.api)
    implementation(libs.gradle.declarative.dsl.tooling.models)
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(8)
    }
}