package org.gradle.kotlin.dsl.plugins.dsl

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.Project
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.fixtures.assertInstanceOf

import org.gradle.kotlin.dsl.fixtures.classLoaderFor
import org.gradle.kotlin.dsl.plugins.AbstractPluginTest
import org.gradle.kotlin.dsl.precompile.PrecompiledProjectScript
import org.gradle.kotlin.dsl.precompile.PrecompiledSettingsScript

import org.gradle.testkit.runner.TaskOutcome

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


// TODO: test for precompiled Gradle (init) scripts
// TODO: test for FileProvider API behaviour
// TODO: test for ScriptApi behaviour
class PrecompiledScriptPluginTest : AbstractPluginTest() {

    @Test
    fun `Project scripts from regular source-sets are compiled via the PrecompiledProjectScript template`() {

        withKotlinDslPlugin()

        withFile("src/main/kotlin/my-project-script.gradle.kts", """

            task("my-task")

        """)

        compileKotlin()

        val project = mock<Project>()

        assertInstanceOf<PrecompiledProjectScript>(
            instantiatePrecompiledScriptOf(
                project,
                "My_project_script_gradle"))

        verify(project).task("my-task")
    }

    @Test
    fun `Settings scripts from regular source-sets are compiled via the PrecompiledSettingsScript template`() {

        withKotlinDslPlugin()

        withFile("src/main/kotlin/my-settings-script.settings.gradle.kts", """

            include("my-project")

        """)

        compileKotlin()

        val settings = mock<Settings>()

        assertInstanceOf<PrecompiledSettingsScript>(
            instantiatePrecompiledScriptOf(
                settings,
                "My_settings_script_settings_gradle"))

        verify(settings).include("my-project")
    }

    private inline
    fun <reified T> instantiatePrecompiledScriptOf(target: T, className: String): Any =
        loadCompiledKotlinClass(className)
            .getConstructor(T::class.java)
            .newInstance(target)

    private
    fun loadCompiledKotlinClass(className: String) =
        classLoaderFor(existing("build/classes/kotlin/main"))
            .loadClass(className)

    private
    fun withKotlinDslPlugin() =
        withBuildScript("""

            plugins {
                `kotlin-dsl`
            }

        """)

    private
    fun compileKotlin() {
        assertThat(
            buildWithPlugin("classes").outcomeOf(":compileKotlin"),
            equalTo(TaskOutcome.SUCCESS))
    }
}
