/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.accessors

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.same
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal
import org.gradle.api.internal.plugins.ExtensionContainerInternal
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.TaskContainerInternal
import org.gradle.api.reflect.TypeOf.parameterizedTypeOf
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.concurrent.withSynchronousIO
import org.gradle.kotlin.dsl.fixtures.AbstractDslTest
import org.gradle.kotlin.dsl.fixtures.eval
import org.gradle.kotlin.dsl.fixtures.testRuntimeClassPath
import org.gradle.kotlin.dsl.fixtures.withClassLoaderFor
import org.gradle.kotlin.dsl.support.compileToDirectory
import org.gradle.kotlin.dsl.support.loggerFor
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.gradle.nativeplatform.BuildType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.anyMap
import java.io.File
import java.lang.reflect.Method
import java.lang.reflect.Modifier.PUBLIC
import java.lang.reflect.Modifier.STATIC


class ProjectAccessorsClassPathTest : AbstractDslTest() {

    abstract class CustomConvention

    @Test
    fun `#buildAccessorsFor (Kotlin types)`() {

        // given:
        val schema =
            TypedProjectSchema(
                extensions = listOf(
                    entry<Project, () -> Unit>("function0"),
                    entry<Project, (String) -> Unit>("function1"),
                    entry<Project, (Int, Double) -> Boolean>("function2")
                ),
                containerElements = listOf(),
                conventions = listOf(),
                tasks = listOf(),
                configurations = listOf()
            )

        val function0 = mock<() -> Unit>()
        val function1 = mock<(String) -> Unit>()
        val function2 = mock<(Int, Double) -> Boolean>()
        val extensions = mock<ExtensionContainerInternal> {
            on { getByName("function0") } doReturn function0
            on { getByName("function1") } doReturn function1
            on { getByName("function2") } doReturn function2
        }
        val project = mock<ProjectInternal> {
            on { getExtensions() } doReturn extensions
        }

        // when:
        evalWithAccessorsFor(
            schema = schema,
            target = project,
            script = """
                val a: () -> Unit = function0
                val b: (String) -> Unit = function1
                val c: (Int, Double) -> Boolean = function2
            """
        )

        // then:
        inOrder(extensions) {
            verify(extensions).getByName("function0")
            verify(extensions).getByName("function1")
            verify(extensions).getByName("function2")
        }
    }

    @Test
    fun `#buildAccessorsFor (bytecode)`() {

        testAccessorsBuiltBy(::buildAccessorsFor)
    }

    @Test
    fun `#buildAccessorsFor (source)`() {

        testAccessorsBuiltBy(::buildAccessorsFromSourceFor)
    }

