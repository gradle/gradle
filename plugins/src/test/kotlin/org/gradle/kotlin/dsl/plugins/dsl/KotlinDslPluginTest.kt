package org.gradle.kotlin.dsl.plugins.dsl

import org.gradle.kotlin.dsl.plugins.AbstractPluginTest

import org.gradle.testkit.runner.TaskOutcome

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
