/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.script.lang.kotlin.plugins.embedded

import org.gradle.script.lang.kotlin.fixtures.gradleRunnerFor
import org.gradle.script.lang.kotlin.plugins.AbstractPluginTest

import org.gradle.testkit.runner.TaskOutcome

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo

import org.junit.Assert.assertThat
import org.junit.Test

import java.io.File


class EmbeddedKotlinPluginTest : AbstractPluginTest() {

    @Test
    fun `applies the kotlin plugin`() {

        withBuildScript("""

            plugins {
                `embedded-kotlin`
            }

        """)

        val result = buildWithPlugin("assemble")

        assertThat(result.task(":compileKotlin").outcome, equalTo(TaskOutcome.NO_SOURCE))
    }

    @Test
    fun `all embedded kotlin dependencies are resolvable without any added repository`() {

        withBuildScript("""

            plugins {
                `embedded-kotlin`
            }

            dependencies {
                ${dependencyDeclarationsFor(embeddedModules.filter { !it.autoDependency })}
            }

            println(repositories.map { it.name })
            configurations["compileClasspath"].files.map { println(it) }

        """)

        val result = buildWithPlugin("help")

        assertThat(result.output, containsString("Embedded Kotlin Repository"))
        embeddedModules.forEach {
            assertThat(result.output, containsString(it.jarRepoPath.replace('/', File.separatorChar)))
        }
    }

    @Test
    fun `sources and javadoc of all embedded kotlin dependencies are resolvable with an added repository`() {

        withBuildScript("""

            import org.gradle.plugins.ide.internal.IdeDependenciesExtractor

            plugins {
                `embedded-kotlin`
            }

            repositories {
                jcenter()
            }

            dependencies {
                ${dependencyDeclarationsFor(embeddedModules.filter { !it.autoDependency })}
            }

            configurations["compileClasspath"].files.map { println(it) }

            tasks {
                "downloadAuxiliaries" {
                    val depsExtractor = IdeDependenciesExtractor()
                    val deps = depsExtractor.extractRepoFileDependencies(dependencies,
                                                                         listOf(configurations["compileClasspath"]),
                                                                         emptyList(),
                                                                         true, true)
                    deps.forEach { it.sourceFiles.forEach { println(it.name) } }
                    deps.forEach { it.javadocFiles.forEach { println(it.name) } }
                }
            }

        """)

        val result = buildWithPlugin("help")

        embeddedModules.forEach {
            assertThat(result.output, containsString("${it.name}-${it.version}.jar"))
            assertThat(result.output, containsString("${it.name}-${it.version}-sources.jar"))
            assertThat(result.output, containsString("${it.name}-${it.version}-javadoc.jar"))
        }
    }

    @Test
    fun `embedded kotlin modules versions are pinned to the embedded Kotlin version`() {

        withBuildScript("""

            plugins {
                `embedded-kotlin`
            }

            repositories {
                jcenter()
            }

            dependencies {
                ${dependencyDeclarationsFor(embeddedModules, "1.1.1")}
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:0.15")
            }

            configurations["compileClasspath"].files.map { println(it) }

        """)

        val result = buildWithPlugin("dependencies")

        embeddedModules.forEach {
            assertThat(result.output, containsString("${it.group}:${it.name}:1.1.1 -> ${it.version}"))
            assertThat(result.output, containsString("${it.name}-${it.version}.jar"))
        }
    }

    private
    fun dependencyDeclarationsFor(modules: List<EmbeddedModule>, version: String? = null) =
        modules.map {
            "implementation(\"${
            if (version == null) it.notation
            else "${it.group}:${it.name}:$version"
            }\")"
        }.joinToString("\n")
}

