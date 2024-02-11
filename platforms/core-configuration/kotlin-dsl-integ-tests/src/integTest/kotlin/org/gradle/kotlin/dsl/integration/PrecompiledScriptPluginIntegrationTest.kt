package org.gradle.kotlin.dsl.integration

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.classEntriesFor
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.TextUtil.normaliseFileSeparators
import org.gradle.util.internal.ToBeImplemented
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import spock.lang.Issue
import java.io.File


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class PrecompiledScriptPluginIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `can apply precompiled script plugin from groovy script`() {

        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/my-plugin.gradle.kts",
            """
            tasks.register("myTask") {}
            """
        )

        withDefaultSettings()
        withFile(
            "build.gradle",
            """
            plugins {
                id 'my-plugin'
            }
            """
        )

        build("myTask")
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/15416")
    fun `can use an empty plugins block in precompiled settings plugin`() {
        withFolders {
            "build-logic" {
                withFile("settings.gradle.kts", defaultSettingsScript)
                withFile(
                    "build.gradle.kts",
                    """
                        plugins {
                            `kotlin-dsl`
                        }
                        $repositoriesBlock
                    """
                )
                withFile(
                    "src/main/kotlin/my-plugin.settings.gradle.kts",
                    """
                        plugins {
                        }
                        println("my-plugin settings plugin applied")
                    """
                )
            }
        }
        withSettings(
            """
                pluginManagement {
                    includeBuild("build-logic")
                }
                plugins {
                    id("my-plugin")
                }
            """
        )

        build("help").run {
            assertThat(output, containsString("my-plugin settings plugin applied"))
        }
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/15416")
    fun `can apply a plugin from the same project in precompiled settings plugin`() {
        withFolders {
            "build-logic" {
                withFile("settings.gradle.kts", defaultSettingsScript)
                withFile(
                    "build.gradle.kts",
                    """
                        plugins {
                            `kotlin-dsl`
                        }
                        $repositoriesBlock
                    """
                )
                withFile(
                    "src/main/kotlin/base-plugin.settings.gradle.kts",
                    """
                        println("base-plugin settings plugin applied")
                    """
                )
                withFile(
                    "src/main/kotlin/my-plugin.settings.gradle.kts",
                    """
                        plugins {
                            id("base-plugin")
                        }
                        println("my-plugin settings plugin applied")
                    """
                )
            }
        }
        withSettings(
            """
                pluginManagement {
                    includeBuild("build-logic")
                }
                plugins {
                    id("my-plugin")
                }
            """
        )

        build("help").run {
            assertThat(output, containsString("base-plugin settings plugin applied"))
            assertThat(output, containsString("my-plugin settings plugin applied"))
        }
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/15416")
    fun `can apply a plugin from a repository in precompiled settings plugin`() {
        withFolders {
            "external-plugin" {
                withFile("settings.gradle.kts", defaultSettingsScript)
                withFile(
                    "build.gradle.kts",
                    """
                        plugins {
                            `kotlin-dsl`
                            id("maven-publish")
                        }
                        $repositoriesBlock
                        publishing {
                            repositories {
                                maven {
                                    url = uri("maven-repo")
                                }
                            }
                        }
                        group = "test"
                        version = "42"
                    """
                )
                withFile(
                    "src/main/kotlin/base-plugin.settings.gradle.kts",
                    """
                        println("base-plugin settings plugin applied")
                    """
                )
            }
            "build-logic" {
                withFile("settings.gradle.kts", defaultSettingsScript)
                withFile(
                    "build.gradle.kts",
                    """
                        plugins {
                            `kotlin-dsl`
                        }
                        repositories {
                            gradlePluginPortal()
                            maven {
                                url = uri("../external-plugin/maven-repo")
                            }
                        }
                        dependencies {
                             implementation("test:external-plugin:42")
                        }
                    """
                )
                withFile(
                    "src/main/kotlin/my-plugin.settings.gradle.kts",
                    """
                        plugins {
                            id("base-plugin")
                        }
                        println("my-plugin settings plugin applied")
                    """
                )
            }
        }
        withSettings(
            """
                pluginManagement {
                    repositories {
                        maven {
                            url = uri("external-plugin/maven-repo")
                        }
                    }
                    includeBuild("build-logic")
                }
                plugins {
                    id("my-plugin")
                }
            """
        )

        build(file("external-plugin"), "publish")

        build("help").run {
            assertThat(output, containsString("base-plugin settings plugin applied"))
            assertThat(output, containsString("my-plugin settings plugin applied"))
        }
    }

    @Test
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
    fun `should compile correctly with Kotlin explicit api mode`() {
        withBuildScript(
            """
            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock

            kotlin {
                explicitApi()
            }
            """
        )
        withPrecompiledKotlinScript(
            "my-plugin.gradle.kts",
            """
            tasks.register("myTask") {}
            """
        )

        compileKotlin()
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/23576")
    @ToBeImplemented
    fun `can compile precompiled scripts with compileOnly dependency`() {

        fun withPluginJar(fileName: String, versionString: String): File =
            withZip(
                fileName,
                classEntriesFor(MyPlugin::class.java, MyTask::class.java) + sequenceOf(
                    "META-INF/gradle-plugins/my-plugin.properties" to "implementation-class=org.gradle.kotlin.dsl.integration.MyPlugin".toByteArray(),
                    "my-plugin-version.txt" to versionString.toByteArray(),
                )
            )

        val pluginJarV1 = withPluginJar("my-plugin-1.0.jar", "1.0")
        val pluginJarV2 = withPluginJar("my-plugin-2.0.jar", "2.0")

        withDefaultSettingsIn("buildSrc")
        withBuildScriptIn("buildSrc", """
            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock

            dependencies {
                compileOnly(files("${normaliseFileSeparators(pluginJarV1.absolutePath)}"))
            }
        """)
        val precompiledScript = withFile("buildSrc/src/main/kotlin/my-precompiled-script.gradle.kts", """
            plugins {
                id("my-plugin")
            }
        """)

        withBuildScript("""
            buildscript {
                dependencies {
                    classpath(files("${normaliseFileSeparators(pluginJarV2.absolutePath)}"))
                }
            }
            plugins {
                id("my-precompiled-script")
            }
        """)

        buildAndFail("action").apply {
            assertHasFailure("Plugin [id: 'my-plugin'] was not found in any of the following sources") {
                assertHasErrorOutput("Precompiled script plugin '${precompiledScript.absolutePath}' line: 1")
            }
        }

        // Once implemented:
        // build("action").apply {
        //     assertOutputContains("Applied plugin 2.0")
        // }
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/17831")
    fun `precompiled script plugins in resources are ignored`() {
        withKotlinDslPlugin()
        withPrecompiledKotlinScript("correct.gradle.kts", "")
        file("src/main/resources/invalid.gradle.kts", "DOES NOT COMPILE")
        compileKotlin()
        val generated = file("build/generated-sources/kotlin-dsl-plugins/kotlin").walkTopDown().filter { it.isFile }.map { it.name }
        assertThat(generated.toList(), equalTo(listOf("CorrectPlugin.kt")))
    }

    @Issue("https://github.com/gradle/gradle/issues/16154")
    @Test
    fun `file annotations and package statement in precompiled script plugin handled correctly`() {
        withKotlinBuildSrc()

        val pluginId = "my-plugin"
        withFile(
            "buildSrc/src/main/kotlin/my-plugin.gradle.kts",
            """
                @file:Suppress("UnstableApiUsage")

                package bar

                println("foo")
            """
        )

        withBuildScript(
            """
                plugins {
                    id("bar.$pluginId")
                }
            """
        )

        build(":help").apply {
            assertTaskExecuted(":help")
            assertOutputContains("foo")
        }
    }
}


abstract class MyPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("action", MyTask::class.java)
    }
}


abstract class MyTask : DefaultTask() {
    @TaskAction
    fun action() {
        this::class.java.classLoader
            .getResource("my-plugin-version.txt")!!
            .readText()
            .let { version ->
                println("Applied plugin $version")
            }
    }
}
