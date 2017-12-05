package org.gradle.kotlin.dsl.accessors

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class GenerateProjectSchemaTest : AbstractIntegrationTest() {

    @Test
    fun `writes multi-project schema to gradle slash project dash schema dot json`() {

        withBuildScript("""
            plugins { java }
        """)

        build("kotlinDslAccessorsSnapshot")

        val generatedSchema =
            loadMultiProjectSchemaFrom(
                existing("gradle/project-schema.json"))

        val expectedSchema =
            mapOf(
                ":" to ProjectSchema(
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
                            "org.gradle.api.plugins.ExtraPropertiesExtension"),
                        ProjectSchemaEntry(
                            "org.gradle.api.Project",
                            "reporting",
                            "org.gradle.api.reporting.ReportingExtension"),
                        ProjectSchemaEntry(
                            "org.gradle.api.reporting.ReportingExtension",
                            "ext",
                            "org.gradle.api.plugins.ExtraPropertiesExtension")),
                    conventions = listOf(
                        ProjectSchemaEntry(
                            "org.gradle.api.Project",
                            "base",
                            "org.gradle.api.plugins.BasePluginConvention"),
                        ProjectSchemaEntry(
                            "org.gradle.api.Project",
                            "java",
                            "org.gradle.api.plugins.JavaPluginConvention")),
                    configurations = listOf(
                        "annotationProcessor",
                        "apiElements", "archives", "compile", "compileClasspath", "compileOnly", "default",
                        "implementation", "runtime", "runtimeClasspath", "runtimeElements", "runtimeOnly",
                        "testAnnotationProcessor",
                        "testCompile", "testCompileClasspath", "testCompileOnly", "testImplementation",
                        "testRuntime", "testRuntimeClasspath", "testRuntimeOnly")))

        assertThat(
            generatedSchema,
            equalTo(expectedSchema))
    }
}
