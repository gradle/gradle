package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest
import org.gradle.kotlin.dsl.fixtures.DeepThought
import org.junit.Test


class KotlinInitScriptIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `initscript classpath`() {

        withClassJar("fixture.jar", DeepThought::class.java)

        val initScript =
            withFile("init.gradle.kts", """

                initscript {
                    dependencies { classpath(files("fixture.jar")) }
                }

                val computer = ${DeepThought::class.qualifiedName}()
                val answer = computer.compute()
                println("*" + answer + "*")
            """)

        assert(
            build("-I", initScript.canonicalPath)
                .output.contains("*42*"))
    }

    @Test
    fun `initscript file path is resolved relative to parent script dir`() {

        val initScript =
            withFile("gradle/init.gradle.kts", """
                apply { from("./answer.gradle.kts") }
            """)

        withFile("gradle/answer.gradle.kts", """
            rootProject {
                val answer by extra { "42" }
            }
        """)

        withBuildScript("""
            val answer: String by extra
            println("*" + answer + "*")
        """)

        assert(
            build("-I", initScript.canonicalPath)
                .output.contains("*42*"))
    }
}
