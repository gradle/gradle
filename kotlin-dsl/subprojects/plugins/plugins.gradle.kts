import build.futureKotlin
import plugins.bundledGradlePlugin

plugins {
    `kotlin-dsl-plugin-bundle`
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

bundledGradlePlugin(
    name = "kotlinDslBase",
    shortDescription = "Gradle Kotlin DSL Base Plugin",
    pluginId = "org.gradle.kotlin.kotlin-dsl.base",
    pluginClass = "org.gradle.kotlin.dsl.plugins.base.KotlinDslBasePlugin")

bundledGradlePlugin(
    name = "kotlinDslCompilerSettings",
    shortDescription = "Gradle Kotlin DSL Compiler Settings",
    pluginId = "org.gradle.kotlin.kotlin-dsl.compiler-settings",
    pluginClass = "org.gradle.kotlin.dsl.plugins.dsl.KotlinDslCompilerPlugins")

bundledGradlePlugin(
    name = "kotlinDslPrecompiledScriptPlugins",
    shortDescription = "Gradle Kotlin DSL Precompiled Script Plugins",
    pluginId = "org.gradle.kotlin.kotlin-dsl.precompiled-script-plugins",
    pluginClass = "org.gradle.kotlin.dsl.plugins.precompiled.PrecompiledScriptPlugins")
