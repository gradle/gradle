package demo.demoPlugins

import com.h0tk3y.kotlin.staticObjectNotation.analysis.printResolutionResults
import com.h0tk3y.kotlin.staticObjectNotation.analysis.resolve
import com.h0tk3y.kotlin.staticObjectNotation.schemaBuilder.schemaFromTypes

fun main() {
    val schema = schemaFromTypes(
        topLevelReceiver = TopLevelScope::class,
        types = listOf(TopLevelScope::class, PluginsBlock::class, PluginDefinition::class)
    )

    printResolutionResults(
        schema.resolve(
            """
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
    )
}