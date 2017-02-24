package org.gradle.script.lang.kotlin.integration

import org.gradle.script.lang.kotlin.support.classEntriesFor
import org.gradle.script.lang.kotlin.support.zipTo

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

import org.junit.Rule
import org.junit.rules.TemporaryFolder

import java.io.File

open class AbstractIntegrationTest {

    @JvmField
    @Rule val projectDir = TemporaryFolder()

    protected val projectRoot: File
        get() = projectDir.root

    protected fun withBuildScript(script: String, produceFile: (String) -> File = this::newFile): File =
        withBuildScriptIn(".", script, produceFile)

    protected fun withBuildScriptIn(baseDir: String, script: String, produceFile: (String) -> File = this::newFile): File {
        withFile("$baseDir/settings.gradle", "rootProject.buildFileName = 'build.gradle.kts'", produceFile)
        return withFile("$baseDir/build.gradle.kts", script, produceFile)
    }

    protected fun withFile(fileName: String, text: String = "", produceFile: (String) -> File = this::newFile) =
        writeFile(produceFile(fileName), text)

    protected fun writeFile(file: File, text: String): File =
        file.apply { writeText(text) }

    protected fun withBuildSrc() {
        withFile("buildSrc/src/main/groovy/build/Foo.groovy", """
            package build
            class Foo {}
        """)
    }

    protected fun withClassJar(fileName: String, vararg classes: Class<*>) =
        newFile(fileName).apply {
            zipTo(this, classEntriesFor(*classes))
        }

    protected fun newFile(fileName: String): File {
        makeParentFoldersOf(fileName)
        return projectDir.newFile(fileName)
    }

    protected fun newOrExisting(fileName: String) =
        existing(fileName).let {
            when {
                it.isFile -> it
                else -> newFile(fileName)
            }
        }

    protected fun existing(relativePath: String): File =
        File(projectRoot, relativePath).run {
            canonicalFile
        }

    protected fun makeParentFoldersOf(fileName: String) {
        parentFileOf(fileName).mkdirs()
    }

    protected fun parentFileOf(fileName: String): File =
        File(projectRoot, fileName).parentFile

    protected fun build(vararg arguments: String): BuildResult =
        gradleRunner()
            .withArguments(*arguments, "--stacktrace")
            .build()

    protected fun buildAndFail(): BuildResult =
        gradleRunner()
            .withArguments("--stacktrace")
            .buildAndFail()

    protected fun buildFailureOutput(): String =
        buildAndFail().output

    private fun gradleRunner() =
        gradleRunnerFor(projectRoot)
}


fun gradleRunnerFor(projectDir: File): GradleRunner =
    GradleRunner
        .create()
        .withDebug(false)
        .withGradleInstallation(customInstallation())
        .withProjectDir(projectDir)


fun customInstallation() =
    File("build/custom").listFiles()?.let {
        it.singleOrNull { it.name.startsWith("gradle") } ?:
            throw IllegalStateException(
                "Expected 1 custom installation but found ${it.size}. Run `./gradlew clean customInstallation`.")
    } ?: throw IllegalStateException("Custom installation not found. Run `./gradlew customInstallation`.")


inline fun <T> withDaemonRegistry(registryBase: File, block: () -> T) =
    withSystemProperty("org.gradle.daemon.registry.base", registryBase.absolutePath, block)


inline fun <T> withSystemProperty(key: String, value: String, block: () -> T): T {
    val originalValue = System.getProperty(key)
    try {
        System.setProperty(key, value)
        return block()
    } finally {
        setOrClearProperty(key, originalValue)
    }
}


fun setOrClearProperty(key: String, value: String?) {
    when (value) {
        null -> System.clearProperty(key)
        else -> System.setProperty(key, value)
    }
}
