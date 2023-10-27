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

package org.gradle.kotlin.dsl.plugins.embedded

import org.gradle.test.fixtures.file.LeaksFileHandles

import org.gradle.kotlin.dsl.embeddedKotlinVersion
import org.gradle.kotlin.dsl.fixtures.AbstractPluginTest

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

import org.hamcrest.CoreMatchers.containsString

import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class EmbeddedKotlinPluginIntegTest : AbstractPluginTest() {

    @Test
    fun `applies the kotlin plugin`() {

        withBuildScript(
            """

            plugins {
                `embedded-kotlin`
            }

            $repositoriesBlock

            """
        )

        val result = build("assemble")

        result.assertOutputContains(":compileKotlin NO-SOURCE")
    }

    @Test
    @ToBeFixedForConfigurationCache
    fun `adds stdlib and reflect as compile only dependencies`() {

        withBuildScript(
            """

            plugins {
                `embedded-kotlin`
            }

            configurations {
                create("compileOnlyClasspath") { extendsFrom(configurations["compileOnly"]) }
            }

            $repositoriesBlock

            tasks {
                register("assertions") {
                    doLast {
                        val requiredLibs = listOf("kotlin-stdlib-$embeddedKotlinVersion.jar", "kotlin-reflect-$embeddedKotlinVersion.jar")
                        listOf("compileOnlyClasspath", "testRuntimeClasspath").forEach { configuration ->
                            require(configurations[configuration].files.map { it.name }.containsAll(requiredLibs), {
                                "Embedded Kotlin libraries not found in ${'$'}configuration"
                            })
                        }
                    }
                }
            }

            """
        )

        build("assertions")
    }

    @Test
    fun `all embedded kotlin dependencies are resolvable`() {

        withBuildScript(
            """

            plugins {
                `embedded-kotlin`
            }

            $repositoriesBlock

            dependencies {
                ${dependencyDeclarationsFor(
                "implementation",
                listOf("compiler-embeddable", "scripting-compiler-embeddable", "scripting-compiler-impl-embeddable")
            )}
            }

            configurations["compileClasspath"].files.map { println(it) }

            """
        )

        val result = build("dependencies")

        listOf("stdlib", "reflect", "compiler-embeddable", "scripting-compiler-embeddable", "scripting-compiler-impl-embeddable").forEach {
            assertThat(result.output, containsString("kotlin-$it-$embeddedKotlinVersion.jar"))
        }
    }

    @Test
    fun `sources and javadoc of all embedded kotlin dependencies are resolvable`() {

        withBuildScript(
            """

            plugins {
                `embedded-kotlin`
            }

            $repositoriesBlock

            dependencies {
                ${dependencyDeclarationsFor("implementation", listOf("stdlib", "reflect"))}
            }

            configurations["compileClasspath"].files.forEach {
                println(it)
            }

            val components =
                configurations
                    .compileClasspath.get()
                    .incoming
                    .artifactView { lenient(true) }
                    .artifacts
                    .map { it.id.componentIdentifier }

            val resolvedComponents =
                dependencies
                    .createArtifactResolutionQuery()
                    .forComponents(*components.toTypedArray())
                    .withArtifacts(
                        JvmLibrary::class.java,
                        SourcesArtifact::class.java,
                        JavadocArtifact::class.java)
                    .execute()
                    .resolvedComponents

            inline fun <reified T : Artifact> printFileNamesOf() =
                resolvedComponents
                    .flatMap { it.getArtifacts(T::class.java) }
                    .filterIsInstance<ResolvedArtifactResult>()
                    .forEach { println(it.file.name) }

            printFileNamesOf<SourcesArtifact>()
            printFileNamesOf<JavadocArtifact>()
            """
        )

        val result = build("help")

        listOf("stdlib", "reflect").forEach {
            assertThat(result.output, containsString("kotlin-$it-$embeddedKotlinVersion.jar"))
            assertThat(result.output, containsString("kotlin-$it-$embeddedKotlinVersion-sources.jar"))
            assertThat(result.output, containsString("kotlin-$it-$embeddedKotlinVersion-javadoc.jar"))
        }
    }

    @Test
    fun `can add embedded dependencies to custom configuration`() {

        withBuildScript(
            """

            plugins {
                `embedded-kotlin`
            }

            $repositoriesBlock

            val customConfiguration by configurations.creating
            customConfiguration.extendsFrom(configurations["embeddedKotlin"])

            configurations["customConfiguration"].files.map { println(it) }
            """
        )

        val result = build("dependencies", "--configuration", "customConfiguration")

        listOf("stdlib", "reflect").forEach {
            assertThat(result.output, containsString("org.jetbrains.kotlin:kotlin-$it:$embeddedKotlinVersion"))
            assertThat(result.output, containsString("kotlin-$it-$embeddedKotlinVersion.jar"))
        }
    }

    @Test
    @LeaksFileHandles("Kotlin Compiler Daemon working directory")
    fun `can be used with embedded artifact-only repository`() {

        withDefaultSettings()

        withBuildScript(
            """

            plugins {
                `embedded-kotlin`
            }

            $repositoriesBlock

            """
        )

        withFile("src/main/kotlin/source.kt", """var foo = "bar"""")

        val result = build("assemble")

        result.assertTaskExecuted(":compileKotlin")
    }

    private
    fun dependencyDeclarationsFor(configuration: String, modules: List<String>, version: String? = null) =
        modules.joinToString("\n") {
            "$configuration(\"org.jetbrains.kotlin:kotlin-$it:${version ?: embeddedKotlinVersion}\")"
        }
}