    @Test
    fun `#buildAccessorsFor (deprecated configurations)`() {
        val schema =
            TypedProjectSchema(
                extensions = listOf(),
                conventions = listOf(),
                containerElements = listOf(),
                tasks = listOf(),
                configurations = listOf(
                    ConfigurationEntry("api"),
                    ConfigurationEntry("implementation"),
                    ConfigurationEntry("compile", listOf("api", "implementation"))
                )
            )

        val srcDir = newFolder("src")
        val binDir = newFolder("bin")

        withClassLoaderFor(binDir) {
            // when:
            buildAccessorsFromSourceFor(
                schema,
                testRuntimeClassPath,
                srcDir,
                binDir
            )

            val binaryAccessorsDir = File(binDir, "org/gradle/kotlin/dsl")

            // then:
            schema.configurations.forEach { config ->
                val name = config.target
                val className = "${name.uppercaseFirstChar()}ConfigurationAccessorsKt"
                val classFile = File(binaryAccessorsDir, "$className.class")

                require(classFile.exists())

                loadClass("org.gradle.kotlin.dsl.$className").run {
                    dependencyHandlerExtensionMethods(name).forEach {
                        assertEquals(
                            isDeprecated(it),
                            config.hasDeclarationDeprecations()
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `#buildAccessorsFor (default package types)`() {

        // given:
        val defaultPackageTypes = classPathWith {
            publicClass("ExtensionReceiver")
            publicClass("ConventionReceiver")
            publicInterface("Entry")
            publicInterface("Element", "Entry")
            publicInterface("CustomTask", Task::class.qualifiedName!!)
        }
        withClassLoaderFor(defaultPackageTypes) {
            val entryType = schemaTypeFor("Entry")
            val schema =
                TypedProjectSchema(
                    extensions = listOf(
                        ProjectSchemaEntry(schemaTypeFor("ExtensionReceiver"), "extension", entryType)
                    ),
                    conventions = listOf(
                        ProjectSchemaEntry(schemaTypeFor("ConventionReceiver"), "convention", entryType)
                    ),
                    containerElements = listOf(
                        ProjectSchemaEntry(namedDomainObjectContainerOf(entryType), "element", schemaTypeFor("Element"))
                    ),
                    tasks = listOf(
                        ProjectSchemaEntry(SchemaType.of<TaskContainer>(), "task", schemaTypeFor("CustomTask"))
                    ),
                    configurations = listOf()
                )

            val srcDir = newFolder("src")
            val binDir = newFolder("bin")

            // when:
            buildAccessorsFromSourceFor(
                schema,
                testRuntimeClassPath + defaultPackageTypes,
                srcDir,
                binDir
            )

            // then:
            require(
                kotlinFilesIn(srcDir).isNotEmpty()
            )
        }
    }

    private
    fun Class<*>.dependencyHandlerExtensionMethods(name: String): List<Method> {
        return declaredMethods.filter(Method::isPublicStatic)
            .filter { it.name == name }
            .filter { it.parameterCount > 0 }
            .filter { it.parameterTypes[0].simpleName == "DependencyHandler" }
    }

    private
    fun isDeprecated(it: Method) = it.annotations.map { it.annotationClass }.contains(Deprecated::class)

    private
    fun buildAccessorsFromSourceFor(
        schema: TypedProjectSchema,
        classPath: ClassPath,
        srcDir: File,
        binDir: File
    ) {
        buildAccessorsFor(
            schema,
            classPath,
            srcDir,
            newFolder("ignored")
        )
        require(
            compileToDirectory(
                binDir,
                JavaVersion.current(),
                "bin",
                kotlinFilesIn(srcDir),
                loggerFor<ProjectAccessorsClassPathTest>(),
                classPath.asFiles
            )
        )
    }

    private
    fun kotlinFilesIn(srcDir: File) =
        srcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private
    fun testAccessorsBuiltBy(buildAccessorsFor: (TypedProjectSchema, ClassPath, File, File) -> Unit) {

        // given:
        val schema =
            TypedProjectSchema(
                extensions = listOf(
                    entry<Project, SourceSetContainer>("sourceSets"),
                    entry<Project, NamedDomainObjectContainer<BuildType>>("buildTypes")
                ),
                containerElements = listOf(
                    entry<SourceSetContainer, SourceSet>("main")
                ),
                conventions = listOf(
                    entry<Project, CustomConvention>("customConvention")
                ),
                tasks = listOf(
                    entry<TaskContainer, Delete>("clean")
                ),
                configurations = listOf(ConfigurationEntry("api"))
            )

        val apiConfiguration = mock<NamedDomainObjectProvider<Configuration>>()
        val configurations = mock<RoleBasedConfigurationContainerInternal> {
            on { named(any<String>(), any<Class<Configuration>>()) } doReturn apiConfiguration
        }
        val sourceSet = mock<NamedDomainObjectProvider<SourceSet>>()
        val sourceSets = mock<SourceSetContainer> {
            on { named(any<String>(), eq(SourceSet::class.java)) } doReturn sourceSet
        }
        val extensions = mock<ExtensionContainerInternal> {
            on { getByName(any()) } doReturn sourceSets
        }
        val constraint = mock<DependencyConstraint>()
        val constraints = mock<DependencyConstraintHandler> {
            on { add(any(), any()) } doReturn constraint
            on { add(any(), any(), any()) } doReturn constraint
        }
        val dependency = mock<ExternalModuleDependency>()
        val projectDependency = mock<ProjectDependency>()
        val dependencies = mock<DependencyHandler> {
            on { create(any()) } doReturn dependency
            on { getConstraints() } doReturn constraints
            on { project(anyMap<String, Any?>()) } doReturn projectDependency
        }
        val clean = mock<TaskProvider<Delete>>()
        val tasks = mock<TaskContainerInternal> {
            on { named(any<String>(), eq(Delete::class.java)) } doReturn clean
        }
        val customConvention = mock<CustomConvention>()
        @Suppress("deprecation")
        val convention = mock<org.gradle.api.plugins.Convention> {
            on { plugins } doReturn mapOf("customConvention" to customConvention)
        }
        val project = mock<ProjectInternal> {
            on { getConfigurations() } doReturn configurations
            on { getExtensions() } doReturn extensions
            on { getDependencies() } doReturn dependencies
            on { getTasks() } doReturn tasks
            on { @Suppress("deprecation") getConvention() } doReturn convention
        }

        // when:
        evalWithAccessorsFor(
            schema = schema,
            target = project,
            buildAccessorsFor = buildAccessorsFor,
            script = """
                val a: NamedDomainObjectProvider<Configuration> = configurations.api

                val b: Dependency? = dependencies.api("module")

                val c: SourceSetContainer = sourceSets

                val d: Unit = sourceSets {}

                val e: NamedDomainObjectProvider<SourceSet> = sourceSets.main

                val f: TaskProvider<Delete> = tasks.clean

                val g: Dependency? = dependencies.api("module") {
                    val module: ExternalModuleDependency = this
                }

                val h: Unit = buildTypes {
                    val container: NamedDomainObjectContainer<BuildType> = this
                }

                val i: org.gradle.kotlin.dsl.accessors.ProjectAccessorsClassPathTest.CustomConvention = customConvention

                val j: Unit = customConvention {
                    val convention: org.gradle.kotlin.dsl.accessors.ProjectAccessorsClassPathTest.CustomConvention = this
                }

                val k: DependencyConstraint = dependencies.constraints.api("direct:accessor:1.0")
                val l: DependencyConstraint = dependencies.constraints.api("direct:accessor-with-action") {
                    val constraint: DependencyConstraint = this
                }

                val projectDependency = dependencies.project(":core")
                val m: ProjectDependency = dependencies.api(projectDependency) {
                    val dependency: ProjectDependency = this
                }

                val n: ExternalModuleDependency = dependencies.api(group = "g", name = "n")

                val o: ExternalModuleDependency = dependencies.api(group = "g", name = "n") {
                    val dependency: ExternalModuleDependency = this
                }

                fun Project.canUseAccessorsFromConfigurationsScope() {
                    configurations {
                        api {
                            outgoing.variants
                        }
                    }
                }

                fun Project.canUseContainerElementAccessors() {
                    sourceSets {
                        main {
                        }
                    }
                }
            """
        )

        // then:
        inOrder(
            project,
            configurations,
            apiConfiguration,
            extensions,
            sourceSets,
            dependencies,
            tasks,
            convention,
            customConvention,
            constraints
        ) {
            // val a
            verify(project).configurations
            verify(configurations).named("api", Configuration::class.java)

            // val b
            verify(project).dependencies
            verify(dependencies).add("api", "module")

            // val c
            verify(project).extensions
            verify(extensions).getByName("sourceSets")

            // val d
            verify(project).extensions
            verify(extensions).configure(eq("sourceSets"), any<Action<*>>())

            // val e
            verify(project).extensions
            verify(extensions).getByName("sourceSets")
            verify(sourceSets).named("main", SourceSet::class.java)

            // val f
            verify(project).tasks
            verify(tasks).named("clean", Delete::class.java)

            // val g
            verify(project).dependencies
            verify(dependencies).create("module")
            verify(dependencies).add("api", dependency)

            // val h
            verify(project).extensions
            verify(extensions).configure(eq("buildTypes"), any<Action<*>>())

            // val i
            @Suppress("deprecation")
            verify(project).convention
            @Suppress("deprecation")
            verify(convention).plugins

            // val j
            @Suppress("deprecation")
            verify(project).convention
            @Suppress("deprecation")
            verify(convention).plugins

            // val k
            verify(project).dependencies
            verify(dependencies).constraints
            verify(constraints).add(eq("api"), eq("direct:accessor:1.0"))

            // val l
            verify(project).dependencies
            verify(dependencies).constraints
            verify(constraints).add(eq("api"), eq("direct:accessor-with-action"), any())

            // val m
            verify(project).dependencies
            verify(dependencies).project(path = ":core")
            verify(project).dependencies
            verify(dependencies).add(eq("api"), same(projectDependency))

            // val n
            verify(project).dependencies
            verify(dependencies).create(mapOf("group" to "g", "name" to "n"))
            verify(dependencies).add("api", dependency)

            // val o
            verify(project).dependencies
            verify(dependencies).create(mapOf("group" to "g", "name" to "n"))
            verify(dependencies).add("api", dependency)

            verifyNoMoreInteractions()
        }
    }

    private
    fun evalWithAccessorsFor(
        schema: TypedProjectSchema,
        target: Project,
        script: String,
        classPath: ClassPath = testRuntimeClassPath,
        buildAccessorsFor: (TypedProjectSchema, ClassPath, File, File) -> Unit = ::buildAccessorsFor
    ) {

        val srcDir = newFolder("src")
        val binDir = newFolder("bin")

        buildAccessorsFor(schema, classPath, srcDir, binDir)

        eval(
            script = script,
            target = target,
            baseCacheDir = kotlinDslEvalBaseCacheDir,
            baseTempDir = kotlinDslEvalBaseTempDir,
            scriptCompilationClassPath = DefaultClassPath.of(binDir) + classPath,
            scriptRuntimeClassPath = DefaultClassPath.of(binDir)
        )
    }

    private
    fun buildAccessorsFor(schema: TypedProjectSchema, classPath: ClassPath, srcDir: File, binDir: File) {
        withSynchronousIO {
            buildAccessorsFor(schema, classPath, srcDir, binDir)
        }
    }
}


internal
fun namedDomainObjectContainerOf(elementType: SchemaType) =
    SchemaType(parameterizedTypeOf(typeOf<NamedDomainObjectContainer<*>>(), elementType.value))


internal
inline fun <reified ReceiverType, reified EntryType> entry(name: String): ProjectSchemaEntry<SchemaType> =
    ProjectSchemaEntry(SchemaType.of<ReceiverType>(), name, SchemaType.of<EntryType>())


private
fun Method.isPublicStatic() = (modifiers and STATIC == STATIC) &&
    (modifiers and PUBLIC == PUBLIC)
