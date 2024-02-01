import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    embeddedKotlin("plugin.serialization")
    id("gradlebuild.repositories")
}

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

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.jetbrains:annotations:24.0.1")
}

testing.suites.named("test", JvmTestSuite::class) {
    useJUnitJupiter()
}
