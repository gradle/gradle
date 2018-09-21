package org.gradle.kotlin.dsl.accessors

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class KotlinDslAccessorsSnapshotTaskIntegrationTest : AbstractIntegrationTest() {

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
                .mapValues { (_, entry) ->
                    entry.sortedForComparison()
                }

        val expectedSchema =
            mapOf(
                ":" to rootProjectSchema.sortedForComparison(),
                ":sub-groovy" to groovyProjectSchema.sortedForComparison(),
                ":sub-java" to javaProjectSchema.sortedForComparison(),
                ":sub-kotlin-dsl" to kotlinDslProjectSchema.sortedForComparison())

        val projectPaths = listOf(":", ":sub-java", ":sub-groovy", ":sub-kotlin-dsl")

        assertThat(generatedSchema.keys, equalTo(projectPaths.toSet()))

        projectPaths.forEach { projectPath ->
            mapOf(
                "extensions" to { schema: ProjectSchema<String> -> schema.extensions },
                "conventions" to { schema: ProjectSchema<String> -> schema.conventions },
                "containerElements" to { schema: ProjectSchema<String> -> schema.containerElements },
                "configurations" to { schema: ProjectSchema<String> -> schema.configurations }
            ).forEach { schemaPartName, schemaPartSupplier ->
                schemaPartSupplier(generatedSchema[projectPath]!!)
                assertThat(
                    "$schemaPartName of '$projectPath'",
                    schemaPartSupplier(generatedSchema[projectPath]!!),
                    equalTo(schemaPartSupplier(expectedSchema[projectPath]!!))
                )
            }
        }
    }
}


private
fun ProjectSchema<String>.sortedForComparison(): ProjectSchema<String> =
    ProjectSchema(
        extensions.sortedWith(projectSchemaEntryComparator),
        conventions.sortedWith(projectSchemaEntryComparator),
        containerElements.sortedWith(projectSchemaEntryComparator),
        configurations.sorted()
    )


private
val projectSchemaEntryComparator = Comparator { left: ProjectSchemaEntry<String>, right: ProjectSchemaEntry<String> ->
    "${left.target}#${left.name}#${left.type}".compareTo("${right.target}#${right.name}#${right.type}")
}


private
operator fun ProjectSchema<String>.plus(schema: ProjectSchema<String>) =
    ProjectSchema(
        extensions + schema.extensions,
        conventions + schema.conventions,
        containerElements + schema.containerElements,
        configurations + schema.configurations
    )


private
val defaultProjectSchema = ProjectSchema(
    extensions = listOf(
        ProjectSchemaEntry("org.gradle.api.Project", "ext", "org.gradle.api.plugins.ExtraPropertiesExtension")
    ),
    conventions = emptyList(),
    containerElements = emptyList(),
    configurations = emptyList()
)


private
val baseProjectSchema = defaultProjectSchema + ProjectSchema(
    extensions = listOf(
        ProjectSchemaEntry("org.gradle.api.Project", "defaultArtifacts", "org.gradle.api.internal.plugins.DefaultArtifactPublicationSet"),
        ProjectSchemaEntry("org.gradle.api.internal.plugins.DefaultArtifactPublicationSet", "ext", "org.gradle.api.plugins.ExtraPropertiesExtension")
    ),
    conventions = listOf(
        ProjectSchemaEntry("org.gradle.api.Project", "base", "org.gradle.api.plugins.BasePluginConvention")
    ),
    containerElements = listOf(
        existingConfigurationContainerElement("archives"),
        existingConfigurationContainerElement("default")
    ),
    configurations = listOf(
        "archives", "default"
    )
)


private
val rootProjectSchema = baseProjectSchema + ProjectSchema(
    extensions = emptyList(),
    conventions = emptyList(),
    containerElements = emptyList(),
    configurations = emptyList()
)


