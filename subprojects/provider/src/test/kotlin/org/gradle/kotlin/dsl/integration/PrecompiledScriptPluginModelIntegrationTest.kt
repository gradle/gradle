package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.FoldersDsl

import org.junit.Test

import java.io.File


class PrecompiledScriptPluginModelIntegrationTest : ScriptModelIntegrationTest() {

    @Test
    fun `given a single project build, the classpath of a precompiled script plugin is the compile classpath of its enclosing source-set`() {

        withProjectRoot(newDir("single-project-build")) {

            val implementationDependency =
                withFile("implementation.jar")

            val classpathDependency =
                withFile("classpath.jar")

            withDefaultSettings()

            withBuildScript("""
                plugins {
                    `kotlin-dsl`
                }

                buildscript {
                    dependencies {
                        classpath(files("${classpathDependency.name}"))
                    }
                }

                dependencies {
                    implementation(files("${implementationDependency.name}"))
                }
            """)

            val precompiledScriptPlugin =
                withFile("src/main/kotlin/my-plugin.gradle.kts")

            assertClassPathFor(
                precompiledScriptPlugin,
                includes = setOf(implementationDependency),
                excludes = setOf(classpathDependency)
            )
        }
    }

    @Test
    fun `given a multi-project build, the classpath of a precompiled script plugin is the compile classpath of its enclosing source-set`() {

        val dependencyA =
            withFile("a.jar")

        val dependencyB =
            withFile("b.jar")

        withFolders {

            withFile("settings.gradle.kts", """
                include("project-a")
                include("project-b")
            """)

            "project-a" {
                "src/main/kotlin" {
                    withFile("my-plugin-a.gradle.kts")
                }
                withImplementationDependencyOn(dependencyA)
            }

            "project-b" {
                "src/main/kotlin" {
                    withFile("my-plugin-b.gradle.kts")
                }
                withImplementationDependencyOn(dependencyB)
            }
        }

        assertClassPathFor(
            existing("project-a/src/main/kotlin/my-plugin-a.gradle.kts"),
            includes = setOf(dependencyA),
            excludes = setOf(dependencyB))

        assertClassPathFor(
            existing("project-b/src/main/kotlin/my-plugin-b.gradle.kts"),
            includes = setOf(dependencyB),
            excludes = setOf(dependencyA))
    }

    private
    fun FoldersDsl.withImplementationDependencyOn(file: File) {
        withFile("build.gradle.kts", """
            plugins {
                `kotlin-dsl`
            }

            dependencies {
                implementation(files("${file.name}"))
            }
        """)
    }
}
