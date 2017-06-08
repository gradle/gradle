package org.gradle.script.lang.kotlin.plugins.embedded

import org.gradle.script.lang.kotlin.fixtures.AbstractIntegrationTest
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test


class EmbeddedKotlinPluginTest : AbstractIntegrationTest() {

    @Test
    fun `applies the kotlin plugin`() {

        withBuildScript("""

            plugins {
                id("embedded-kotlin")
            }

        """)

        val result = runWithArguments("assemble")

        assertThat(result.task(":compileKotlin").outcome, equalTo(TaskOutcome.NO_SOURCE))
    }

    @Test
    fun `all embedded kotlin dependencies are resolvable without any added repository`() {

        withBuildScript("""

            plugins {
                id("embedded-kotlin")
            }

            dependencies {
                ${dependencyDeclarationsFor(embeddedModules.filter { !it.autoDependency })}
            }

            println(repositories.map { it.name })
            configurations["compileClasspath"].files.map { println(it) }

        """)

        val result = runWithArguments("help")

        assertThat(result.output, containsString("Embedded Kotlin Repository"))
        embeddedModules.forEach {
            assertThat(result.output, containsString(it.jarRepoPath))
        }
    }

    @Test
    fun `sources and javadoc of all embedded kotlin dependencies are resolvable with an added repository`() {

        withBuildScript("""

            import org.gradle.plugins.ide.internal.IdeDependenciesExtractor

            plugins {
                id("embedded-kotlin")
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

        val result = runWithArguments("help")

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
                id("embedded-kotlin")
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

        val result = runWithArguments("dependencies")

        embeddedModules.forEach {
            assertThat(result.output, containsString("${it.group}:${it.name}:1.1.1 -> ${it.version}"))
            assertThat(result.output, containsString("${it.name}-${it.version}.jar"))
        }
    }


    private
    fun runWithArguments(vararg arguments: String) =
        GradleRunner.create()
            .withProjectDir(projectRoot)
            .withPluginClasspath()
            .withArguments(*arguments)
            .build()


    private
    fun dependencyDeclarationsFor(modules: List<EmbeddedModule>, version: String? = null) =
        modules.map {
            "implementation(\"${
            if (version == null) it.notation
            else "${it.group}:${it.name}:$version"
            }\")"
        }.joinToString("\n")
}

