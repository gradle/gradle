import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("gradlebuild.distribution.implementation-kotlin")
    id("gradlebuild.publish-public-libraries")

    embeddedKotlin("plugin.serialization")
}

description = "Common shared classes used by the Declarative DSL"

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_1_9)
        languageVersion.set(KotlinVersion.KOTLIN_1_9)
    }
}

dependencies {
    api(project(":declarative-dsl-tooling-models"))

    api(libs.kotlinCompilerEmbeddable)
    api(libs.kotlinStdlib)

    implementation(project(":declarative-dsl-api"))
    implementation(libs.kotlinReflect)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    testImplementation(libs.futureKotlin("test-junit5"))
    testImplementation("org.jetbrains:annotations:24.0.1")

    integTestDistributionRuntimeOnly(project(":distributions-full"))
}
