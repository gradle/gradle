package org.gradle.kotlin.dsl.integration

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.classEntriesFor
import org.gradle.kotlin.dsl.support.expectedKotlinDslPluginsVersion
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
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
                            ${RepoScriptBlockUtil.gradlePluginRepositoryDefinition(GradleDsl.KOTLIN)}
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
    @Requires(TestExecutionPreconditions.NotEmbeddedExecutor::class)
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
    @Requires(TestExecutionPreconditions.NotEmbeddedExecutor::class)
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

    @Test
    fun `packaged precompiled script with CRLF line separators and a license header can use type-safe accessors`() {
        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/my/conv/my-plugin.gradle.kts",
            "/*\n * License header\n */\n\n@file:Suppress(\"UnstableApiUsage\")\n\npackage my.conv\n\nplugins { java }\n\njava { withSourcesJar() }\n\ntasks.register(\"hello\") { doLast { println(\"main source set: \" + sourceSets.main.get().name) } }\n"
                .replace("\n", "\r\n")
        )
        withBuildScript(
            """
                plugins {
                    id("my.conv.my-plugin")
                }
            """
        )

        build("hello").apply {
            assertOutputContains("main source set: main")
        }
    }

    @Test
    fun `body-only edit of a precompiled script plugin recompiles only that script`() {
        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/plugin-a.gradle.kts",
            """
                plugins { java }
                tasks.register("helloA") { doLast { println("helloA v1 " + sourceSets.main.get().name) } }
            """
        )
        withFile(
            "buildSrc/src/main/kotlin/my/conv/plugin-b.gradle.kts",
            """
                package my.conv
                plugins { java }
                tasks.register("helloB") { doLast { println("helloB v1 " + sourceSets.main.get().name) } }
            """
        )
        withBuildScript(
            """
                plugins {
                    id("plugin-a")
                    id("my.conv.plugin-b")
                }
            """
        )
        build("helloA", "helloB")
        val classesDir = existing("buildSrc/build/classes/kotlin/main")
        fun classFileTimestamps() =
            classesDir.walkTopDown().filter { it.isFile && it.name.endsWith(".class") }.associate { it.relativeTo(classesDir).path to it.lastModified() }
        val before = classFileTimestamps()
        Thread.sleep(1000) // ensure a rewritten class file gets a different timestamp

        val pluginA = existing("buildSrc/src/main/kotlin/plugin-a.gradle.kts")
        pluginA.writeText(pluginA.readText().replace("helloA v1", "helloA v2"))

        build("helloA", "helloB", "--info").apply {
            assertOutputContains("helloA v2 main")
            assertNotOutput("The input changes require a full rebuild for incremental task ':buildSrc:compileKotlin'.")
        }
        val after = classFileTimestamps()
        assertThat(after.keys, equalTo(before.keys))
        val rewritten = after.filter { (path, timestamp) -> before[path] != timestamp }.keys
        assertThat("rewritten class files: $rewritten", rewritten.isNotEmpty() && rewritten.all { it.startsWith("Plugin_a_gradle") }, equalTo(true))
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
                        dependencyResolutionManagement.repositories {
                            ${RepoScriptBlockUtil.gradlePluginRepositoryDefinition(GradleDsl.KOTLIN)}
                        }
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

                            dependencyResolutionManagement.repositories {
                                $testRepositories
                                ${RepoScriptBlockUtil.gradlePluginRepositoryDefinition(GradleDsl.KOTLIN)}
                            }
                        """
                    )

                    withFile(
                        "build.gradle.kts",
                        """
                            plugins {
                                `kotlin-dsl`
                            }

                            dependencies {
                                implementation("org.gradle.kotlin.kotlin-dsl:org.gradle.kotlin.kotlin-dsl.gradle.plugin:$futureKotlinDslPluginVersion")
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
