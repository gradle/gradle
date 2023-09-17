package demo.demoPlugins

import com.h0tk3y.kotlin.staticObjectNotation.analysis.printResolutionResults
import com.h0tk3y.kotlin.staticObjectNotation.analysis.resolve

private val schema = demoSchema()

object PluginsDemo {
    @JvmStatic
    fun main(args: Array<String>) {
        printResolutionResults(
            resolve(
                schema,
                """
                    plugins {
                        val kotlinVersion = "1.9.20"
                        id("org.jetbrains.kotlin.jvm").version(kotlinVersion)
                        id("org.jetbrains.kotlin.kapt").version(kotlinVersion).apply(false)
                        id("application")
                    }
                    """.trimIndent()
            )
        )
    }
}