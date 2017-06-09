package org.gradle.script.lang.kotlin.compiler

import org.gradle.script.lang.kotlin.fixtures.AbstractIntegrationTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.StringContains.containsString
import org.junit.Test


class CompilerPluginIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `can be used as a Kotlin compiler plugin`() {

        withFile("src/main/kotlin/my/GradlePlugin.kt", """
            package my

            import org.gradle.api.*

            open class GradlePlugin : Plugin<Project> {
                override fun apply(project: Project) {
                    project.copy { // Action<T> with implicit receiver
                        from("source") // Look, ma, no `it`!
                        into("target")
                    }
                }
            }
        """)

        withBuildScript("""
            import org.jetbrains.kotlin.gradle.tasks.*

            buildscript {
                repositories { gradleScriptKotlin() }
                dependencies { classpath(kotlin("gradle-plugin")) }
            }

            apply { plugin("kotlin") }

            dependencies { compile(gradleApi()) }

            val compilerPluginVersion = KotlinBuildScript::class.java.`package`.implementationVersion
            val compilerPluginFileName = "lib/gradle-script-kotlin-compiler-plugin-" + compilerPluginVersion + ".jar"
            val compilerPlugin = File(gradle.gradleHomeDir, compilerPluginFileName)
            tasks.withType<KotlinCompile> {
                require(compilerPlugin.exists()) { "Compiler plugin could not be found! " + compilerPlugin }
                kotlinOptions.freeCompilerArgs += listOf("-Xplugin", compilerPlugin.path)
            }
        """)

        assertThat(
            build("assemble").output,
            containsString("BUILD SUCCESSFUL"))
    }
}
