package org.gradle.script.lang.kotlin.integration

import org.gradle.testkit.runner.GradleRunner

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import java.io.File

class GradleScriptKotlinIntegrationTest {

    @JvmField
    @Rule val projectDir = TemporaryFolder()

    @Test
    fun `given a script with SAM conversions, it can run it`() {
        withBuildScript("""
            import org.gradle.script.lang.kotlin.*
            import org.gradle.api.tasks.bundling.Zip
            import java.util.concurrent.Callable

            configurations.create("compile")

            dependencies { "compile"(gradleApi()) }

            task<Zip>("repackage") {
                baseName = "gradle-api"
                from(Callable {
                    val files = configurations.getByName("compile").files
                    zipTree(files.single { it.name.startsWith(baseName) })
                })
            }""")
        assert(
            gradleRunner()
                .withArguments("tasks")
                .build().output.contains("repackage"))
    }

    fun withBuildScript(script: String) {
        writeFile("settings.gradle", "rootProject.buildFileName = 'build.gradle.kts'")
        writeFile("build.gradle.kts", script)
    }

    private fun writeFile(fileName: String, text: String) {
        projectDir.newFile(fileName).writeText(text)
    }

    private fun gradleRunner(debug: Boolean = false) =
        GradleRunner
            .create()
            .withDebug(debug)
            .withGradleInstallation(customInstallation())
            .withProjectDir(projectDir.root)

    private fun customInstallation() =
        File("build/custom").listFiles()?.single()
            ?: throw IllegalStateException("Custom installation not found. Run `./gradlew customInstallation`.")
}
