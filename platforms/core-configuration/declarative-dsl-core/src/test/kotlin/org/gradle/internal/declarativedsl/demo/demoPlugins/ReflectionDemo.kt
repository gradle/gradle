package org.gradle.internal.declarativedsl.demo.demoPlugins

import org.gradle.internal.declarativedsl.demo.reflection.printReflection
import org.gradle.internal.declarativedsl.demo.reflection.reflect
import org.gradle.internal.declarativedsl.demo.reflection.reflectAndPrint
import org.gradle.internal.declarativedsl.analysis.DefaultFqName
import org.gradle.internal.declarativedsl.objectGraph.ObjectReflection.ConstantValue
import org.gradle.internal.declarativedsl.objectGraph.ObjectReflection.DataObjectReflection


object ReflectionDemo {
    @JvmStatic
    fun main(args: Array<String>) {
        val code = """
            plugins {
                val kotlinVersion = "1.9.20"

                id("org.jetbrains.kotlin.jvm") version kotlinVersion
                id("org.jetbrains.kotlin.kapt") version kotlinVersion apply false
                id("java") apply false

                val app = id("application")
                app.apply(false)
            }""".trimIndent()
        val result = schema.reflect(code)

        printReflection(result)

        val pluginsProp = schema.topLevelReceiverType.properties.find { it.name == "plugins" }

        val plugins = (result as DataObjectReflection).properties[pluginsProp]!!.value as DataObjectReflection

        val kotlinKaptPlugin = plugins.addedObjects
            .find {
                val properties = (it as? DataObjectReflection)?.properties ?: error("unexpected object")
                val id = properties[idProp] ?: error("no id proprety found")
                val idPropertyValue = id.value
                idPropertyValue is ConstantValue && idPropertyValue.value == "org.jetbrains.kotlin.kapt"
            } as DataObjectReflection

        val version = kotlinKaptPlugin.properties[versionProp]!!.value as ConstantValue

        val ast = version.objectOrigin.originElement.sourceData
        val versionRange = ast.indexRange

        println("=== version range:")
        println(versionRange)

        println("===")
        val newCode = code.replaceRange(versionRange, "\"1.9.20-Beta\"")
        println(newCode)

        println("===")
        schema.reflectAndPrint(newCode)
    }
}


private
fun String.replaceRange(range: IntRange, replacement: String): String {
    return take(range.start) + replacement + drop(range.last + 1)
}


val pluginDefinition = schema.dataClassesByFqName
    .getValue(DefaultFqName.parse("org.gradle.internal.declarativedsl.demo.demoPlugins.PluginDefinition"))


val idProp = pluginDefinition.properties.single { it.name == "id" }


val versionProp = pluginDefinition.properties.single { it.name == "version" }
