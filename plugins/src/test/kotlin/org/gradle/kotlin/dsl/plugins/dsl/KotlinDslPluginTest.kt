package org.gradle.kotlin.dsl.plugins.dsl

import org.gradle.kotlin.dsl.plugins.AbstractPluginTest

import org.gradle.testkit.runner.TaskOutcome

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo

import org.junit.Assert.assertThat
import org.junit.Test


class KotlinDslPluginTest : AbstractPluginTest() {

    @Test
    fun `gradle kotlin dsl api dependency is added`() {

        withBuildScript("""

            plugins {
                `kotlin-dsl`
            }

        """)

        withFile("src/main/kotlin/code.kt", """

            // src/main/kotlin
            import org.gradle.kotlin.dsl.GradleDsl

            // src/generated
            import org.gradle.kotlin.dsl.embeddedKotlinVersion

        """)

        val result = buildWithPlugin("classes")

        assertThat(result.outcomeOf(":compileKotlin"), equalTo(TaskOutcome.SUCCESS))
    }

    @Test
    fun `gradle kotlin dsl api is available at test runtime`() {
        withBuildScript("""

            plugins {
                `java-gradle-plugin`
                `kotlin-dsl`
            }

            repositories {
                jcenter()
            }

            dependencies {
                testCompile("junit:junit:4.12")
            }

        """)

        withFile("src/main/kotlin/code.kt", """

            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.kotlin.dsl.embeddedKotlinVersion

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    project.run {
                        println("Plugin Using Embedded Kotlin " + embeddedKotlinVersion)
                    }
                }
            }
        """)

        withFile("src/test/kotlin/test.kt", """

            import org.gradle.testfixtures.ProjectBuilder
            import org.junit.Test

            class MyTest {

                @Test
                fun `my test`() {
                    val project = ProjectBuilder.builder().build()
                    project.plugins.apply(MyPlugin::class.java)
                }
            }

        """)

        val result = buildWithPlugin("test", "-i")

        assertThat(result.output, containsString("Plugin Using Embedded Kotlin "))
    }

    @Test
    fun `sam-with-receiver kotlin compiler plugin is applied to production code`() {

        withBuildScript("""

            plugins {
                `kotlin-dsl`
            }

        """)

        withFile("src/main/kotlin/code.kt", """

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class MyPlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    project.run {
                        copy {
                            from("build.gradle.kts")
                            into("build/build.gradle.kts.copy")
                        }
                    }
                }
            }

        """)

        val result = buildWithPlugin("classes")

        assertThat(result.outcomeOf(":compileKotlin"), equalTo(TaskOutcome.SUCCESS))
    }
}
