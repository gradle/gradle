package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.DeepThought
import org.gradle.kotlin.dsl.fixtures.withFolders

import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class KotlinInitScriptIntegrationTest : AbstractKotlinIntegrationTest() {

    @Test
    fun `initscript classpath`() {

        withClassJar("fixture.jar", DeepThought::class.java)

        val initScript =
            withFile(
                "init.gradle.kts",
                """

                initscript {
                    dependencies { classpath(files("fixture.jar")) }
                }

                val computer = ${DeepThought::class.qualifiedName}()
                val answer = computer.compute()
                println("*" + answer + "*")
                """
            )

        assert(
            build("-I", initScript.canonicalPath)
                .output.contains("*42*")
        )
    }

    @Test
    fun `initscript file path is resolved relative to parent script dir`() {

        val initScript =
            withFile(
                "gradle/init.gradle.kts",
                """
                apply(from = "./answer.gradle.kts")
                """
            )

        withFile(
            "gradle/answer.gradle.kts",
            """
            rootProject {
                val answer by extra { "42" }
            }
            """
        )

        withBuildScript(
            """
            val answer: String by extra
            println("*" + answer + "*")
            """
        )

        assert(
            build("-I", initScript.canonicalPath)
                .output.contains("*42*")
        )
    }

    @Test
    fun `Kotlin init scripts from init dir can add buildscript repositories to projects`() {

        val testRepositoryDir = file("test-repository").apply { mkdirs() }

        val guh = file("gradle-user-home").apply { mkdirs() }
        guh.withFolders {
            "init.d" {
                withFile(
                    "init.gradle.kts",
                    """
                    allprojects {
                        buildscript.repositories {
                            maven {
                                name = "test-repository"
                                url = uri("${testRepositoryDir.toURI()}")
                            }
                        }
                    }
                    """
                )
            }
        }

        withBuildScript(
            """
            buildscript {
                repositories.forEach {
                    println("*" + it.name + "*")
                }
            }
            """
        )

        executer.withGradleUserHomeDir(guh)
        executer.requireIsolatedDaemons()

        assertThat(
            build().output,
            containsString("*test-repository*")
        )
    }

    @Test
    fun `given a script plugin with an initscript block, it will be used to compute its classpath`() {

        withClassJar("fixture.jar", DeepThought::class.java)

        withFile(
            "plugin.init.gradle.kts",
            """
            initscript {
                dependencies { classpath(files("fixture.jar")) }
            }

            rootProject {
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

        val initScript =
            withFile(
                "init.gradle.kts",
                """
                apply(from = "plugin.init.gradle.kts")
                """
            )

        withSettings("")

        assertThat(
            build("compute", "-I", initScript.canonicalPath).output,
            containsString("*42*")
        )
    }
}
