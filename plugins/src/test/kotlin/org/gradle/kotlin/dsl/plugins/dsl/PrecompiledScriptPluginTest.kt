package org.gradle.kotlin.dsl.plugins.dsl

import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify

import org.gradle.api.Project
import org.gradle.kotlin.dsl.fixtures.assertInstanceOf

import org.gradle.kotlin.dsl.fixtures.classLoaderFor
import org.gradle.kotlin.dsl.plugins.AbstractPluginTest
import org.gradle.kotlin.dsl.precompile.PrecompiledProjectScript

import org.gradle.testkit.runner.TaskOutcome

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


// TODO: test for precompiled Project scripts
// TODO: test for precompiled Settings scripts
// TODO: test for precompiled Gradle (init) scripts
// TODO: test for FileProvider API behaviour
// TODO: test for ScriptApi behaviour
class PrecompiledScriptPluginTest : AbstractPluginTest() {

    @Test
    fun `Project scripts from regular source-sets are compiled via the PrecompiledProjectScript template`() {

        withBuildScript("""

            plugins {
                `kotlin-dsl`
            }

        """)

        withFile("src/main/kotlin/my-script-plugin.gradle.kts", """

            task("my-task")

        """)

        assertThat(
            buildWithPlugin("classes").outcomeOf(":compileKotlin"),
            equalTo(TaskOutcome.SUCCESS))

        val project = mock<Project>()

        assertInstanceOf<PrecompiledProjectScript>(
            classLoaderFor(existing("build/classes/kotlin/main"))
                .loadClass("My_script_plugin_gradle")
                .getConstructor(Project::class.java)
                .newInstance(project))

        verify(project).task("my-task")
    }
}