private
val javaProjectSchema: ProjectSchema<String> = listOf(
    "annotationProcessor",
    "apiElements",
    "compile", "compileClasspath", "compileOnly",
    "implementation",
    "runtime", "runtimeClasspath", "runtimeElements", "runtimeOnly",
    "testAnnotationProcessor",
    "testCompile", "testCompileClasspath", "testCompileOnly",
    "testImplementation",
    "testRuntime", "testRuntimeClasspath", "testRuntimeOnly"
).let { configurationNames ->

    baseProjectSchema + ProjectSchema(
        extensions = listOf(
            ProjectSchemaEntry("org.gradle.api.Project", "reporting", "org.gradle.api.reporting.ReportingExtension"),
            ProjectSchemaEntry("org.gradle.api.Project", "sourceSets", "org.gradle.api.tasks.SourceSetContainer"),
            ProjectSchemaEntry("org.gradle.api.Project", "java", "org.gradle.api.plugins.JavaPluginExtension"),
            ProjectSchemaEntry("org.gradle.api.plugins.JavaPluginExtension", "ext", "org.gradle.api.plugins.ExtraPropertiesExtension"),
            ProjectSchemaEntry("org.gradle.api.reporting.ReportingExtension", "ext", "org.gradle.api.plugins.ExtraPropertiesExtension"),
            ProjectSchemaEntry("org.gradle.api.tasks.SourceSetContainer", "ext", "org.gradle.api.plugins.ExtraPropertiesExtension"),
            ProjectSchemaEntry("org.gradle.api.tasks.SourceSet", "ext", "org.gradle.api.plugins.ExtraPropertiesExtension")
        ),
        conventions = listOf(
            ProjectSchemaEntry("org.gradle.api.Project", "java", "org.gradle.api.plugins.JavaPluginConvention")
        ),
        containerElements = configurationNames.map(::existingConfigurationContainerElement),
        configurations = configurationNames
    )
}


private
val groovyProjectSchema: ProjectSchema<String> = javaProjectSchema + ProjectSchema(
    extensions = listOf(
        ProjectSchemaEntry("org.gradle.api.Project", "groovyRuntime", "org.gradle.api.tasks.GroovyRuntime"),
        ProjectSchemaEntry("org.gradle.api.tasks.GroovyRuntime", "ext", "org.gradle.api.plugins.ExtraPropertiesExtension")
    ),
    conventions = emptyList(),
    containerElements = emptyList(),
    configurations = emptyList()
)


private
val kotlinDslProjectSchema: ProjectSchema<String> = listOf(
    "api", "apiDependenciesMetadata",
    "compileOnlyDependenciesMetadata",
    "embeddedKotlin",
    "implementationDependenciesMetadata",
    "kapt",
    "kaptTest",
    "kotlinCompilerClasspath",
    "kotlinCompilerPluginClasspath",
    "runtimeOnlyDependenciesMetadata",
    "testApi", "testApiDependenciesMetadata",
    "testCompileOnlyDependenciesMetadata",
    "testImplementationDependenciesMetadata",
    "testRuntimeOnlyDependenciesMetadata"
).let { configurationNames ->
    javaProjectSchema + ProjectSchema(
        extensions = listOf(
            ProjectSchemaEntry("org.gradle.api.Project", "kotlin", "org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension"),
            ProjectSchemaEntry("org.gradle.api.Project", "kotlinDslPluginOptions", "org.gradle.kotlin.dsl.plugins.dsl.KotlinDslPluginOptions"),
            ProjectSchemaEntry("org.gradle.api.Project", "samWithReceiver", "org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverExtension"),
            ProjectSchemaEntry("org.gradle.api.Project", "kotlinScripting", "org.jetbrains.kotlin.gradle.scripting.ScriptingExtension"),
            ProjectSchemaEntry("org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension", "experimental", "org.jetbrains.kotlin.gradle.dsl.ExperimentalExtension"),
            ProjectSchemaEntry("org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension", "sourceSets", "org.gradle.api.NamedDomainObjectContainer<org.jetbrains.kotlin.gradle.plugin.sources.DefaultKotlinSourceSet>"),
            ProjectSchemaEntry("org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension", "ext", "org.gradle.api.plugins.ExtraPropertiesExtension"),
            ProjectSchemaEntry("org.jetbrains.kotlin.gradle.dsl.ExperimentalExtension", "ext", "org.gradle.api.plugins.ExtraPropertiesExtension"),
            ProjectSchemaEntry("org.gradle.api.NamedDomainObjectContainer<org.jetbrains.kotlin.gradle.plugin.sources.DefaultKotlinSourceSet>", "ext", "org.gradle.api.plugins.ExtraPropertiesExtension"),
            ProjectSchemaEntry("org.jetbrains.kotlin.samWithReceiver.gradle.SamWithReceiverExtension", "ext", "org.gradle.api.plugins.ExtraPropertiesExtension"),
            ProjectSchemaEntry("org.jetbrains.kotlin.gradle.scripting.ScriptingExtension", "ext", "org.gradle.api.plugins.ExtraPropertiesExtension")
        ),
        conventions = emptyList(),
        containerElements = configurationNames.map(::existingConfigurationContainerElement),
        configurations = configurationNames
    )
}


private
fun existingConfigurationContainerElement(name: String) =
    ProjectSchemaEntry("org.gradle.api.NamedDomainObjectContainer<org.gradle.api.artifacts.Configuration>", name, "org.gradle.api.internal.artifacts.configurations.DefaultConfiguration")
