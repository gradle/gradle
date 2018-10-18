package org.gradle.kotlin.dsl.accessors

import groovy.json.JsonOutput
import groovy.json.JsonOutput.prettyPrint
import groovy.json.JsonSlurper

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.fixtures.normalisedPath
import org.gradle.kotlin.dsl.support.serviceOf

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File


const val PROJECT_SCHEMA_JSON_PATH = "build/project-schema.json"


class ProjectSchemaIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `project schema for a java, groovy and kotlin-dsl multi-project build`() {

        val dumpTaskJar = withClassJar("dump-task.jar", DumpJsonProjectSchema::class.java)

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

            buildscript { dependencies { classpath(files("${dumpTaskJar.normalisedPath}")) } }
            tasks.register<${DumpJsonProjectSchema::class.java.name}>("dumpJsonProjectSchema")
        """)

        build("dumpJsonProjectSchema", "-s")

        val generatedSchema =
            loadMultiProjectSchemaFrom(
                existing(PROJECT_SCHEMA_JSON_PATH))
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
                "tasks" to { schema: ProjectSchema<String> -> schema.tasks },
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
        tasks.sortedWith(projectSchemaEntryComparator),
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
        tasks + schema.tasks,
        containerElements + schema.containerElements,
        configurations + schema.configurations
    )


private
val defaultProjectSchema = ProjectSchema(
    extensions = listOf(
        ProjectSchemaEntry("org.gradle.api.Project", "ext", "org.gradle.api.plugins.ExtraPropertiesExtension")
    ),
    conventions = emptyList(),
    tasks = listOf(
        existingTaskContainerElement("buildEnvironment", "org.gradle.api.tasks.diagnostics.BuildEnvironmentReportTask"),
        existingTaskContainerElement("components", "org.gradle.api.reporting.components.ComponentReport"),
        existingTaskContainerElement("dependencies", "org.gradle.api.tasks.diagnostics.DependencyReportTask"),
        existingTaskContainerElement("dependencyInsight", "org.gradle.api.tasks.diagnostics.DependencyInsightReportTask"),
        existingTaskContainerElement("dependentComponents", "org.gradle.api.reporting.dependents.DependentComponentsReport"),
        existingTaskContainerElement("help", "org.gradle.configuration.Help"),
        existingTaskContainerElement("model", "org.gradle.api.reporting.model.ModelReport"),
        existingTaskContainerElement("projects", "org.gradle.api.tasks.diagnostics.ProjectReportTask"),
        existingTaskContainerElement("properties", "org.gradle.api.tasks.diagnostics.PropertyReportTask"),
        existingTaskContainerElement("tasks", "org.gradle.api.tasks.diagnostics.TaskReportTask")
    ),
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
    tasks = listOf(
        existingTaskContainerElement("assemble"),
        existingTaskContainerElement("build"),
        existingTaskContainerElement("check"),
        existingTaskContainerElement("clean", "org.gradle.api.tasks.Delete")
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
    tasks = listOf(
        existingTaskContainerElement("init", "org.gradle.buildinit.tasks.InitBuild"),
        existingTaskContainerElement("wrapper", "org.gradle.api.tasks.wrapper.Wrapper"),
        existingTaskContainerElement("kotlinDslAccessorsReport", "org.gradle.kotlin.dsl.accessors.tasks.PrintAccessors"),
        existingTaskContainerElement("dumpJsonProjectSchema", DumpJsonProjectSchema::class.java.name)
    ),
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
        tasks = listOf(
            existingTaskContainerElement("buildDependents"),
            existingTaskContainerElement("buildNeeded"),
            existingTaskContainerElement("classes"),
            existingTaskContainerElement("compileJava", "org.gradle.api.tasks.compile.JavaCompile"),
            existingTaskContainerElement("compileTestJava", "org.gradle.api.tasks.compile.JavaCompile"),
            existingTaskContainerElement("jar", "org.gradle.api.tasks.bundling.Jar"),
            existingTaskContainerElement("javadoc", "org.gradle.api.tasks.javadoc.Javadoc"),
            existingTaskContainerElement("processResources", "org.gradle.language.jvm.tasks.ProcessResources"),
            existingTaskContainerElement("processTestResources", "org.gradle.language.jvm.tasks.ProcessResources"),
            existingTaskContainerElement("test", "org.gradle.api.tasks.testing.Test"),
            existingTaskContainerElement("testClasses")
        ),
        containerElements = configurationNames.map(::existingConfigurationContainerElement) + listOf(
            existingSourceSetContainerElement("main"),
            existingSourceSetContainerElement("test")
        ),
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
    tasks = listOf(
        existingTaskContainerElement("compileGroovy", "org.gradle.api.tasks.compile.GroovyCompile"),
        existingTaskContainerElement("compileTestGroovy", "org.gradle.api.tasks.compile.GroovyCompile"),
        existingTaskContainerElement("groovydoc", "org.gradle.api.tasks.javadoc.Groovydoc")
    ),
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
    "kotlinNativeCompilerPluginClasspath",
    "kotlinScriptDef", "testKotlinScriptDef",
    "runtimeOnlyDependenciesMetadata",
    "testApi", "testApiDependenciesMetadata",
    "testCompileOnlyDependenciesMetadata",
    "testImplementationDependenciesMetadata",
    "testRuntimeOnlyDependenciesMetadata"
).let { configurationNames ->
    javaProjectSchema + ProjectSchema(
        extensions = listOf(
            ProjectSchemaEntry("org.gradle.api.Project", "gradlePlugin", "org.gradle.plugin.devel.GradlePluginDevelopmentExtension"),
            ProjectSchemaEntry("org.gradle.plugin.devel.GradlePluginDevelopmentExtension", "ext", "org.gradle.api.plugins.ExtraPropertiesExtension"),
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
        tasks = listOf(
            existingTaskContainerElement("mainClasses"),
            existingTaskContainerElement("compileKotlin", "org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
            existingTaskContainerElement("compileTestKotlin", "org.jetbrains.kotlin.gradle.tasks.KotlinCompile"),
            existingTaskContainerElement("generateScriptPluginAdapters"),
            existingTaskContainerElement("inspectClassesForKotlinIC", "org.jetbrains.kotlin.gradle.tasks.InspectClassesForMultiModuleIC"),
            existingTaskContainerElement("pluginDescriptors", "org.gradle.plugin.devel.tasks.GeneratePluginDescriptors"),
            existingTaskContainerElement("pluginUnderTestMetadata", "org.gradle.plugin.devel.tasks.PluginUnderTestMetadata"),
            existingTaskContainerElement("validateTaskProperties", "org.gradle.plugin.devel.tasks.ValidateTaskProperties")
        ),
        containerElements = configurationNames.map(::existingConfigurationContainerElement) + listOf(
            ProjectSchemaEntry("org.gradle.api.NamedDomainObjectContainer<org.jetbrains.kotlin.gradle.plugin.sources.DefaultKotlinSourceSet>", "main", "org.jetbrains.kotlin.gradle.plugin.sources.DefaultKotlinSourceSet"),
            ProjectSchemaEntry("org.gradle.api.NamedDomainObjectContainer<org.jetbrains.kotlin.gradle.plugin.sources.DefaultKotlinSourceSet>", "test", "org.jetbrains.kotlin.gradle.plugin.sources.DefaultKotlinSourceSet")
        ),
        configurations = configurationNames
    )
}


private
fun existingTaskContainerElement(name: String, type: String = "org.gradle.api.DefaultTask") =
    ProjectSchemaEntry("org.gradle.api.tasks.TaskContainer", name, type)


private
fun existingConfigurationContainerElement(name: String) =
    ProjectSchemaEntry("org.gradle.api.NamedDomainObjectContainer<org.gradle.api.artifacts.Configuration>", name, "org.gradle.api.artifacts.Configuration")


private
fun existingSourceSetContainerElement(name: String) =
    ProjectSchemaEntry("org.gradle.api.tasks.SourceSetContainer", name, "org.gradle.api.tasks.SourceSet")


open class DumpJsonProjectSchema : DefaultTask() {

    @TaskAction
    fun dumpJsonProjectSchema() = project.run {
        val schema = allprojects.map { it.path to projectSchemaProvider.schemaFor(it) }.toMap()
            .mapValues { it.value.withKotlinTypeStrings() }
        file(PROJECT_SCHEMA_JSON_PATH).run {
            parentFile.mkdirs()
            writeText(prettyPrint(toJson(schema)))
        }
    }

    private
    fun toJson(multiProjectStringSchema: Map<String, ProjectSchema<String>>): String =
        JsonOutput.toJson(multiProjectStringSchema)

    private
    val projectSchemaProvider: ProjectSchemaProvider
        get() = project.serviceOf()
}


class DumpJsonProjectSchemaPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        tasks.register("dumpJsonProjectSchema", DumpJsonProjectSchema::class.java)
    }
}


@Suppress("unchecked_cast")
fun loadMultiProjectSchemaFrom(file: File) =
    (JsonSlurper().parse(file) as Map<String, Map<String, *>>).mapValues { (_, value) ->
        ProjectSchema(
            extensions = loadSchemaEntryListFrom(value["extensions"]),
            conventions = loadSchemaEntryListFrom(value["conventions"]),
            tasks = loadSchemaEntryListFrom(value["tasks"]),
            containerElements = loadSchemaEntryListFrom(value["containerElements"]),
            configurations = value["configurations"] as? List<String> ?: emptyList())
    }


@Suppress("unchecked_cast")
private
fun loadSchemaEntryListFrom(extensions: Any?): List<ProjectSchemaEntry<String>> =
    when (extensions) {
        is Map<*, *> -> // <0.17 format
            (extensions as? Map<String, String>)?.map {
                ProjectSchemaEntry(
                    Project::class.java.name,
                    it.key,
                    it.value)
            } ?: emptyList()
        is List<*> -> // >=0.17 format
            (extensions as? List<Map<String, String>>)?.map {
                ProjectSchemaEntry(
                    it.getValue("target"),
                    it.getValue("name"),
                    it.getValue("type"))
            } ?: emptyList()
        else -> emptyList()
    }
