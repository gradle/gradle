package org.gradle.kotlin.dsl.integration

import org.gradle.api.internal.FeaturePreviews
import org.gradle.kotlin.dsl.fixtures.AbstractPluginTest
import org.gradle.kotlin.dsl.fixtures.DeepThought
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class KotlinSettingsScriptIntegrationTest : AbstractPluginTest() {

    @Test
    fun `can apply plugin using ObjectConfigurationAction syntax`() {

        val pluginJar = file("plugin.jar")

        PluginBuilder(file("plugin")).run {
            packageName = null
            addSettingsPlugin("", "test.MySettingsPlugin", "MySettingsPlugin")
            publishTo(executer, pluginJar)
        }

        withSettings(
            """
            buildscript {
                dependencies {
                    classpath(files("${pluginJar.name}"))
                }
            }
            apply {
                plugin<MySettingsPlugin>()
            }
            """
        )

        withBuildScript("")
        build("help", "-q")
    }

    @Test
    fun `can apply plugin using plugins block`() {

        val pluginPortal: MavenHttpPluginRepository = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)
        try {
            pluginPortal.start()

            PluginBuilder(file("plugin")).run {
                addSettingsPlugin("println '*42*'", "test.MySettingsPlugin", "MySettingsPlugin")
                publishAs("g", "m", "1.0", pluginPortal, createExecuter()).allowAll()
            }

            withSettings(
                """
                plugins {
                    id("test.MySettingsPlugin").version("1.0")
                }
                """
            )

            assertThat(
                build().output,
                containsString("*42*")
            )
        } finally {
            pluginPortal.stop()
        }
    }

    @Test
    fun `Settings script path is resolved relative to parent script dir`() {

        withFile(
            "gradle/my.settings.gradle.kts",
            """
            apply(from = "./answer.settings.gradle.kts")
            """
        )

        withFile(
            "gradle/answer.settings.gradle.kts",
            """
            gradle.rootProject {
                val answer by extra { "42" }
            }
            """
        )

        withSettings(
            """
            apply(from = "gradle/my.settings.gradle.kts")
            """
        )

        withBuildScript(
            """
            val answer: String by extra
            println("*" + answer + "*")
            """
        )

        assertThat(
            build().output,
            containsString("*42*")
        )
    }

    @Test
    fun `pluginManagement block cannot appear twice in settings scripts`() {

        withSettings(
            """
            pluginManagement {}
            pluginManagement {}
            """
        )

        assertThat(
            buildAndFail("help").error,
            containsString("settings.gradle.kts:3:13: Unexpected `pluginManagement` block found. Only one `pluginManagement` block is allowed per script.")
        )
    }

    @Test
    fun `given a script plugin with a buildscript block, it will be used to compute its classpath`() {

        withClassJar("fixture.jar", DeepThought::class.java)

        withFile(
            "other.settings.gradle.kts",
            """
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
            """
        )

        withSettings(
            """
            apply(from = "other.settings.gradle.kts")
            """
        )

        assert(
            build("compute").output.contains("*42*")
        )
    }

    @Test
    fun `can access settings extensions`() {
        withKotlinDslPluginIn("build-logic")
        withFile("build-logic/src/main/kotlin/MyExtension.kt", """
            interface MyExtension {
                fun some(message: String) { println(message) }
            }
        """)
        withFile("build-logic/src/main/kotlin/my-plugin.settings.gradle.kts", """
            extensions.create<MyExtension>("my")
        """)
        withSettings("""
            pluginManagement {
                includeBuild("build-logic")
            }
            plugins { id("my-plugin") }

            extensions.getByType(MyExtension::class).some("api.get")
            extensions.configure<MyExtension> { some("api.configure") }
            the<MyExtension>().some("kotlin.get")
            configure<MyExtension> { some("kotlin.configure") }
        """)
        withBuildScript("""tasks.register("noop")""")

        assertThat(
            build("noop", "-q").output.trim(),
            equalTo(
                """
                api.get
                api.configure
                kotlin.get
                kotlin.configure
                """.trimIndent()
            )
        )
    }

    @Test
    fun `enableSettingsPreview is available`() {
        withSettings("""
            rootProject.name = "under-test"
            enableFeaturePreview("${FeaturePreviews.Feature.TYPESAFE_PROJECT_ACCESSORS.name}")
        """)
        build("help")
    }
}
