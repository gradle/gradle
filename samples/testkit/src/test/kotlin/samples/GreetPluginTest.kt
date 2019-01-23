package samples

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

import org.hamcrest.CoreMatchers.hasItem

import org.junit.Assert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.io.File


class GreetPluginTest {

    @Test
    fun `greeting message can be configured`() {

        val message = "Hello, TestKit!"

        givenBuildScript("""
            plugins {
                id("greet")
            }

            greeting {
                message = "$message"
            }
        """)

        assertThat(
            build("greet", "-q").output.lines(),
            hasItem(message))
    }

    private
    fun build(vararg arguments: String): BuildResult =
        GradleRunner
            .create()
            .withProjectDir(temporaryFolder.root)
            .withPluginClasspath()
            .withArguments(*arguments)
//            .withDebug(true)
            .build()

    private
    fun givenBuildScript(script: String) =
        newFile("build.gradle.kts").apply {
            writeText(script)
        }

    private
    fun newFile(fileName: String): File =
        temporaryFolder.newFile(fileName)

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()
}
