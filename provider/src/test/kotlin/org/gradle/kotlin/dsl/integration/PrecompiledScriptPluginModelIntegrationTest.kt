package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.FoldersDsl
import org.gradle.kotlin.dsl.fixtures.withFolders

import org.junit.Test

import java.io.File


class PrecompiledScriptPluginModelIntegrationTest : ScriptModelIntegrationTest() {

    @Test
    fun `given a single project build, the classpath of a precompiled script plugin is the compile classpath of its enclosing source-set`() {

        val compileDependency =
            withFile("compile.jar")

        val classpathDependency =
            withFile("classpath.jar")

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
                compile(files("${compileDependency.name}"))
            }
        """)

        val precompiledScriptPlugin =
            withFile("src/main/kotlin/my-plugin.gradle.kts")

        assertClassPathFor(
            precompiledScriptPlugin,
            includes = setOf(compileDependency),
            excludes = setOf(classpathDependency))
    }

    @Test
    fun `given a multi-project build, the classpath of a precompiled script plugin is the compile classpath of its enclosing source-set`() {

        val dependencyA =
            withFile("a.jar")

        val dependencyB =
            withFile("b.jar")

        projectRoot.withFolders {

            withFile("settings.gradle.kts", """
                include("project-a")
                include("project-b")
            """)

            "project-a" {
                "src/main/kotlin" {
                    withFile("my-plugin-a.gradle.kts")
                }
                withCompileDependencyOn(dependencyA)
            }

            "project-b" {
                "src/main/kotlin" {
                    withFile("my-plugin-b.gradle.kts")
                }
                withCompileDependencyOn(dependencyB)
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
    fun FoldersDsl.withCompileDependencyOn(file: File) {
        withFile("build.gradle.kts", """
            plugins {
                `kotlin-dsl`
            }

            dependencies {
                compile(files("${file.name}"))
            }
        """)
    }
}
