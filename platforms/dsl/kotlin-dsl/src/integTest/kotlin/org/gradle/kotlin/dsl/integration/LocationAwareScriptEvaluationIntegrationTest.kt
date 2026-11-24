package org.gradle.kotlin.dsl.integration

import org.gradle.kotlin.dsl.fixtures.AbstractKotlinIntegrationTest
import org.gradle.kotlin.dsl.fixtures.containsMultiLineString

import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test


class LocationAwareScriptEvaluationIntegrationTest : AbstractKotlinIntegrationTest() {

    private
    val boom = """throw InternalError("BOOM!")"""

    @Test
    fun `location of exception thrown from build script is reported`() {

        withSettings("""include("a")""")
        val script = withBuildScriptIn("a", boom)

        assertFailingBuildOutputOf("help") {
            """
            * Where:
            Build file '${script.canonicalPath}' line: 1
            """
        }
    }

    @Test
    fun `location of exception thrown from applied script is reported`() {

        withBuildScript("""apply(from = "other.gradle.kts")""")
        val script = withFile("other.gradle.kts", boom)

        assertFailingBuildOutputOf("help") {
            """
            * Where:
            Script '${script.canonicalPath}' line: 1
            """
        }
    }

    @Test
    fun `location of exception thrown from applied script with same filename is reported`() {

        withBuildScript("""apply(from = "other/build.gradle.kts")""")
        val script = withFile("other/build.gradle.kts", boom)

        assertFailingBuildOutputOf("help") {
            """
            * Where:
            Script '${script.canonicalPath}' line: 1
            """
        }
    }

    @Test
    fun `location of exception thrown from buildscript block is reported`() {

        val script = withBuildScript("buildscript { $boom }")

        assertFailingBuildOutputOf("help") {
            """
            * Where:
            Build file '${script.canonicalPath}' line: 1
            """
        }
    }

    @Test
    fun `location of exception thrown from plugins block is reported`() {

        val script = withBuildScript("plugins { $boom }")

        assertFailingBuildOutputOf("help") {
            """
            * Where:
            Build file '${script.canonicalPath}' line: 1
            """
        }
    }

    @Test
    fun `location of exception thrown from settings script is reported`() {

        val script = withSettings(boom)

        assertFailingBuildOutputOf("help") {
            """
            * Where:
            Settings file '${script.canonicalPath}' line: 1
            """
        }
    }

    @Test
    fun `location of exception thrown from initialization script is reported`() {

        val script = withFile("my.init.gradle.kts", boom)

        assertFailingBuildOutputOf("help", "-I", script.absolutePath) {
            """
            * Where:
            Initialization script '${script.canonicalPath}' line: 1
            """
        }
    }

    @Test
    fun `location of missing script application is reported`() {

        withBuildScript("""apply(from = "present.gradle.kts")""")
        val present = withFile("present.gradle.kts", """apply(from = "absent.gradle.kts")""")

        assertFailingBuildOutputOf("help") {
            """
            * Where:
            Script '${present.canonicalPath}' line: 1

            * What went wrong:
            Could not read script '${existing("absent.gradle.kts").canonicalPath}' as it does not exist.
            """
        }
    }

    @Test
    fun `location of exception thrown by kotlin script applied from groovy script is reported`() {

        withFile("build.gradle", "apply from: 'other.gradle.kts'")
        val script = withFile(
            "other.gradle.kts",
            """
            println("In Kotlin Script")
            $boom
            """
        )

        assertFailingBuildOutputOf("help") {
            """
            * Where:
            Script '${script.canonicalPath}' line: 3
            """
        }
    }

    /**
     * This is a caveat.
     * The Groovy DSL provider relies on exceptions being analyzed up the stack.
     * See [org.gradle.initialization.DefaultExceptionAnalyser.transform], note the comments.
     * The Kotlin DSL provider handles this in isolation,
     * thus hiding the location of exceptions thrown by groovy scripts applied from kotlin scripts.
     *
     * This test exercises the current behavior.
     */
    @Test
    fun `location of exception thrown by groovy script applied from kotlin script shadowed by the kotlin location`() {

        val kotlinScript = withBuildScript("""apply(from = "other.gradle")""")
        withFile(
            "other.gradle",
            """
            println("In Groovy Script")
            throw new InternalError("BOOM!")
            """
        )

        assertFailingBuildOutputOf("help") {
            """
            * Where:
            Build file '${kotlinScript.canonicalPath}' line: 1
            """
        }
    }

    private
    fun assertFailingBuildOutputOf(vararg arguments: String, string: () -> String) =
        assertThat(buildAndFail(*arguments).error, containsMultiLineString(string()))
}
