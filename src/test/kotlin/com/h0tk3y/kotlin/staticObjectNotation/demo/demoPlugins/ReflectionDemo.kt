package com.h0tk3y.kotlin.staticObjectNotation.demo.demoPlugins

import com.example.com.h0tk3y.kotlin.staticObjectNotation.demo.reflection.reflectAndPrint

object ReflectionDemo {
    @JvmStatic
    fun main(args: Array<String>) {
        schema.reflectAndPrint(
            """
            // Workaround for missing default value:
            plugins = com.h0tk3y.kotlin.staticObjectNotation.demo.demoPlugins.PluginsBlock()
                
            plugins {
                val kotlinVersion = "1.9.20"
                
                id("org.jetbrains.kotlin.jvm") version kotlinVersion
                id("org.jetbrains.kotlin.kapt") version kotlinVersion apply false
                id("java") apply false
                val app = id("application")
                app.apply(false)
            }
            """.trimIndent()
        )
    }
}