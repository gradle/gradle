package org.gradle.kotlin.dsl.plugins.precompiled

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.bundling.Jar

import org.gradle.kotlin.dsl.fixtures.assertFailsWith
import org.gradle.kotlin.dsl.fixtures.assertInstanceOf
import org.gradle.kotlin.dsl.fixtures.assertStandardOutputOf
import org.gradle.kotlin.dsl.fixtures.withFolders

import org.gradle.kotlin.dsl.precompile.PrecompiledInitScript
import org.gradle.kotlin.dsl.precompile.PrecompiledProjectScript
import org.gradle.kotlin.dsl.precompile.PrecompiledSettingsScript

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class PrecompiledScriptPluginTest : AbstractPrecompiledScriptPluginTest() {

    @Test
    fun `Project scripts from regular source-sets are compiled via the PrecompiledProjectScript template`() {

        givenPrecompiledKotlinScript("my-project-script.gradle.kts", """

            task("my-task")

        """)

        val project = mock<Project>()

        assertInstanceOf<PrecompiledProjectScript>(
            instantiatePrecompiledScriptOf(
                project,
                "My_project_script_gradle"))

        verify(project).task("my-task")
    }

    @Test
    fun `Settings scripts from regular source-sets are compiled via the PrecompiledSettingsScript template`() {

        givenPrecompiledKotlinScript("my-settings-script.settings.gradle.kts", """

            include("my-project")

        """)

        val settings = mock<Settings>()

        assertInstanceOf<PrecompiledSettingsScript>(
            instantiatePrecompiledScriptOf(
                settings,
                "My_settings_script_settings_gradle"))

        verify(settings).include("my-project")
    }

    @Test
    fun `Gradle scripts from regular source-sets are compiled via the PrecompiledInitScript template`() {

        givenPrecompiledKotlinScript("my-gradle-script.init.gradle.kts", """

            useLogger("my-logger")

        """)

        val gradle = mock<Gradle>()

        assertInstanceOf<PrecompiledInitScript>(
            instantiatePrecompiledScriptOf(
                gradle,
                "My_gradle_script_init_gradle"))

        verify(gradle).useLogger("my-logger")
    }

    @Test
    fun `plugin adapter doesn't mask exceptions thrown by precompiled script`() {

        // given:
        val expectedMessage = "Not on my watch!"

        withKotlinDslPlugin()

        withFile("src/main/kotlin/my-project-script.gradle.kts", """
            throw IllegalStateException("$expectedMessage")
        """)

        // when:
        compileKotlin()

        // then:
        @Suppress("unchecked_cast")
        val pluginAdapter =
            loadCompiledKotlinClass("MyProjectScriptPlugin")
                .getConstructor()
                .newInstance() as Plugin<Project>

        val exception =
            assertFailsWith(IllegalStateException::class) {
                pluginAdapter.apply(mock())
            }

        assertThat(
            exception.message,
            equalTo(expectedMessage))
    }

    @Test
    fun `implicit imports are available to precompiled scripts`() {

        givenPrecompiledKotlinScript("my-project-script.gradle.kts", """

            task<Jar>("jar")

        """)

        val task = mock<Jar>()
        val tasks = mock<TaskContainer> {
            on { create(any<String>(), any<Class<Task>>()) } doReturn task
        }
        val project = mock<Project> {
            on { getTasks() } doReturn tasks
        }

        instantiatePrecompiledScriptOf(
            project,
            "My_project_script_gradle")

        verify(tasks).create("jar", Jar::class.java)
    }

    @Test
    fun `precompiled script plugin ids are honored by java-gradle-plugin plugin`() {

        projectRoot.withFolders {

            "buildSrc" {

                "src/main/kotlin" {

                    // Plugin id for script with no package declaration is simply
                    // the file name minus the script file extension.

                    // Project plugins must be named `*.gradle.kts`
                    withFile("my-plugin.gradle.kts", """
                        println("my-plugin applied!")
                    """)

                    // Settings plugins must be named `*.settings.gradle.kts`
                    withFile("my-settings-plugin.settings.gradle.kts", """
                        println("my-settings-plugin applied!")
                    """)

                    // Gradle object plugins, a.k.a., precompiled init script plugins,
                    // must be named `*.init.gradle.kts`
                    withFile("my-init-plugin.init.gradle.kts", """
                        println("my-init-plugin applied!")
                    """)

                    // plugin id for script with package declaration is the
                    // package name dot the file name minus the `.gradle.kts` suffix
                    withFile("org/acme/my-other-plugin.gradle.kts", """
                        package org.acme

                        println("my-other-plugin applied!")
                    """)
                }

                withFile("settings.gradle.kts", """

                    $pluginManagementBlock

                """)

                withFile(
                    "build.gradle.kts",
                    scriptWithKotlinDslPlugin())
            }
        }

        withSettings("""

            // Apply Gradle plugin via type as it cannot be applied via id
            // because `buildSrc` is not in the `gradle` object
            // plugin search classpath

            gradle.apply<MyInitPluginPlugin>()

            apply(plugin = "my-settings-plugin")
        """)

        withBuildScript("""
            plugins {
                id("my-plugin")
                id("org.acme.my-other-plugin")
            }
        """)

        assertThat(
            build("help").output,
            allOf(
                containsString("my-init-plugin applied!"),
                containsString("my-settings-plugin applied!"),
                containsString("my-plugin applied!"),
                containsString("my-other-plugin applied!")
            )
        )
    }

    @Test
    fun `precompiled script plugins can be published by maven-publish plugin`() {

        withFolders {

            "plugins" {

                "src/main/kotlin" {

                    withFile("my-plugin.gradle.kts", """
                        println("my-plugin applied!")
                    """)

                    withFile("org/acme/my-other-plugin.gradle.kts", """
                        package org.acme

                        println("org.acme.my-other-plugin applied!")
                    """)

                    withFile("org/acme/plugins/my-init.init.gradle.kts", """

                        package org.acme.plugins

                        println("org.acme.plugins.my-init applied!")
                    """)
                }

                withFile("settings.gradle.kts", """

                    $pluginManagementBlock

                """)

                withFile("build.gradle.kts", """

                    plugins {
                        `kotlin-dsl`
                        `maven-publish`
                    }

                    group = "org.acme"

                    version = "0.1.0"

                    $repositoriesBlock

                    publishing {
                        repositories {
                            maven(url = "../repository")
                        }
                    }
                """)
            }
        }

        build(existing("plugins"), "publish")

        val repositoriesBlock = """
            repositories {
                maven { url = uri("./repository") }
            }
        """

        withSettings("""
            pluginManagement {
                $repositoriesBlock
            }
        """)

        withBuildScript("""
            plugins {
                id("my-plugin") version "0.1.0"
                id("org.acme.my-other-plugin") version "0.1.0"
            }
        """)

        val initScript =
            withFile("my-init-script.init.gradle.kts", """

                initscript {
                    $repositoriesBlock
                    dependencies {
                        classpath("org.acme:plugins:0.1.0")
                    }
                }

                apply<org.acme.plugins.MyInitPlugin>()

                // TODO: can't apply plugin by id
                // apply(plugin = "org.acme.plugins.my-init")
            """)

        assertThat(
            build("help", "-I", initScript.canonicalPath).output,
            allOf(
                containsString("org.acme.plugins.my-init applied!"),
                containsString("my-plugin applied!"),
                containsString("org.acme.my-other-plugin applied!")
            )
        )
    }

    @Test
    fun `precompiled script plugins can use Kotlin 1 dot 3 language features`() {

        givenPrecompiledKotlinScript("my-plugin.gradle.kts", """

            // Coroutines are no longer experimental
            val coroutine = sequence {
                // Unsigned integer types
                yield(42UL)
            }

            when (val value = coroutine.first()) {
                42UL -> print("42!")
                else -> throw IllegalStateException()
            }
        """)

        assertStandardOutputOf("42!") {
            instantiatePrecompiledScriptOf(
                mock<Project>(),
                "My_plugin_gradle"
            )
        }
    }
}
