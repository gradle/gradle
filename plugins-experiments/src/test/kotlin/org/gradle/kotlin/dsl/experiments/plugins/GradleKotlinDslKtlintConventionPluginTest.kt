package org.gradle.kotlin.dsl.experiments.plugins

import org.gradle.kotlin.dsl.embeddedKotlinVersion
import org.gradle.kotlin.dsl.fixtures.AbstractPluginTest

import org.gradle.testkit.runner.TaskOutcome

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.containsString

import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test


class GradleKotlinDslKtlintConventionPluginTest : AbstractPluginTest() {

    @Before
    fun setup() {
        withBuildScript("""
            plugins {
                kotlin("jvm") version "$embeddedKotlinVersion"
                id("org.gradle.kotlin.ktlint-convention")
            }

            repositories {
                jcenter()
            }
        """)
    }

    @Test
    fun `ktlint check tasks are cacheable`() {

        withFile("gradle.properties", "org.gradle.caching=true")
        existing("settings.gradle.kts").run {
            writeText(readText() + """
                buildCache {
                    local { isEnabled = false }
                    remote(DirectoryBuildCache::class.java) {
                        directory = file("local-build-cache")
                        isEnabled = true
                        isPush = true
                    }
                }
            """)
        }

        withSource("""val foo = "bar"""")

        build("ktlintMainCheck").apply {

            assertThat(outcomeOf(":ktlintMainCheck"), equalTo(TaskOutcome.SUCCESS))
        }

        build("ktlintMainCheck").apply {

            assertThat(outcomeOf(":ktlintMainCheck"), equalTo(TaskOutcome.UP_TO_DATE))
        }

        build("clean")

        build("ktlintMainCheck").apply {

            assertThat(outcomeOf(":ktlintMainCheck"), equalTo(TaskOutcome.FROM_CACHE))
        }
    }

    @Test
    fun `visibility modifiers on their own single line`() {

        withSource("""

            private val bar = false


            class Bazar(private val name: String) {

                private lateinit
                var description: String

                private
                inline fun something() = Unit
            }
        """)

        buildAndFail("ktlintMainCheck")

        assertKtlintErrors(3)
        assertKtLintError("Visibility modifiers must be on their own single line", 3, 13)
        assertKtLintError("Visibility modifiers must be on their own single line", 8, 17)
        assertKtLintError("Visibility modifiers must be on their own single line", 11, 17)

        withSource("""

            private
            val bar = false


            class Bazar(private val name: String) {

                private
                lateinit var description: String

                private
                inline fun something() = Unit
            }
        """)

        build("ktlintMainCheck")
    }

    @Test
    fun `allowed wildcard imports`() {

        withSource("""

            import java.util.*
            import org.w3c.dom.*

            import org.gradle.kotlin.dsl.*
        """)

        buildAndFail("ktlintMainCheck")

        assertKtlintErrors(1)
        assertKtLintError("Wildcard import not allowed (org.w3c.dom.*)", 4, 13)
    }

    @Test
    fun `blank lines`() {

        withSource("""
            package some

            import org.gradle.kotlin.dsl.*

            val foo = "bar"

            interface Foo



            object Bar


            data class Some(val name: String)
        """)

        buildAndFail("ktlintMainCheck")

        assertKtlintErrors(3)
        assertKtLintError("Top level elements must be separated by two blank lines", 4, 43)
        assertKtLintError("Top level elements must be separated by two blank lines", 6, 28)
        assertKtLintError("Needless blank line(s)", 10, 1)

        withSource("""
            /*
             * Copyright 2016 the original author or authors.
             */

            // Random words
            @file:JvmName("Something")

            /**
             * Package kdoc.
             */
            package some

            import org.gradle.kotlin.dsl.*


            /*
             * Some file documentation.
             */


            val foo = "bar"


            /**
             * Interface kdoc.
             */
            interface Foo


            object Bar


            data class Some(val name: String)
        """)

        build("ktlintMainCheck")
    }

    @Test
    fun `new lines starting with ANDAND are allowed`() {

        withSource("""

            val foo = "bar".isNotEmpty()
                && "bazar".isNotEmpty() // either
        """)

        build("ktlintMainCheck")
    }

    @Test
    fun `property accessors on new line`() {

        withSource("""

        val foo get() = "bar"


        val bar: String get() { return "bar" }
    """)

        buildAndFail("ktlintMainCheck")

        assertKtlintErrors(2)
        assertKtLintError("Property accessor must be on a new line", 3, 17)
        assertKtLintError("Property accessor must be on a new line", 6, 25)

        withSource("""

        val foo
            get() = "bar"


        val bar: String
            get() { return "bar" }
    """)

        build("ktlintMainCheck")
    }


    private
    fun withSource(text: String) =
        withFile("src/main/kotlin/source.kt", text)

    private
    val ktlintReportFile by lazy { existing("build/reports/ktlint/ktlint-main.txt") }

    private
    fun assertKtlintErrors(count: Int) =
        assertThat(
            "ktlint error count",
            ktlintReportFile.readLines().filter { it.contains("source.kt:") }.count(),
            equalTo(count))

    private
    fun assertKtLintError(error: String, line: Int, column: Int) =
        assertThat(
            ktlintReportFile.readText(),
            containsString("source.kt:$line:$column: $error"))
}
