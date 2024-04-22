package org.gradle.internal.declarativedsl.demo.demoSimple

import org.gradle.internal.declarativedsl.demo.reflection.reflectAndPrint


object ReflectionDemo {
    @JvmStatic
    fun main(args: Array<String>) {
        schema.reflectAndPrint(
            """
            plugins {
                val kotlinVersion = "1.9.20"

                id("org.jetbrains.kotlin.jvm") version kotlinVersion
                id("org.jetbrains.kotlin.kapt") version kotlinVersion apply false
                val java = id("java")
                val app = id("application")
                app.apply(java.apply)
            }
            """.trimIndent()
        )
    }
}
