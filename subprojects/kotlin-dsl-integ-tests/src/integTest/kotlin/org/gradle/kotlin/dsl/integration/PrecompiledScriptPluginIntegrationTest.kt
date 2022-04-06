package org.gradle.kotlin.dsl.integration

import org.codehaus.groovy.runtime.StringGroovyMethods
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.kotlin.dsl.fixtures.normalisedPath
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.util.GradleVersion
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class PrecompiledScriptPluginIntegrationTest : AbstractPluginIntegrationTest() {

    @Test
    fun `generated code follows kotlin-dsl coding conventions`() {

        assumeNonEmbeddedGradleExecuter() // ktlint plugin issue in embedded mode

        withBuildScript(
            """
            plugins {
                `kotlin-dsl`
                id("org.gradle.kotlin-dsl.ktlint-convention") version "0.7.0"
            }

            $repositoriesBlock
            """
        )

        withPrecompiledKotlinScript(
            "plugin-without-package.gradle.kts",
            """
            plugins {
                org.gradle.base
            }

            """.trimIndent()
        )
        withPrecompiledKotlinScript(
            "test/gradle/plugins/plugin-with-package.gradle.kts",
            """
            package test.gradle.plugins

            plugins {
                org.gradle.base
            }

            """.trimIndent()
        )

        build("generateScriptPluginAdapters")

        build("ktlintCheck", "-x", "ktlintKotlinScriptCheck")
    }

    @Test
    fun `precompiled script plugins tasks are cached and relocatable`() {

        val firstLocation = "first-location"
        val secondLocation = "second-location"
        val cacheDir = newDir("cache-dir")

        withDefaultSettingsIn(firstLocation).appendText(
            """
            rootProject.name = "test"
            buildCache {
                local {
                    directory = file("${cacheDir.normalisedPath}")
                }
            }
            """
        )
        withBuildScriptIn(
            firstLocation,
            """
            plugins { `kotlin-dsl` }
            ${RepoScriptBlockUtil.mavenCentralRepository(GradleDsl.KOTLIN)}
            """
        )

        withFile("$firstLocation/src/main/kotlin/plugin-without-package.gradle.kts")
        withFile(
            "$firstLocation/src/main/kotlin/plugins/plugin-with-package.gradle.kts",
            """
            package plugins
            """
        )


        val firstDir = existing(firstLocation)
        val secondDir = newDir(secondLocation)
        firstDir.copyRecursively(secondDir)

        val cachedTasks = listOf(
            ":extractPrecompiledScriptPluginPlugins",
            ":generateExternalPluginSpecBuilders",
            ":compilePluginsBlocks",
            ":generateScriptPluginAdapters"
        )
        val downstreamKotlinCompileTask = ":compileKotlin"

        build(firstDir, "classes", "--build-cache").apply {
            cachedTasks.forEach { assertTaskExecuted(it) }
            assertTaskExecuted(downstreamKotlinCompileTask)
        }

        build(firstDir, "classes", "--build-cache").apply {
            cachedTasks.forEach { assertOutputContains("$it UP-TO-DATE") }
            assertOutputContains("$downstreamKotlinCompileTask UP-TO-DATE")
        }

        build(secondDir, "classes", "--build-cache").apply {
            cachedTasks.forEach { assertOutputContains("$it FROM-CACHE") }
            assertOutputContains("$downstreamKotlinCompileTask FROM-CACHE")
        }
    }

    @Test
    fun `precompiled script plugins adapters generation clean stale outputs`() {

        withBuildScript(
            """
            plugins { `kotlin-dsl` }
            """
        )

        val fooScript = withFile("src/main/kotlin/foo.gradle.kts", "")

        build("generateScriptPluginAdapters")
        assertTrue(existing("build/generated-sources/kotlin-dsl-plugins/kotlin/FooPlugin.kt").isFile)

        fooScript.renameTo(fooScript.parentFile.resolve("bar.gradle.kts"))

        build("generateScriptPluginAdapters")
        assertFalse(existing("build/generated-sources/kotlin-dsl-plugins/kotlin/FooPlugin.kt").exists())
        assertTrue(existing("build/generated-sources/kotlin-dsl-plugins/kotlin/BarPlugin.kt").isFile)
    }

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
    fun `accessors are available after script body change`() {

        withKotlinBuildSrc()
        val myPluginScript = withFile(
            "buildSrc/src/main/kotlin/my-plugin.gradle.kts",
            """
            plugins { base }

            base {
                archivesName.set("my")
            }

            println("base")
            """
        )

        withDefaultSettings()
        withBuildScript(
            """
            plugins {
                `my-plugin`
            }
            """
        )

        build("help").apply {
            assertThat(output, containsString("base"))
        }

        myPluginScript.appendText(
            """

            println("modified")
            """.trimIndent()
        )

        build("help").apply {
            assertThat(output, containsString("base"))
            assertThat(output, containsString("modified"))
        }
    }

    @Test
    fun `accessors are available after re-running tasks`() {

        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/my-plugin.gradle.kts",
            """
            plugins { base }

            base {
                archivesName.set("my")
            }
            """
        )

        withDefaultSettings()
        withBuildScript(
            """
            plugins {
                `my-plugin`
            }
            """
        )

        build("clean")

        build("clean", "--rerun-tasks")
    }

    @Test
    fun `accessors are available after registering plugin`() {
        withSettings(
            """
            $defaultSettingsScript

            include("consumer", "producer")
            """
        )

        withBuildScript(
            """
            plugins {
                `java-library`
            }

            allprojects {
                $repositoriesBlock
            }

            dependencies {
                api(project(":consumer"))
            }
            """
        )

        withFolders {

            "consumer" {
                withFile(
                    "build.gradle.kts",
                    """
                    plugins {
                        id("org.gradle.kotlin.kotlin-dsl")
                    }

                    // Forces dependencies to be visible as jars
                    // so we get plugin spec accessors
                    ${forceJarsOnCompileClasspath()}

                    dependencies {
                        implementation(project(":producer"))
                    }
                    """
                )

                withFile(
                    "src/main/kotlin/consumer-plugin.gradle.kts",
                    """
                    plugins { `producer-plugin` }
                    """
                )
            }

            "producer" {
                withFile(
                    "build.gradle",
                    """
                    plugins {
                        id("java-library")
                        id("java-gradle-plugin")
                    }
                    """
                )
                withFile(
                    "src/main/java/producer/ProducerPlugin.java",
                    """
                    package producer;
                    public class ProducerPlugin {
                        // Using internal class to verify https://github.com/gradle/gradle/issues/17619
                        public static class Implementation implements ${nameOf<Plugin<*>>()}<${nameOf<Project>()}> {
                            @Override public void apply(${nameOf<Project>()} target) {}
                        }
                    }
                    """
                )
            }
        }

        buildAndFail("assemble").run {
            // Accessor is not available on the first run as the plugin hasn't been registered.
            assertTaskExecuted(
                ":consumer:generateExternalPluginSpecBuilders"
            )
        }

        existing("producer/build.gradle").run {
            appendText(
                """
                gradlePlugin {
                    plugins {
                        producer {
                            id = 'producer-plugin'
                            implementationClass = 'producer.ProducerPlugin${'$'}Implementation'
                        }
                    }
                }
                """
            )
        }

        // Accessor becomes available after registering the plugin.
        build("assemble").run {
            assertTaskExecuted(
                ":consumer:generateExternalPluginSpecBuilders"
            )
        }
    }

    private
    inline fun <reified T> nameOf() = T::class.qualifiedName

    @Test
    fun `accessors are available after renaming precompiled script plugin from project dependency`() {

        withSettings(
            """
            $defaultSettingsScript

            include("consumer", "producer")
            """
        )

        withBuildScript(
            """
            plugins {
                `java-library`
                `kotlin-dsl` apply false
            }

            allprojects {
                $repositoriesBlock
            }

            dependencies {
                api(project(":consumer"))
            }
            """
        )

        withFolders {

            "consumer" {
                withFile(
                    "build.gradle.kts",
                    """
                    plugins {
                        id("org.gradle.kotlin.kotlin-dsl")
                    }

                    // Forces dependencies to be visible as jars
                    // to reproduce the failure that happens in forkingIntegTest.
                    // Incidentally, this also allows us to write `stable-producer-plugin`
                    // in the plugins block below instead of id("stable-producer-plugin").
                    ${forceJarsOnCompileClasspath()}

                    dependencies {
                        implementation(project(":producer"))
                    }
                    """
                )

                withFile(
                    "src/main/kotlin/consumer-plugin.gradle.kts",
                    """
                    plugins { `stable-producer-plugin` }
                    """
                )
            }

            "producer" {
                withFile(
                    "build.gradle.kts",
                    """
                    plugins { id("org.gradle.kotlin.kotlin-dsl") }
                    """
                )
                withFile("src/main/kotlin/changing-producer-plugin.gradle.kts")
                withFile(
                    "src/main/kotlin/stable-producer-plugin.gradle.kts",
                    """
                    println("*42*")
                    """
                )
            }
        }

        build("assemble").run {
            assertTaskExecuted(
                ":consumer:generateExternalPluginSpecBuilders"
            )
        }

        existing("producer/src/main/kotlin/changing-producer-plugin.gradle.kts").run {
            renameTo(resolveSibling("changed-producer-plugin.gradle.kts"))
        }

        build("assemble").run {
            assertTaskExecuted(
                ":consumer:generateExternalPluginSpecBuilders"
            )
        }
    }

    private
    fun forceJarsOnCompileClasspath() = """
        configurations {
            compileClasspath {
                attributes {
                    attribute(
                        LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                        objects.named(LibraryElements.JAR)
                    )
                }
            }
        }
    """

    @Test
    fun `applied precompiled script plugin is reloaded upon change`() {
        // given:
        withFolders {
            "build-logic" {
                withFile(
                    "settings.gradle.kts",
                    """
                        $defaultSettingsScript
                        include("producer", "consumer")
                    """
                )
                withFile(
                    "build.gradle.kts",
                    """
                        plugins {
                            `kotlin-dsl` apply false
                        }

                        subprojects {
                            apply(plugin = "org.gradle.kotlin.kotlin-dsl")
                            $repositoriesBlock
                        }

                        project(":consumer") {
                            dependencies {
                                "implementation"(project(":producer"))
                            }
                        }
                    """
                )

                withFile(
                    "producer/src/main/kotlin/producer-plugin.gradle.kts",
                    """
                        println("*version 1*")
                    """
                )
                withFile(
                    "consumer/src/main/kotlin/consumer-plugin.gradle.kts",
                    """
                        plugins { id("producer-plugin") }
                    """
                )
            }
        }
        withSettings(
            """
                includeBuild("build-logic")
            """
        )
        withBuildScript(
            """
                plugins { id("consumer-plugin") }
            """
        )

        // when:
        build("help").run {
            // then:
            assertThat(
                output.count("*version 1*"),
                equalTo(2)
            )
        }

        // when:
        file("build-logic/producer/src/main/kotlin/producer-plugin.gradle.kts").text = """
            println("*version 2*")
        """
        build("help").run {
            // then:
            assertThat(
                output.count("*version 2*"),
                equalTo(2)
            )
        }
    }

    private
    fun CharSequence.count(text: CharSequence): Int =
        StringGroovyMethods.count(this, text)

    // https://github.com/gradle/gradle/issues/15416
    @Test
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

    // https://github.com/gradle/gradle/issues/15416
    @Test
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

    // https://github.com/gradle/gradle/issues/15416
    @Test
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
    fun `should not allow precompiled plugin to conflict with core plugin`() {
        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/java.gradle.kts",
            """
            tasks.register("myTask") {}
            """
        )
        withDefaultSettings()
        withFile(
            "build.gradle",
            """
            plugins {
                java
            }
            """
        )

        val error = buildAndFail("help")

        error.assertHasCause(
            "The precompiled plugin (${"src/main/kotlin/java.gradle.kts".replace("/", File.separator)}) conflicts with the core plugin 'java'. Rename your plugin.\n\n"
                + "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/custom_plugins.html#sec:precompiled_plugins for more details."
        )
    }

    @Test
    fun `should not allow precompiled plugin to have org-dot-gradle prefix`() {
        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/org.gradle.my-plugin.gradle.kts",
            """
            tasks.register("myTask") {}
            """
        )
        withDefaultSettings()

        val error = buildAndFail("help")

        error.assertHasCause(
            "The precompiled plugin (${"src/main/kotlin/org.gradle.my-plugin.gradle.kts".replace("/", File.separator)}) cannot start with 'org.gradle' or be in the 'org.gradle' package.\n\n"
                + "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/custom_plugins.html#sec:precompiled_plugins for more details."
        )
    }

    @Test
    fun `should not allow precompiled plugin to be in org-dot-gradle package`() {
        withKotlinBuildSrc()
        withFile(
            "buildSrc/src/main/kotlin/org/gradle/my-plugin.gradle.kts",
            """
            package org.gradle

            tasks.register("myTask") {}
            """
        )
        withDefaultSettings()

        val error = buildAndFail("help")

        error.assertHasCause(
            "The precompiled plugin (${"src/main/kotlin/org/gradle/my-plugin.gradle.kts".replace("/", File.separator)}) cannot start with 'org.gradle' or be in the 'org.gradle' package.\n\n"
                + "See https://docs.gradle.org/${GradleVersion.current().version}/userguide/custom_plugins.html#sec:precompiled_plugins for more details."
        )
    }
}
