package org.gradle.script.lang.kotlin

import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.Project

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

class DependencyHandlerExtensionsTest {

    @Test
    fun `given group, name, version, configuration, classifier and ext, 'create' extension will build corresponding map`() {

        val expectedModuleMap = mapOf(
            "group" to "g",
            "name" to "n",
            "version" to "v",
            "configuration" to "cfg",
            "classifier" to "cls",
            "ext" to "x")

        val dependencies: DependencyHandler = mock()
        val dependency: ExternalModuleDependency = mock()
        whenever(dependencies.create(expectedModuleMap)).thenReturn(dependency)

        assertThat(
            dependencies.create(
                group = "g",
                name = "n",
                version = "v",
                configuration = "cfg",
                classifier = "cls",
                ext = "x"),
            sameInstance(dependency))
    }

    @Test
    fun `given group and module, 'exclude' extension will build corresponding map`() {

        val dependencies = KotlinDependencyHandler(mock())
        val dependency: ExternalModuleDependency = mock()
        val events = mutableListOf<String>()
        whenever(dependencies.create("dependency")).then {
            events.add("created")
            dependency
        }
        whenever(dependency.exclude(mapOf("group" to "g", "module" to "m"))).then {
            events.add("configured")
            dependency
        }
        whenever(dependencies.add("configuration", dependency)).then {
            events.add("added")
            dependency
        }

        dependencies {

            "configuration"("dependency") {
                val configuredDependency =
                    exclude(group = "g", module = "m")
                assertThat(
                    configuredDependency,
                    sameInstance(dependency))
            }
        }

        assertThat(
            events,
            equalTo(listOf("created", "configured", "added")))
    }

    @Test
    fun `given path and configuration, 'project' extension will build corresponding map`() {

        val dependencies = KotlinDependencyHandler(mock())
        val dependency: ProjectDependency = mock()
        val events = mutableListOf<String>()
        val expectedProjectMap = mapOf("path" to ":project", "configuration" to "default")
        whenever(dependencies.project(expectedProjectMap)).then {
            events.add("created")
            dependency
        }
        whenever(dependencies.add("configuration", dependency)).then {
            events.add("added")
            dependency
        }
        val project: Project = mock()
        whenever(dependency.dependencyProject).thenReturn(project)

        dependencies {

            "configuration"(project(path = ":project", configuration = "default")) {
                events.add("configured")
                assertThat(
                    dependencyProject,
                    sameInstance(project))
            }
        }

        assertThat(
            events,
            equalTo(listOf("created", "configured", "added")))
    }

    @Test
    fun `given extensions for common configurations, they will delegate to the appropriate methods`() {

        val dependencies = KotlinDependencyHandler(mock())
        whenever(dependencies.add(any(), any())).then { it.getArgument(1) }

        val externalModuleDependency: ExternalModuleDependency = mock()
        whenever(dependencies.create(any<Map<String, String>>())).thenReturn(externalModuleDependency)

        val projectDependency: ProjectDependency = mock()
        whenever(dependencies.project(any())).thenReturn(projectDependency)

        for (dependency in listOf(externalModuleDependency, projectDependency)) {
            whenever(dependency.exclude(any())).thenReturn(dependency)
        }

        dependencies {

            default(group = "org.gradle", name = "foo", version = "1.0") {
                isForce = true
            }

            compile(group = "org.gradle", name = "bar") {
                exclude(module = "foo")
            }

            runtime("org.gradle:baz:1.0-SNAPSHOT") {
                isChanging = true
                isTransitive = false
            }

            testCompile(group = "junit", name = "junit")

            testRuntime(project(":core")) {
                exclude(group = "org.gradle")
            }
        }
    }
}
