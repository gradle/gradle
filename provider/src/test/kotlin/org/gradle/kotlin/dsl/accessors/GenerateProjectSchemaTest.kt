package org.gradle.kotlin.dsl.accessors

import org.gradle.kotlin.dsl.provider.spi.ProjectSchema
import org.gradle.kotlin.dsl.provider.spi.ProjectSchemaEntry
import org.gradle.kotlin.dsl.provider.spi.loadMultiProjectSchemaFrom

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class GenerateProjectSchemaTest : AbstractIntegrationTest() {

    @Test
    fun `writes multi-project schema to gradle slash project dash schema dot json`() {

        withSettings("""
            include("sub-java")
            include("sub-groovy")
            include("sub-kotlin-dsl")
        """)

        withBuildScript("""
            plugins {
                base
                `kotlin-dsl` apply false
            }
            subprojects {
                apply(plugin = "java")
            }
            project(":sub-groovy") {
                apply(plugin = "groovy")
            }
            project(":sub-kotlin-dsl") {
                apply(plugin = "org.gradle.kotlin.kotlin-dsl")
            }
        """)

        build("kotlinDslAccessorsSnapshot")

        val generatedSchema =
            loadMultiProjectSchemaFrom(
                existing("gradle/project-schema.json"))

        val expectedSchema =
            mapOf(
                ":" to baseProjectSchema,
                ":sub-groovy" to groovyProjectSchema,
                ":sub-java" to javaProjectSchema,
                ":sub-kotlin-dsl" to kotlinDslProjectSchema)

        assertThat(
            generatedSchema,
            equalTo(expectedSchema))
    }
}


private
val baseProjectSchema = ProjectSchema(
    extensions = listOf(
        ProjectSchemaEntry(
            "org.gradle.api.Project",
            "ext",
            "org.gradle.api.plugins.ExtraPropertiesExtension"),
        ProjectSchemaEntry(
            "org.gradle.api.Project",
            "defaultArtifacts",
            "org.gradle.api.internal.plugins.DefaultArtifactPublicationSet"),
        ProjectSchemaEntry(
            "org.gradle.api.internal.plugins.DefaultArtifactPublicationSet",
            "ext",
            "org.gradle.api.plugins.ExtraPropertiesExtension")),
    conventions = listOf(
        ProjectSchemaEntry(
            "org.gradle.api.Project",
            "base",
            "org.gradle.api.plugins.BasePluginConvention")),
    configurations = listOf("archives", "default"))


private
fun javaExtensionsWith(otherExtensions: List<ProjectSchemaEntry<String>>) =
    baseProjectSchema.extensions +
        listOf(
            ProjectSchemaEntry(
                "org.gradle.api.Project",
                "reporting",
                "org.gradle.api.reporting.ReportingExtension"),
            ProjectSchemaEntry(
                "org.gradle.api.reporting.ReportingExtension",
                "ext",
                "org.gradle.api.plugins.ExtraPropertiesExtension")) +
        otherExtensions +
        listOf(
            ProjectSchemaEntry(
                "org.gradle.api.tasks.SourceSet",
                "ext",
                "org.gradle.api.plugins.ExtraPropertiesExtension"))


private
val javaProjectSchema: ProjectSchema<String> =
    ProjectSchema(
        extensions = javaExtensionsWith(emptyList()),
        conventions = baseProjectSchema.conventions + listOf(
            ProjectSchemaEntry(
                "org.gradle.api.Project",
                "java",
                "org.gradle.api.plugins.JavaPluginConvention")),
        configurations = (baseProjectSchema.configurations + listOf(
            "annotationProcessor",
            "apiElements",
            "compile", "compileClasspath", "compileOnly",
            "implementation",
            "runtime", "runtimeClasspath", "runtimeElements", "runtimeOnly",
            "testAnnotationProcessor",
            "testCompile", "testCompileClasspath", "testCompileOnly",
            "testImplementation",
            "testRuntime", "testRuntimeClasspath", "testRuntimeOnly")).sorted())


private
val groovyProjectSchema: ProjectSchema<String> =
    ProjectSchema(
        extensions = javaExtensionsWith(listOf(
            ProjectSchemaEntry(
                "org.gradle.api.Project",
                "groovyRuntime",
                "org.gradle.api.tasks.GroovyRuntime"),
            ProjectSchemaEntry(
                "org.gradle.api.tasks.GroovyRuntime",
                "ext",
                "org.gradle.api.plugins.ExtraPropertiesExtension"))),
        conventions = javaProjectSchema.conventions,
        configurations = javaProjectSchema.configurations)


private
val kotlinDslProjectSchema: ProjectSchema<String> =
    ProjectSchema(
        extensions = javaExtensionsWith(listOf(
            ProjectSchemaEntry(
                "org.gradle.api.Project",
                "kotlin",
                "org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension"),
            ProjectSchemaEntry(
                "org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension",
                "ext",
                "org.gradle.api.plugins.ExtraPropertiesExtension"),
            ProjectSchemaEntry(
                "org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension",
                "experimental",
                "org.jetbrains.kotlin.gradle.dsl.ExperimentalExtension"),
            ProjectSchemaEntry(
                "org.jetbrains.kotlin.gradle.dsl.ExperimentalExtension",
                "ext",
                "org.gradle.api.plugins.ExtraPropertiesExtension"),
            ProjectSchemaEntry(
                "org.gradle.api.Project",
                "kapt",
                "org.jetbrains.kotlin.gradle.plugin.KaptExtension"),
            ProjectSchemaEntry(
                "org.jetbrains.kotlin.gradle.plugin.KaptExtension",
                "ext",
                "org.gradle.api.plugins.ExtraPropertiesExtension"),
            ProjectSchemaEntry(
                "org.gradle.api.Project",
                "samWithReceiver",
                "org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverExtension"),
            ProjectSchemaEntry(
                "org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverExtension",
                "ext",
                "org.gradle.api.plugins.ExtraPropertiesExtension"))),
        conventions = javaProjectSchema.conventions,
        configurations = (javaProjectSchema.configurations + listOf("embeddedKotlin", "kapt", "kaptTest")).sorted())
