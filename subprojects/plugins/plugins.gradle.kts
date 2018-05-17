import build.futureKotlin
import plugins.bundledGradlePlugin

plugins {
    id("kotlin-dsl-plugin-bundle")
}

base {
    archivesBaseName = "gradle-kotlin-dsl-plugins"
}

dependencies {
    compileOnly(project(":provider"))

    implementation(futureKotlin("stdlib-jdk8"))
    implementation(futureKotlin("gradle-plugin"))
    implementation(futureKotlin("sam-with-receiver"))

    testImplementation(project(":test-fixtures"))
}


// plugins ------------------------------------------------------------

bundledGradlePlugin(
    name = "embeddedKotlin",
    shortDescription = "Embedded Kotlin Gradle Plugin",
    pluginId = "org.gradle.kotlin.embedded-kotlin",
    pluginClass = "org.gradle.kotlin.dsl.plugins.embedded.EmbeddedKotlinPlugin")

bundledGradlePlugin(
    name = "kotlinDsl",
    shortDescription = "Gradle Kotlin DSL Plugin",
    pluginId = "org.gradle.kotlin.kotlin-dsl",
    pluginClass = "org.gradle.kotlin.dsl.plugins.dsl.KotlinDslPlugin")
