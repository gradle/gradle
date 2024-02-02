import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("gradlebuild.distribution.implementation-kotlin")
    embeddedKotlin("plugin.serialization")
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_1_9)
        languageVersion.set(KotlinVersion.KOTLIN_1_9)
    }
}

dependencies {
    api(libs.futureKotlin("compiler-embeddable"))
    api(libs.futureKotlin("stdlib"))
    api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.2")

    implementation(libs.futureKotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    testImplementation(libs.futureKotlin("test-junit5"))
    testImplementation("org.jetbrains:annotations:24.0.1")
}
