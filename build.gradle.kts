import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "com.h0tk3y"
version = "1.0-SNAPSHOT"

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

java.targetCompatibility = JavaVersion.VERSION_1_8

dependencies {
    implementation(kotlin("compiler-embeddable"))
    implementation(kotlin("reflect"))

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("com.strumenta:antlr-kotlin-runtime-jvm:0.1.2")

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.jetbrains:annotations:24.0.1")
}

testing.suites.named("test", JvmTestSuite::class) {
    useJUnitJupiter()
}
