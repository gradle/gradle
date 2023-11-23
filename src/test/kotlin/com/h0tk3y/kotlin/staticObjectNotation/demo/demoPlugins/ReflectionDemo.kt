package com.h0tk3y.kotlin.staticObjectNotation.demo.demoPlugins

import com.example.com.h0tk3y.kotlin.staticObjectNotation.demo.reflection.printReflection
import com.example.com.h0tk3y.kotlin.staticObjectNotation.demo.reflection.reflect
import com.example.com.h0tk3y.kotlin.staticObjectNotation.demo.reflection.reflectAndPrint
import com.h0tk3y.kotlin.staticObjectNotation.analysis.DataType
import com.h0tk3y.kotlin.staticObjectNotation.analysis.FqName
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.ObjectReflection
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.ObjectReflection.ConstantValue
import com.h0tk3y.kotlin.staticObjectNotation.objectGraph.ObjectReflection.DataObjectReflection
import kotlinx.ast.common.ast.*

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
            }
            """.trimIndent()
        val result = schema.reflect(code)

        printReflection(result)

        val pluginsProp = schema.topLevelReceiverType.properties.find { it.name == "plugins" }

        val plugins = (result as DataObjectReflection).properties[pluginsProp] as DataObjectReflection

        val kotlinKaptPlugin = plugins.addedObjects
            .find {
                val properties = (it as? DataObjectReflection)?.properties ?: error("unexpected object")
                val id = properties[idProp] ?: error("no id proprety found")
                val idPropertyValue = id.value
                idPropertyValue is ConstantValue && idPropertyValue.value == "org.jetbrains.kotlin.kapt"
            } as DataObjectReflection

        val version = kotlinKaptPlugin.properties[versionProp] as ConstantValue

        val ast = version.objectOrigin.originElement.originAst
        val versionRange = ast.rangeOrNull

        println("=== version range:")
        println(versionRange)

        println("===")
        val newCode = code.replaceRange(versionRange!!, "\"1.9.20-Beta\"")
        println(newCode)

        println("===")
        schema.reflectAndPrint(newCode)
    }
}

private val Ast.rangeOrNull: IntRange?
    get() = astInfoOrNull?.let { it.start.index..it.stop.index }

private fun String.replaceRange(range: IntRange, replacement: String): String {
    return take(range.start) + replacement + drop(range.last + 1)
}

val pluginDefinition = schema.dataClassesByFqName
    .getValue(FqName.parse("com.h0tk3y.kotlin.staticObjectNotation.demo.demoPlugins.PluginDefinition"))
val idProp = pluginDefinition.properties.single { it.name == "id" }
val versionProp = pluginDefinition.properties.single { it.name == "version" }
