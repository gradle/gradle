/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.kotlin.dsl.plugins.precompiled

import com.nhaarman.mockito_kotlin.KStubbing
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.same
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.initialization.Settings
import org.gradle.api.invocation.Gradle
import org.gradle.api.plugins.ObjectConfigurationAction
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.bundling.Jar

import org.gradle.kotlin.dsl.fixtures.FoldersDslExpression
import org.gradle.kotlin.dsl.fixtures.assertFailsWith
import org.gradle.kotlin.dsl.fixtures.assertInstanceOf
import org.gradle.kotlin.dsl.fixtures.assertStandardOutputOf
import org.gradle.kotlin.dsl.fixtures.withFolders

import org.gradle.kotlin.dsl.precompile.v1.PrecompiledInitScript
import org.gradle.kotlin.dsl.precompile.v1.PrecompiledProjectScript
import org.gradle.kotlin.dsl.precompile.v1.PrecompiledSettingsScript

import org.gradle.test.fixtures.file.LeaksFileHandles

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.jetbrains.kotlin.name.NameUtils

import org.junit.Test

import org.mockito.invocation.InvocationOnMock

import java.io.File


@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class PrecompiledScriptPluginTemplatesTest : AbstractPrecompiledScriptPluginTest() {

    @Test
    fun `Project scripts from regular source-sets are compiled via the PrecompiledProjectScript template`() {

        givenPrecompiledKotlinScript(
            "my-project-script.gradle.kts",
            """

            task("my-task")

            """
        )

        val task = mock<Task>()
        val project = mock<Project> {
            on { task(any()) } doReturn task
        }

        assertInstanceOf<PrecompiledProjectScript>(
            instantiatePrecompiledScriptOf(
                project,
                "My_project_script_gradle"
            )
        )

        inOrder(project, task) {
            verify(project).task("my-task")
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `Settings scripts from regular source-sets are compiled via the PrecompiledSettingsScript template`() {

        givenPrecompiledKotlinScript(
            "my-settings-script.settings.gradle.kts",
            """

            include("my-project")

            """
        )

        val settings = mock<Settings>()

        assertInstanceOf<PrecompiledSettingsScript>(
            instantiatePrecompiledScriptOf(
                settings,
                "My_settings_script_settings_gradle"
            )
        )

        verify(settings).include("my-project")
    }

    @Test
    fun `Gradle scripts from regular source-sets are compiled via the PrecompiledInitScript template`() {

        givenPrecompiledKotlinScript(
            "my-gradle-script.init.gradle.kts",
            """

            addListener("my-listener")

            """
        )

        val gradle = mock<Gradle>()

        assertInstanceOf<PrecompiledInitScript>(
            instantiatePrecompiledScriptOf(
                gradle,
                "My_gradle_script_init_gradle"
            )
        )

        verify(gradle).addListener("my-listener")
    }

    @Test
    fun `plugin adapter doesn't mask exceptions thrown by precompiled script`() {

        // given:
        val expectedMessage = "Not on my watch!"

        withKotlinDslPlugin()

        withFile(
            "src/main/kotlin/my-project-script.gradle.kts",
            """
            throw IllegalStateException("$expectedMessage")
            """
        )

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
            equalTo(expectedMessage)
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun `implicit imports are available to precompiled scripts`() {

        givenPrecompiledKotlinScript(
            "my-project-script.gradle.kts",
            """

            task<Jar>("jar")

            """
        )

        val task = mock<Jar>()
        val tasks = mock<TaskContainer> {
            on { create(any<String>(), any<Class<Task>>()) } doReturn task
        }
        val project = mock<Project> {
            on { getTasks() } doReturn tasks
        }

        instantiatePrecompiledScriptOf(
            project,
            "My_project_script_gradle"
        )

        verify(tasks).create("jar", Jar::class.java)
    }

    @Test
    fun `precompiled script plugin ids are honored by java-gradle-plugin plugin`() {

        projectRoot.withFolders {

            "plugin" {

                "src/main/kotlin" {

                    // Plugin id for script with no package declaration is simply
                    // the file name minus the script file extension.

                    // Project plugins must be named `*.gradle.kts`
                    withFile(
                        "my-plugin.gradle.kts",
                        """
                        println("my-plugin applied!")
                        """
                    )

                    // Settings plugins must be named `*.settings.gradle.kts`
                    withFile(
                        "my-settings-plugin.settings.gradle.kts",
                        """
                        println("my-settings-plugin applied!")
                        """
                    )

                    // Gradle object plugins, a.k.a., precompiled init script plugins,
                    // must be named `*.init.gradle.kts`
                    withFile(
                        "my-init-plugin.init.gradle.kts",
                        """
                        println("my-init-plugin applied!")
                        """
                    )

                    // plugin id for script with package declaration is the
                    // package name dot the file name minus the `.gradle.kts` suffix
                    withFile(
                        "org/acme/my-other-plugin.gradle.kts",
                        """
                        package org.acme

                        println("my-other-plugin applied!")
                        """
                    )
                }

                withFile("settings.gradle.kts", defaultSettingsScript)

                withFile(
                    "build.gradle.kts",
                    scriptWithKotlinDslPlugin()
                )
            }
        }

        executer.inDirectory(file("plugin")).withTasks("jar").run()

        val pluginJar = file("plugin/build/libs/plugin.jar")
        assertThat("pluginJar was built", pluginJar.exists())
        val movedPluginJar = file("plugin.jar")
        pluginJar.renameTo(movedPluginJar)

        withDefaultSettings().appendText(
            """
            buildscript {
                dependencies {
                    classpath(files("${movedPluginJar.name}"))
                }
            }

            gradle.apply<MyInitPluginPlugin>()
            apply(plugin = "my-settings-plugin")
            """
        )

        withFile(
            "buildSrc/build.gradle",
            """
            dependencies {
                api files("../${movedPluginJar.name}")
            }
            """
        )

        withBuildScript(
            """
            plugins {
                id("my-plugin")
                id("org.acme.my-other-plugin")
            }
            """
        )


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

        val repository = newDir("repository")

        publishPluginsTo(repository) {

            withFile(
                "my-plugin.gradle.kts",
                """
                println("my-plugin applied!")
                """
            )

            withFile(
                "org/acme/my-other-plugin.gradle.kts",
                """
                package org.acme

                println("org.acme.my-other-plugin applied!")
                """
            )

            withFile(
                "org/acme/plugins/my-init.init.gradle.kts",
                """
                package org.acme.plugins

                println("org.acme.plugins.my-init applied!")
                """
            )
        }

        val repositoriesBlock = repositoriesBlockFor(repository)

        withSettings(
            """
            pluginManagement {
                $repositoriesBlock
            }
            """
        )

        withBuildScript(
            """
            plugins {
                id("my-plugin") version "1.0"
                id("org.acme.my-other-plugin") version "1.0"
            }
            """
        )

        val initScript =
            withFile(
                "my-init-script.init.gradle.kts",
                """

                initscript {
                    $repositoriesBlock
                    dependencies {
                        classpath("org.acme:plugins:1.0")
                    }
                }

                apply<org.acme.plugins.MyInitPlugin>()

                // TODO: can't apply plugin by id
                // apply(plugin = "org.acme.plugins.my-init")
                """
            )

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

        givenPrecompiledKotlinScript(
            "my-plugin.gradle.kts",
            """

            // Coroutines are no longer experimental
            val coroutine = sequence {
                // Unsigned integer types
                yield(42UL)
            }

            when (val value = coroutine.first()) {
                42UL -> print("42!")
                else -> throw IllegalStateException()
            }
            """
        )

        assertStandardOutputOf("42!") {
            instantiatePrecompiledScriptOf(
                mock<Project>(),
                "My_plugin_gradle"
            )
        }
    }

    @Test
    fun `precompiled project script template honors HasImplicitReceiver`() {

        assertHasImplicitReceiverIsHonoredByScriptOf<Project>("my-project-plugin.gradle.kts")
    }

    @Test
    fun `precompiled settings script template honors HasImplicitReceiver`() {

        assertHasImplicitReceiverIsHonoredByScriptOf<Settings>("my-settings-plugin.settings.gradle.kts")
    }

    @Test
    fun `precompiled init script template honors HasImplicitReceiver`() {

        assertHasImplicitReceiverIsHonoredByScriptOf<Gradle>("my-init-plugin.init.gradle.kts")
    }

    @Test
    fun `precompiled project script receiver is undecorated`() {

        assertUndecoratedImplicitReceiverOf<Project>("my-project-plugin.gradle.kts")
    }

    @Test
    fun `precompiled settings script receiver is undecorated`() {

        assertUndecoratedImplicitReceiverOf<Settings>("my-settings-plugin.settings.gradle.kts")
    }

    @Test
    fun `precompiled init script receiver is undecorated`() {

        assertUndecoratedImplicitReceiverOf<Gradle>("my-init-plugin.init.gradle.kts")
    }

    @Test
    fun `nested plugins block fails to compile with reasonable message`() {

        withKotlinDslPlugin()
        withPrecompiledKotlinScript(
            "my-project-plugin.gradle.kts",
            """
            project(":nested") {
                plugins {
                    java
                }
            }
            """
        )

        buildAndFail("classes").run {
            assertHasDescription(
                "Execution failed for task ':compileKotlin'."
            )
            assertHasErrorOutput(
                """my-project-plugin.gradle.kts:3:17 Using 'plugins(PluginDependenciesSpec.() -> Unit): Nothing' is an error. The plugins {} block must not be used here. If you need to apply a plugin imperatively, please use apply<PluginType>() or apply(plugin = "id") instead."""
            )
        }
    }

    @Test
    fun `can apply plugin using ObjectConfigurationAction syntax`() {

        val pluginsRepository = newDir("repository")

        publishPluginsTo(pluginsRepository) {

            withFile(
                "MyInit.init.gradle.kts",
                """

                open class GradlePlugin : Plugin<Gradle> {
                    override fun apply(target: Gradle) = println("Gradle!")
                }

                apply { plugin<GradlePlugin>() }

                """
            )

            withFile(
                "MySettings.settings.gradle.kts",
                """

                open class SettingsPlugin : Plugin<Settings> {
                    override fun apply(target: Settings) = println("Settings!")
                }

                gradle.apply { plugin<MyInitPlugin>() }

                apply { plugin<SettingsPlugin>() }

                """
            )

            withFile(
                "MyProject.gradle.kts",
                """

                open class ProjectPlugin : Plugin<Project> {
                    override fun apply(target: Project) {
                        val projectName = target.name
                        target.task("run") {
                            doLast { println("Project " + projectName + "!") }
                        }
                    }
                }

                apply { plugin<ProjectPlugin>() }

                subprojects {
                    apply { plugin<ProjectPlugin>() }
                }
                """
            )
        }

        val pluginRepositoriesBlock = repositoriesBlockFor(pluginsRepository)

        withSettings(
            """
            pluginManagement {
                $pluginRepositoriesBlock
            }

            plugins {
                id("MySettings") version "1.0"
            }

            rootProject.name = "foo"

            include("bar")
            """
        )

        withBuildScript(
            """
            plugins { id("MyProject") }
            """
        )

        assertThat(
            build("run", "-q").output,
            allOf(
                containsString("Gradle!"),
                containsString("Settings!"),
                containsString("Project foo!"),
                containsString("Project bar!")
            )
        )
    }

    @Test
    fun `can use PluginAware extensions against nested receiver`() {

        val scriptFileName = "my-project-plugin.gradle.kts"

        givenPrecompiledKotlinScript(
            scriptFileName,
            """
            project(":nested") {
                apply(from = "./gradle/conventions.gradle.kts")
            }
            """
        )

        val configurationAction = mock<ObjectConfigurationAction>()
        val nestedReceiver = mock<Project> {
            on { apply(any<Action<ObjectConfigurationAction>>()) } doAnswer {
                it.executeActionArgument(0, configurationAction)
                Unit
            }
        }
        val project = mock<Project> {
            onProject(":nested", nestedReceiver)
        }

        instantiatePrecompiledScriptOf(
            project,
            scriptClassNameForFile(scriptFileName)
        )

        inOrder(configurationAction) {
            verify(configurationAction).from("./gradle/conventions.gradle.kts")
            verifyNoMoreInteractions()
        }
    }

    private
    fun KStubbing<Project>.onProject(path: String, project: Project) {
        on { project(eq(path), any<Action<Project>>()) } doAnswer {
            it.executeActionArgument(1, project)
            project
        }
    }

    private
    fun <T : Any> InvocationOnMock.executeActionArgument(index: Int, configurationAction: T) {
        getArgument<Action<T>>(index).execute(configurationAction)
    }

    @Suppress("deprecation")
    private
    inline fun <reified T : Any> assertUndecoratedImplicitReceiverOf(fileName: String) {

        givenPrecompiledKotlinScript(
            fileName,
            """
            val ${T::class.simpleName}.receiver get() = this
            (receiver as ${org.gradle.api.internal.HasConvention::class.qualifiedName}).convention.add("receiver", receiver)
            """
        )

        val convention = mock<org.gradle.api.plugins.Convention>()
        val receiver = mock<org.gradle.api.internal.HasConvention>(extraInterfaces = arrayOf(T::class)) {
            on { getConvention() } doReturn convention
        }

        instantiatePrecompiledScriptOf(
            receiver as T,
            scriptClassNameForFile(fileName)
        )

        verify(convention).add(
            eq("receiver"),
            same(receiver)
        )
    }

    private
    inline fun <reified T : Any> assertHasImplicitReceiverIsHonoredByScriptOf(fileName: String) {

        // Action<T> <=> T.() -> Unit because HasImplicitReceiver
        givenPrecompiledKotlinScript(
            fileName,
            """
            fun <T> applyActionTo(a: T, action: ${Action::class.qualifiedName}<T>) = action(a)
            object receiver
            applyActionTo(receiver) {
                require(this === receiver)
                print("42!")
            }
            """
        )

        assertStandardOutputOf("42!") {
            instantiatePrecompiledScriptOf(
                mock<T>(),
                scriptClassNameForFile(fileName)
            )
        }
    }

    private
    fun repositoriesBlockFor(repository: File): String = """
        repositories {
            maven { url = uri("${repository.toURI()}") }
        }
    """

    private
    fun publishPluginsTo(
        repository: File,
        group: String = "org.acme",
        version: String = "1.0",
        sourceFiles: FoldersDslExpression
    ) {
        withFolders {

            "plugins" {

                "src/main/kotlin" {
                    sourceFiles()
                }

                withFile("settings.gradle.kts", defaultSettingsScript)

                withFile(
                    "build.gradle.kts",
                    """

                    plugins {
                        `kotlin-dsl`
                        `maven-publish`
                    }

                    group = "$group"

                    version = "$version"

                    $repositoriesBlock

                    publishing {
                        ${repositoriesBlockFor(repository)}
                    }
                    """
                )
            }
        }

        build(
            existing("plugins"),
            "publish"
        )
    }

    private
    fun scriptClassNameForFile(fileName: String) =
        NameUtils.getScriptNameForFile(fileName).asString()
}
