package com.example.com.h0tk3y.kotlin.staticObjectNotation.demo.demoSimple

import com.example.com.h0tk3y.kotlin.staticObjectNotation.demo.reflection.reflectAndPrint

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
