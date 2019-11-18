package org.gradle.kotlin.dsl.integration

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.DeepThought
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule

import org.junit.Test


class KotlinSettingsScriptIntegrationTest : AbstractKotlinIntegrationTest() {

    @Rule
    @JvmField
    val pluginPortal: MavenHttpPluginRepository = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    @Test
    @ToBeFixedForInstantExecution
    fun `can apply plugin using ObjectConfigurationAction syntax`() {

        val pluginJar = file("plugin.jar")

        PluginBuilder(file("plugin")).run {
            packageName = null
            addSettingsPlugin("", "test.MySettingsPlugin", "MySettingsPlugin")
            publishTo(executer, pluginJar)
        }

        withSettings("""
            buildscript {
                dependencies {
                    classpath(files("${pluginJar.name}"))
                }
            }
            apply {
                plugin<MySettingsPlugin>()
            }
        """)

        withBuildScript("")
        build("help", "-q")
    }

    @Test
    @ToBeFixedForInstantExecution
    fun `can apply plugin using plugins block`() {

        PluginBuilder(file("plugin")).run {
            addSettingsPlugin("println '*42*'", "test.MySettingsPlugin", "MySettingsPlugin")
            publishAs("g", "m", "1.0", pluginPortal, createExecuter()).allowAll()
        }

        withSettings("""
            plugins {
                id("test.MySettingsPlugin").version("1.0")
            }
        """)

        assertThat(
            build().output,
            containsString("*42*")
        )
    }

    @Test
    fun `Settings script path is resolved relative to parent script dir`() {

        withFile("gradle/my.settings.gradle.kts", """
            apply(from = "./answer.settings.gradle.kts")
        """)

        withFile("gradle/answer.settings.gradle.kts", """
            gradle.rootProject {
                val answer by extra { "42" }
            }
        """)

        withSettings("""
            apply(from = "gradle/my.settings.gradle.kts")
        """)

        withBuildScript("""
            val answer: String by extra
            println("*" + answer + "*")
        """)

        assertThat(
            build().output,
            containsString("*42*"))
    }

    @Test
    fun `pluginManagement block cannot appear twice in settings scripts`() {

        withSettings("""
            pluginManagement {}
            pluginManagement {}
        """)

        assertThat(
            buildAndFail("help").error,
            containsString("settings.gradle.kts:3:13: Unexpected `pluginManagement` block found. Only one `pluginManagement` block is allowed per script."))
    }

    @Test
    fun `given a script plugin with a buildscript block, it will be used to compute its classpath`() {

        withClassJar("fixture.jar", DeepThought::class.java)

        withFile("other.settings.gradle.kts", """
            buildscript {
                dependencies { classpath(files("fixture.jar")) }
            }

            gradle.rootProject {
                task("compute") {
                    doLast {
                        val computer = ${DeepThought::class.qualifiedName}()
                        val answer = computer.compute()
                        println("*" + answer + "*")
                    }
                }
            }
        """)

        withSettings("""
            apply(from = "other.settings.gradle.kts")
        """)

        assert(
            build("compute").output.contains("*42*"))
    }
}
