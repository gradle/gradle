/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.FoldersDsl

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.startsWith

import org.junit.Ignore
import org.junit.Test

import java.io.File


class PrecompiledScriptPluginModelIntegrationTest : AbstractPluginIntegrationTest() {

    @Test
    fun `given a single project build, the classpath of a precompiled script plugin is the compile classpath of its enclosing source-set`() {

        val implementationDependency =
            withDeepThoughtJar("implementation.jar")

        val classpathDependency =
            withDeepThoughtJar("classpath.jar")

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

    @Test
    fun `given a multi-project build, the classpath of a precompiled script plugin is the compile classpath of its enclosing source-set`() {

        val dependencyA =
            withFile("a.jar")

        val dependencyB =
            withFile("b.jar")

        withDefaultSettings().appendText("""
            include("project-a")
            include("project-b")
        """)

        withFolders {

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
            excludes = setOf(dependencyB)
        )

        assertClassPathFor(
            existing("project-b/src/main/kotlin/my-plugin-b.gradle.kts"),
            includes = setOf(dependencyB),
            excludes = setOf(dependencyA)
        )
    }

    @Ignore("wip")
    @Test
    fun `implicit imports include type-safe accessors packages`() {

        withDefaultSettings()
        withKotlinDslPlugin()

        val pluginFile = withPrecompiledKotlinScript("plugin.gradle.kts", """
            plugins { java }
        """)

        assertThat(
            kotlinBuildScriptModelFor(pluginFile).implicitImports,
            hasItem(startsWith("gradle.kotlin.dsl.accessors._"))
        )
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
