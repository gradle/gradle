package org.gradle.script.lang.kotlin.fixtures

import org.gradle.internal.FileUtils.toSafeFileName
import org.gradle.script.lang.kotlin.support.zipTo
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import java.io.File


internal
val isCI by lazy { !System.getenv("CI").isNullOrEmpty() }


open class AbstractIntegrationTest {

    @JvmField
    @Rule val testName = TestName()

    @JvmField
    @Rule val temporaryFolder = TemporaryFolder()

    protected
    val projectRoot: File
        get() = File(temporaryFolder.root, toSafeFileName(testName.methodName)).apply { mkdirs() }

    protected
    fun withBuildScript(script: String, produceFile: (String) -> File = this::newFile): File =
        withBuildScriptIn(".", script, produceFile)

    protected
    fun withBuildScriptIn(baseDir: String, script: String, produceFile: (String) -> File = this::newFile): File =
        withFile("$baseDir/build.gradle.kts", script, produceFile)

    protected
    fun withFile(fileName: String, text: String = "", produceFile: (String) -> File = this::newFile) =
        writeFile(produceFile(fileName), text)

    protected
    fun writeFile(file: File, text: String): File =
        file.apply { writeText(text) }

    protected
    fun withBuildSrc() =
        withFile("buildSrc/src/main/groovy/build/Foo.groovy", """
            package build
            class Foo {}
        """)

    protected
    fun withKotlinBuildSrc() =
        withBuildScriptIn("buildSrc", """
            buildscript {
                configure(listOf(repositories, project.repositories)) {
                    gradleScriptKotlin()
                }
                dependencies {
                    classpath(kotlin("gradle-plugin"))
                }
            }
            apply {
                plugin("kotlin")
            }
            dependencies {
                compile(gradleScriptKotlinApi())
            }
        """)

    protected
    fun withClassJar(fileName: String, vararg classes: Class<*>) =
        newFile(fileName).apply {
            zipTo(this, classEntriesFor(*classes))
        }

    protected
    fun newFile(fileName: String): File {
        makeParentFoldersOf(fileName)
        return File(projectRoot, fileName).canonicalFile.apply { createNewFile() }
    }

    protected
    fun newOrExisting(fileName: String) =
        existing(fileName).let {
            when {
                it.isFile -> it
                else -> newFile(fileName)
            }
        }

    protected
    fun existing(relativePath: String): File =
        File(projectRoot, relativePath).run {
            canonicalFile
        }

    protected
    fun makeParentFoldersOf(fileName: String) =
        parentFileOf(fileName).mkdirs()

    protected
    fun parentFileOf(fileName: String): File =
        File(projectRoot, fileName).parentFile

    protected
    fun build(vararg arguments: String): BuildResult =
        gradleRunnerForArguments(arguments)
            .build()

    protected
    fun buildFailureOutput(vararg arguments: String): String =
        buildAndFail(*arguments).output

    protected
    fun buildAndFail(vararg arguments: String): BuildResult =
        gradleRunnerForArguments(arguments)
            .buildAndFail()

    private
    fun gradleRunnerForArguments(arguments: Array<out String>) =
        gradleRunner()
            .withArguments(*arguments, "--stacktrace")

    private
    fun gradleRunner() =
        gradleRunnerFor(projectRoot)
}


fun gradleRunnerFor(projectDir: File): GradleRunner = GradleRunner.create().run {
    withGradleInstallation(customInstallation())
    withProjectDir(projectDir)
    withDebug(false)
    if (isCI) withArguments("-Dkotlin-daemon.verbose=true")
    return this
}


fun customDaemonRegistry() =
    File(customInstallationBuildDir, "daemon-registry")


fun customInstallation() =
    customInstallationBuildDir.listFiles()?.let {
        it.singleOrNull { it.name.startsWith("gradle") } ?:
            throw IllegalStateException(
                "Expected 1 custom installation but found ${it.size}. Run `./gradlew clean customInstallation`.")
    } ?: throw IllegalStateException("Custom installation not found. Run `./gradlew customInstallation`.")


val rootProjectDir = File("..")


val customInstallationBuildDir = File(rootProjectDir, "build/custom")


inline
fun <T> withDaemonRegistry(registryBase: File, block: () -> T) =
    withSystemProperty("org.gradle.daemon.registry.base", registryBase.absolutePath, block)


inline
fun <T> withSystemProperty(key: String, value: String, block: () -> T): T {
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
