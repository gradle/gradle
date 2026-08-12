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

import org.gradle.integtests.fixtures.versions.KotlinGradlePluginVersions
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import org.gradle.util.internal.VersionNumber
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assume
import org.junit.Test
import spock.lang.Issue


class EmbeddedKotlinPluginIntegTest : AbstractKotlinIntegrationTest() {

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
    fun `warns when the Kotlin compiler version differs from the embedded Kotlin version`() {

        withBuildScript(
            """
            import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
            import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

            plugins {
                `embedded-kotlin`
            }

            $repositoriesBlock

            @OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalBuildToolsApi::class)
            fun useDifferentCompiler() = kotlin {
                compilerVersion.set("2.3.21")
            }
            useDifferentCompiler()

            """
        )

        val result = build("classes")

        assertThat(result.output, containsString("Unsupported Kotlin compiler version"))
    }

    @Test
    @Requires(
        TestExecutionPreconditions.NotEmbeddedExecutor::class,
        reason = "Class path isolation, needed for the forced Kotlin Gradle Plugin version"
    )
    fun `does not warn when the Kotlin compiler version is the embedded Kotlin version`() {

        val olderKgpVersion = KotlinGradlePluginVersions().latestsStable
            .last { VersionNumber.parse(it) < VersionNumber.parse(embeddedKotlinVersion) }

        withBuildScript(
            """
            import org.jetbrains.kotlin.buildtools.api.ExperimentalBuildToolsApi
            import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
            import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

            buildscript {
                configurations.classpath {
                    resolutionStrategy.eachDependency {
                        if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-gradle-plugin")) {
                            useVersion("$olderKgpVersion")
                        }
                    }
                }
            }

            plugins {
                `embedded-kotlin`
            }

            $repositoriesBlock

            @OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalBuildToolsApi::class)
            fun useEmbeddedCompiler() = kotlin {
                compilerVersion.set("$embeddedKotlinVersion")
            }
            useEmbeddedCompiler()

            println("applied Kotlin plugin version: " + project.getKotlinPluginVersion())

            """
        )

        val result = build("classes")

        assertThat(result.output, containsString("applied Kotlin plugin version: $olderKgpVersion"))
        assertThat(result.output, not(containsString("Unsupported Kotlin")))
    }

    @Test
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
                    val configurationsToCheck = listOf("compileOnlyClasspath", "testRuntimeClasspath").associate { Pair(it, configurations[it] as FileCollection) }
                    doLast {
                        val requiredLibs = listOf("kotlin-stdlib-$embeddedKotlinVersion.jar", "kotlin-reflect-$embeddedKotlinVersion.jar")
                        configurationsToCheck.forEach { (name, fileCollection) ->
                            require(fileCollection.files.map { it.name }.containsAll(requiredLibs), {
                                "Embedded Kotlin libraries not found in ${'$'}name"
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
                ${
                dependencyDeclarationsFor(
                    "implementation",
                    listOf("compiler-embeddable", "scripting-compiler-embeddable", "scripting-compiler-impl-embeddable")
                )
            }
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

            val customConfiguration = configurations.create("customConfiguration")
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

            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                // Work around JVM validation issue: https://youtrack.jetbrains.com/issue/KT-66919
                jvmTargetValidationMode = org.jetbrains.kotlin.gradle.dsl.jvm.JvmTargetValidationMode.WARNING
            }

            """
        )

        withFile("src/main/kotlin/source.kt", """var foo = "bar"""")

        val result = build("assemble")

        result.assertTaskScheduled(":compileKotlin")
    }

    /**
     * See EmbeddedKotlinPlugin.workAroundKgpEagerConfigurations()
     * TODO remove once https://youtrack.jetbrains.com/issue/KT-81706/ is fixed
     */
    @Test
    @Issue("https://github.com/gradle/gradle/issues/35309")
    fun `clears swift configurations created by KGP`() {
        // TODO: investigate why the test fails with "Error resolving plugin [id: 'org.gradle.kotlin.embedded-kotlin', version: '6.4.2']"
        Assume.assumeFalse("This test does not work with forceRealize set to true",
            System.getProperty("org.gradle.integtest.force.realize.metadata", "false").toBooleanStrictOrNull() ?: false
        )

        withDefaultSettings()

        withBuildScript(
            """
            plugins {
                `embedded-kotlin`
            }

            $repositoriesBlock
            """
        )

        build("dependencies", "--write-verification-metadata", "sha256")

        val verificationMetadata = existing("gradle/verification-metadata.xml")
        assertThat(verificationMetadata.readText(), not(containsString("swift-export")))
    }

    private
    fun dependencyDeclarationsFor(configuration: String, modules: List<String>, version: String? = null) =
        modules.joinToString("\n") {
            "$configuration(\"org.jetbrains.kotlin:kotlin-$it:${version ?: embeddedKotlinVersion}\")"
        }
}
