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

import org.gradle.kotlin.dsl.embeddedKotlinVersion
import org.gradle.kotlin.dsl.fixtures.AbstractPluginTest

import org.gradle.api.logging.Logger

import org.gradle.testkit.runner.TaskOutcome

import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo

import org.junit.Assert.assertThat
import org.junit.Test


class EmbeddedKotlinPluginTest : AbstractPluginTest() {

    @Test
    fun `applies the kotlin plugin`() {

        withBuildScript("""

            plugins {
                `embedded-kotlin`
            }

        """)

        val result = buildWithPlugin("assemble")

        assertThat(result.outcomeOf(":compileKotlin"), equalTo(TaskOutcome.NO_SOURCE))
    }

    @Test
    fun `adds stdlib and reflect as compile only dependencies`() {
        withBuildScript("""

            plugins {
                `embedded-kotlin`
            }

            tasks {
                register("assertions") {
                    doLast {
                        val requiredLibs = listOf("kotlin-stdlib-jdk8-$embeddedKotlinVersion.jar", "kotlin-reflect-$embeddedKotlinVersion.jar")
                        listOf("compileOnly", "testRuntimeClasspath").forEach { configuration ->
                            require(configurations[configuration].files.map { it.name }.containsAll(requiredLibs), {
                                "Embedded Kotlin libraries not found in ${'$'}configuration"
                            })
                        }
                    }
                }
            }

        """)

        buildWithPlugin("assertions")
    }

    @Test
    fun `all embedded kotlin dependencies are resolvable without any added repository`() {

        withBuildScript("""

            plugins {
                `embedded-kotlin`
            }

            dependencies {
                ${dependencyDeclarationsFor("compile", listOf("compiler-embeddable"))}
            }

            println(repositories.map { it.name })
            configurations["compileClasspath"].files.map { println(it) }

        """)

        val result = buildWithPlugin("dependencies")

        assertThat(result.output, containsString("Embedded Kotlin Repository"))
        listOf("stdlib", "reflect", "compiler-embeddable").forEach {
            assertThat(result.output, containsString("kotlin-$it-$embeddedKotlinVersion.jar"))
        }
    }

    @Test
    fun `sources and javadoc of all embedded kotlin dependencies are resolvable with an added repository`() {

        withBuildScript("""

            plugins {
                `embedded-kotlin`
            }

            $repositoriesBlock

            dependencies {
                ${dependencyDeclarationsFor("compile", listOf("stdlib", "reflect"))}
            }

            configurations["compileClasspath"].files.forEach {
                println(it)
            }

            val components =
                configurations
                    .compile
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
        """)

        val result = buildWithPlugin("help")

        listOf("stdlib", "reflect").forEach {
            assertThat(result.output, containsString("kotlin-$it-$embeddedKotlinVersion.jar"))
            assertThat(result.output, containsString("kotlin-$it-$embeddedKotlinVersion-sources.jar"))
            assertThat(result.output, containsString("kotlin-$it-$embeddedKotlinVersion-javadoc.jar"))
        }
    }

    @Test
    fun `can add embedded dependencies to custom configuration`() {

        withBuildScript("""

            plugins {
                `embedded-kotlin`
            }

            val customConfiguration by configurations.creating
            customConfiguration.extendsFrom(configurations["embeddedKotlin"])

            configurations["customConfiguration"].files.map { println(it) }
        """)

        val result = buildWithPlugin("dependencies", "--configuration", "customConfiguration")

        listOf("stdlib", "reflect").forEach {
            assertThat(result.output, containsString("org.jetbrains.kotlin:kotlin-$it:$embeddedKotlinVersion"))
            assertThat(result.output, containsString("kotlin-$it-$embeddedKotlinVersion.jar"))
        }
    }

    @Test
    fun `can be used with GRADLE_METADATA feature preview enabled`() {

        withSettings("""
            $defaultSettingsScript
            enableFeaturePreview("GRADLE_METADATA")
        """)

        withBuildScript("""

            plugins {
                `embedded-kotlin`
            }

            $repositoriesBlock

        """)

        withFile("src/main/kotlin/source.kt", """var foo = "bar"""")

        val result = buildWithPlugin("assemble")

        assertThat(result.outcomeOf(":compileKotlin"), equalTo(TaskOutcome.SUCCESS))
    }

    @Test
    fun `emit a warning if the kotlin plugin version is not the same as embedded`() {

        val logger = mock<Logger>()
        val template = """
            WARNING: Unsupported Kotlin plugin version.
            The `embedded-kotlin` and `kotlin-dsl` plugins rely on features of Kotlin `{}` that might work differently than in the requested version `{}`.
        """.trimIndent()


        logger.warnOnDifferentKotlinVersion(embeddedKotlinVersion)

        inOrder(logger) {
            verifyNoMoreInteractions()
        }


        logger.warnOnDifferentKotlinVersion("1.3")

        inOrder(logger) {
            verify(logger).warn(template, embeddedKotlinVersion, "1.3")
            verifyNoMoreInteractions()
        }


        logger.warnOnDifferentKotlinVersion(null)

        inOrder(logger) {
            verify(logger).warn(template, embeddedKotlinVersion, null)
            verifyNoMoreInteractions()
        }
    }

    private
    fun dependencyDeclarationsFor(configuration: String, modules: List<String>, version: String? = null) =
        modules.joinToString("\n") {
            "$configuration(\"org.jetbrains.kotlin:kotlin-$it:${version ?: embeddedKotlinVersion}\")"
        }
}
