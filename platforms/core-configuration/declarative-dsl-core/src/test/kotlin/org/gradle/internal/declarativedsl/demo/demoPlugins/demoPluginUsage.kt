package org.gradle.internal.declarativedsl.demo.demoPlugins


fun main() {
    val topLevelScope = TopLevelScope()
    topLevelScope.run {
        plugins {
            val kotlinVersion = "1.9.20"

            id("org.jetbrains.kotlin.jvm") version kotlinVersion
            id("org.jetbrains.kotlin.kapt") version kotlinVersion apply false
            id("java") apply false
            val app = id("application")
            app.apply(false)
        }
    }
    println(topLevelScope.plugins)
}
