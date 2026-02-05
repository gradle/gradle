package org.gradle.kotlin.dsl.integration

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.classEntriesFor
import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.internal.TextUtil.normaliseFileSeparators
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
            assertOutputContains("my-plugin settings plugin applied")
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
            assertOutputContains("base-plugin settings plugin applied")
            assertOutputContains("my-plugin settings plugin applied")
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
            assertOutputContains("base-plugin settings plugin applied")
            assertOutputContains("my-plugin settings plugin applied")
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
    @Requires(IntegTestPreconditions.NotEmbeddedExecutor::class)
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
        withFile("buildSrc/src/main/kotlin/my-precompiled-script.gradle.kts", """
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

         build("action").apply {
             assertOutputContains("Applied plugin 2.0")
         }
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
            assertTaskScheduled(":help")
            assertOutputContains("foo")
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/22428")
    @Test
    fun `can apply kotlin-dsl plugin in precompiled script plugin applied in another precompiled script plugin`() {
        withBuildScript(
            """
                plugins {
                    id("apply-java")
                }
            """
        )

        withSettings(
            """
                pluginManagement.includeBuild("build-logic")
            """
        )

        withFolders {
            "build-logic" {
                withFile(
                    "settings.gradle.kts",
                    """
                        rootProject.name = "my-repro-project-build-logic"
    
                        pluginManagement.includeBuild("meta")
                        dependencyResolutionManagement.repositories.gradlePluginPortal()
                    """
                )

                withFile(
                    "build.gradle.kts",
                    """
                        plugins {
                            id("my.kotlin-dsl")
                        }
                    """
                )

                withFile(
                    "src/main/kotlin/apply-java.gradle.kts",
                    """
                        plugins {
                            `java-library`
                        }
                    """
                )

                "meta" {
                    withFile(
                        "settings.gradle.kts",
                        """
                            rootProject.name = "my-repro-project-build-logic-meta"
    
                            dependencyResolutionManagement.repositories.gradlePluginPortal()
                        """
                    )

                    withFile(
                        "build.gradle.kts",
                        """
                            import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion
    
                            plugins {
                                `kotlin-dsl`
                            }
    
                            dependencies {
                                implementation("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:${'$'}expectedKotlinDslPluginsVersion")
                            }
                        """
                    )

                    withFile(
                        "src/main/kotlin/my.kotlin-dsl.gradle.kts",
                        """
                            plugins {
                                `kotlin-dsl`
                            }
                        """
                    )
                }
            }
        }

        buildAndFail(":build").apply {
            assertThatDescription(
                containsString(
                    "Invalid plugin request [id: 'org.gradle.kotlin.kotlin-dsl', version: '${expectedKotlinDslPluginsVersion}']. " +
                            "Plugin requests from precompiled scripts must not include a version number. " +
                            "If you have been using the `kotlin-dsl` helper function, then simply replace it by 'id(\"org.gradle.kotlin.kotlin-dsl\")'. " +
                            "Make sure the module containing the requested plugin 'org.gradle.kotlin.kotlin-dsl' is an implementation dependency of project ':build-logic:meta'."
                )
            )
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
