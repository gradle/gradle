plugins {
    `java-gradle-plugin`
    kotlin("jvm") version embeddedKotlinVersion
    `kotlin-dsl`
    `maven-publish`
}

group = "my"

version = "1.0"

apply<org.gradle.kotlin.dsl.plugins.precompiled.PrecompiledScriptPlugins>()

publishing {
    repositories {
        maven(url = "build/repository")
    }
}

repositories {
    gradlePluginPortal()
}
