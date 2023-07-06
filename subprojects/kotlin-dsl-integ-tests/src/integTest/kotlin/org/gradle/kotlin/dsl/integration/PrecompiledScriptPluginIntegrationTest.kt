package org.gradle.kotlin.dsl.integration

import org.codehaus.groovy.runtime.StringGroovyMethods
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.tasks.TaskAction
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.kotlin.dsl.fixtures.classEntriesFor
import org.gradle.kotlin.dsl.fixtures.normalisedPath
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.server.http.BlockingHttpServer
import org.gradle.util.internal.TextUtil.normaliseFileSeparators
import org.gradle.util.internal.ToBeImplemented
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import spock.lang.Issue
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
                id("org.gradle.kotlin-dsl.ktlint-convention") version "0.8.0"
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

        // From ktlint
        executer.beforeExecute {
            it.expectDocumentedDeprecationWarning("The Project.getConvention() method has been deprecated. This is scheduled to be removed in Gradle 9.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecated_access_to_conventions")
        }

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

        // TODO: the Kotlin compile tasks check for cacheability using Task.getProject
        executer.beforeExecute {
            it.withBuildJvmOpts("-Dorg.gradle.configuration-cache.internal.task-execution-access-pre-stable=true")
        }

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
                equalTo(1)
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
                equalTo(1)
            )
        }
    }

    private
    fun CharSequence.count(text: CharSequence): Int =
        StringGroovyMethods.count(this, text)

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

        buildAndFail("help")
            // TODO remove duplicated failure once https://github.com/gradle/gradle/issues/25636 is fixed
            .assertHasFailures(if (GradleContextualExecuter.isConfigCache()) 1 else 2)
            .assertHasCause("The precompiled plugin (${"src/main/kotlin/java.gradle.kts".replace("/", File.separator)}) conflicts with the core plugin 'java'. Rename your plugin.")
            .assertHasResolution(getPrecompiledPluginsLink())
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

        buildAndFail("help")
            // TODO remove duplicated failure once https://github.com/gradle/gradle/issues/25636 is fixed
            .assertHasFailures(if (GradleContextualExecuter.isConfigCache()) 1 else 2)
            .assertHasCause("The precompiled plugin (${"src/main/kotlin/org.gradle.my-plugin.gradle.kts".replace("/", File.separator)}) cannot start with 'org.gradle' or be in the 'org.gradle' package.")
            .assertHasResolution(getPrecompiledPluginsLink())
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

        buildAndFail("help")
            // TODO remove duplicated failure once https://github.com/gradle/gradle/issues/25636 is fixed
            .assertHasFailures(if (GradleContextualExecuter.isConfigCache()) 1 else 2)
            .assertHasCause("The precompiled plugin (${"src/main/kotlin/org/gradle/my-plugin.gradle.kts".replace("/", File.separator)}) cannot start with 'org.gradle' or be in the 'org.gradle' package.")
            .assertHasResolution(getPrecompiledPluginsLink())
    }

    private
    fun getPrecompiledPluginsLink(): String = DocumentationRegistry().getDocumentationRecommendationFor("information", "custom_plugins", "sec:precompiled_plugins")

    @Test
    fun `should compile correctly with Kotlin explicit api mode`() {
        assumeNonEmbeddedGradleExecuter()
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
    @Issue("https://github.com/gradle/gradle/issues/23564")
    fun `respects offline start parameter on synthetic builds for accessors generation`() {

        file("settings.gradle.kts").appendText("""include("producer", "consumer")""")

        withKotlinDslPluginIn("producer")
        withFile("producer/src/main/kotlin/offline.gradle.kts", """
            if (!gradle.startParameter.isOffline) throw IllegalStateException("Build is not offline!")
        """)

        withKotlinDslPluginIn("consumer").appendText("""
           dependencies { implementation(project(":producer")) }
        """)
        withFile("consumer/src/main/kotlin/my-plugin.gradle.kts", """
            plugins { id("offline") }
        """)

        buildAndFail(":consumer:generatePrecompiledScriptPluginAccessors").apply {
            assertHasFailure("An exception occurred applying plugin request [id: 'offline']") {
                assertHasCause("Build is not offline!")
            }
        }

        build(":consumer:generatePrecompiledScriptPluginAccessors", "--offline")
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

    @Test
    @Issue("https://github.com/gradle/gradle/issues/17831")
    fun `fails with a reasonable error message if init precompiled script has no plugin id`() {
        withKotlinDslPlugin()
        val init = withPrecompiledKotlinScript("init.gradle.kts", "")
        buildAndFail(":compileKotlin").apply {
            // TODO remove duplicated failure once https://github.com/gradle/gradle/issues/25636 is fixed
            assertHasFailures(if (GradleContextualExecuter.isConfigCache()) 1 else 2)
            assertHasCause("Precompiled script '${normaliseFileSeparators(init.absolutePath)}' file name is invalid, please rename it to '<plugin-id>.init.gradle.kts'.")
        }
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/17831")
    fun `fails with a reasonable error message if settings precompiled script has no plugin id`() {
        withKotlinDslPlugin()
        val settings = withPrecompiledKotlinScript("settings.gradle.kts", "")
        buildAndFail(":compileKotlin").apply {
            // TODO remove duplicated failure once https://github.com/gradle/gradle/issues/25636 is fixed
            assertHasFailures(if (GradleContextualExecuter.isConfigCache()) 1 else 2)
            assertHasCause("Precompiled script '${normaliseFileSeparators(settings.absolutePath)}' file name is invalid, please rename it to '<plugin-id>.settings.gradle.kts'.")
        }
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/12955")
    fun `captures output of schema collection and displays it on errors`() {

        fun outputFrom(origin: String, logger: Boolean = true) = buildString {
            appendLine("""println("STDOUT from $origin")""")
            appendLine("""System.err.println("STDERR from $origin")""")
            if (logger) {
                appendLine("""logger.lifecycle("LIFECYCLE log from $origin")""")
                appendLine("""logger.warn("WARN log from $origin")""")
                appendLine("""logger.error("ERROR log from $origin")""")
            }
        }

        withDefaultSettingsIn("external-plugins")
        withKotlinDslPluginIn("external-plugins").appendText("""group = "test"""")
        withFile("external-plugins/src/main/kotlin/applied-output.gradle.kts", outputFrom("applied-output plugin"))
        withFile("external-plugins/src/main/kotlin/applied-output-fails.gradle.kts", """
            ${outputFrom("applied-output-fails plugin")}
            TODO("applied-output-fails plugin application failure")
        """)

        withDefaultSettings().appendText("""includeBuild("external-plugins")""")
        withKotlinDslPlugin().appendText("""dependencies { implementation("test:external-plugins") }""")
        withPrecompiledKotlinScript("some.gradle.kts", """
            plugins {
                ${outputFrom("plugins block", logger = false)}
                id("applied-output")
            }
        """)
        build(":compileKotlin").apply {
            assertNotOutput("STDOUT")
            assertNotOutput("STDERR")
            // TODO logging is not captured yet
            assertOutputContains("LIFECYCLE")
            assertOutputContains("WARN")
            assertHasErrorOutput("ERROR")
        }

        withPrecompiledKotlinScript("some.gradle.kts", """
            plugins {
                ${outputFrom("plugins block", logger = false)}
                id("applied-output")
                TODO("some plugins block failure")
            }
        """)
        buildAndFail(":compileKotlin").apply {
            assertHasFailure("Execution failed for task ':generatePrecompiledScriptPluginAccessors'.") {
                assertHasCause("Failed to collect plugin requests of 'src/main/kotlin/some.gradle.kts'")
                assertHasCause("An operation is not implemented: some plugins block failure")
            }
            assertHasErrorOutput("STDOUT from plugins block")
            assertHasErrorOutput("STDERR from plugins block")
            assertNotOutput("STDOUT from applied plugin")
            assertNotOutput("STDERR from applied plugin")
        }

        withPrecompiledKotlinScript("some.gradle.kts", """
            plugins {
                ${outputFrom("plugins block", logger = false)}
                id("applied-output-fails")
            }
        """)
        buildAndFail(":compileKotlin").apply {
            assertHasFailure("Execution failed for task ':generatePrecompiledScriptPluginAccessors'.") {
                assertHasCause("Failed to generate type-safe Gradle model accessors for the following precompiled script plugins")
                assertHasCause("An operation is not implemented: applied-output-fails plugin application failure")
            }
            assertHasErrorOutput("src/main/kotlin/some.gradle.kts")
            assertHasErrorOutput("STDOUT from applied-output-fails plugin")
            assertHasErrorOutput("STDERR from applied-output-fails plugin")
            assertNotOutput("STDOUT from plugins block")
            assertNotOutput("STDERR from plugins block")
            // TODO logging is not captured yet
            assertOutputContains("LIFECYCLE")
            assertOutputContains("WARN")
            assertHasErrorOutput("ERROR")
        }
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/12955")
    fun `captures output of schema collection but not of concurrent tasks`() {

        val repeatOutput = 50
        val server = BlockingHttpServer()

        try {
            server.start()

            withDefaultSettingsIn("external-plugins")
            withKotlinDslPluginIn("external-plugins").appendText("""group = "test"""")
            withFile("external-plugins/src/main/kotlin/applied-output.gradle.kts", """
                println("STDOUT from applied-output plugin")
                System.err.println("STDERR from applied-output plugin")
                logger.warn("WARN from applied-output plugin")
            """)
            withDefaultSettings().appendText("""includeBuild("external-plugins")""")
            withKotlinDslPlugin().prependText("import java.net.URL").appendText("""
                dependencies { implementation("test:external-plugins") }

                abstract class ConcurrentWork : WorkAction<WorkParameters.None> {
                    private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger("SOME")
                    override fun execute() {
                        URL("${server.uri("blockStart")}").readText()
                        repeat($repeatOutput) {
                            Thread.sleep(25)
                            println("STDOUT from concurrent task ${'$'}it")
                            System.err.println("STDERR from concurrent task ${'$'}it")
                            logger.warn("WARN from concurrent task ${'$'}it")
                        }
                        URL("${server.uri("blockStop")}").readText()
                    }
                }

                abstract class ConcurrentTask : DefaultTask() {
                    @get:Inject abstract val workers: WorkerExecutor
                    @TaskAction fun action() {
                        workers.noIsolation().submit(ConcurrentWork::class) {}
                    }
                }

                tasks {
                    val concurrentTask by registering(ConcurrentTask::class)
                    val generatePrecompiledScriptPluginAccessors by existing {
                        shouldRunAfter(concurrentTask)
                        doFirst {
                            URL("${server.uri("unblockStart")}").readText()
                        }
                        doLast {
                            URL("${server.uri("unblockStop")}").readText()
                        }
                    }
                }
            """)
            withPrecompiledKotlinScript("some.gradle.kts", """plugins { id("applied-output") }""")

            server.expectConcurrent("blockStart", "unblockStart")
            server.expectConcurrent("blockStop", "unblockStop")

            build(":concurrentTask", ":compileKotlin").apply {
                assertThat(output.lineSequence().filter { it.startsWith("STDOUT from concurrent task") }.count(), equalTo(repeatOutput))
                assertThat(error.lineSequence().filter { it.startsWith("STDERR from concurrent task") }.count(), equalTo(repeatOutput))
                assertThat(output.lineSequence().filter { it.startsWith("WARN from concurrent task") }.count(), equalTo(repeatOutput))
                assertNotOutput("STDOUT from applied-output plugin")
                assertNotOutput("STDERR from applied-output plugin")
                // TODO logging is not captured yet
                assertOutputContains("WARN from applied-output plugin")
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `no warnings on absent directories in compilation classpath`() {
        withDefaultSettings().appendText("""include("producer", "consumer")""")
        withFile("producer/build.gradle.kts", """plugins { java }""")
        withKotlinDslPluginIn("consumer").appendText("""dependencies { implementation(project(":producer")) }""")
        withFile("consumer/src/main/kotlin/some.gradle.kts", "")
        build(":consumer:classes").apply {
            assertTaskExecuted(":consumer:compilePluginsBlocks")
            assertNotOutput("w: Classpath entry points to a non-existent location")
        }
        assertFalse(file("producer/build/classes/java/main").exists())
    }

    @Issue("https://github.com/gradle/gradle/issues/24788")
    @Test
    fun `fail with a reasonable message when kotlin-dsl plugin compiler arguments have been tempered with`() {
        assumeNonEmbeddedGradleExecuter()

        withKotlinDslPlugin().appendText(
            """
            tasks.compileKotlin {
                compilerOptions {
                    freeCompilerArgs.set(listOf("some"))
                }
            }
            """
        )
        withPrecompiledKotlinScript("some.gradle.kts", "")

        buildAndFail("compileKotlin").apply {
            assertHasFailure("Execution failed for task ':compileKotlin'.") {
                assertHasCause(
                    "Kotlin compiler arguments of task ':compileKotlin' do not work for the `kotlin-dsl` plugin. " +
                        "The 'freeCompilerArgs' property has been reassigned. " +
                        "It must instead be appended to. " +
                        "Please use 'freeCompilerArgs.addAll(\"your\", \"args\")' to fix this."
                )
            }
        }
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


private
fun File.prependText(text: String): File {
    writeText(text + "\n\n" + readText())
    return this
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
