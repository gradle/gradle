package org.gradle.internal.declarativedsl.demo.demoPlugins

import org.gradle.declarative.dsl.schema.DataType
import org.gradle.internal.declarativedsl.analysis.ErrorReason
import org.gradle.internal.declarativedsl.demo.printResolutionResults
import org.gradle.internal.declarativedsl.demo.printResolvedAssignments
import org.gradle.internal.declarativedsl.demo.resolve
import org.gradle.internal.declarativedsl.schemaBuilder.schemaFromTypes
import org.junit.jupiter.api.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


val schema = schemaFromTypes(
    topLevelReceiver = TopLevelScope::class,
    types = listOf(TopLevelScope::class, PluginsBlock::class, PluginDefinition::class)
)


fun main() {
    val result = schema.resolve(
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
    printResolutionResults(result)

    printResolvedAssignments(result)
}


class Tests {
    @Test
    fun `unit assigned to val is reported as an error`() {
        val result = schema.resolve("val x = plugins { }")
        assertTrue { result.errors.single().errorReason == ErrorReason.UnitAssignment }
    }

    @Test
    fun `unit assigned to property is reported as an error`() {
        val result = schema.resolve("plugins = plugins { }")
        assertTrue { result.errors.any { it.errorReason == ErrorReason.UnitAssignment } }
    }

    @Test
    fun `type mismatch in assignments is reported as an error`() {
        val result = schema.resolve(
            """
            plugins {
                val i = id("test")
                i.version = 1
            }
            """.trimIndent()
        )
        val error = result.errors.singleOrNull()
        assertNotNull(error)
        val reason = error.errorReason
        assertIs<ErrorReason.AssignmentTypeMismatch>(reason)
        assertIs<DataType.StringDataType>(reason.expected)
        assertIs<DataType.IntDataType>(reason.actual)
    }
}
