/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.fixtures

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.kotlin.dsl.resolver.GradleInstallation
import org.gradle.kotlin.dsl.support.zipTo
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import java.io.File
import java.util.Properties


/**
 * Base class for Kotlin DSL integration tests.
 *
 * You must add the following dependencies to the project for this to work:
 * ```
 * integTestRuntimeOnly(project(":kotlin-dsl-plugins")) {
 *     isTransitive = false
 * }
 * integTestLocalRepository(project(":kotlin-dsl-plugins"))
 * ```
 */
abstract class AbstractKotlinIntegrationTest : AbstractIntegrationTest() {

    protected
    open val injectLocalKotlinDslPluginsRepositories = true

    @Before
    fun injectLocallyBuiltKotlinDslPluginsRepositories() {
        if (!injectLocalKotlinDslPluginsRepositories) return
        val setupScript = file(".integTest/inject-local-plugins-repos.init.gradle")
        setupScript.parentFile.mkdirs()
        setupScript.writeText(
            """
            beforeSettings { settings ->
                settings.pluginManagement {
                    repositories {
                        $testRepositories
                        gradlePluginPortal()
                    }
                    resolutionStrategy {
                        eachPlugin {
                            $futurePluginRules
                        }
                    }
                }
            }
            """
        )
        executer.beforeExecute {
            usingInitScript(setupScript)
        }
    }

    @Before
    fun setUpDefaultSettings() {
        withDefaultSettings()
    }

    private
    val testRepositories: String
        get() = testRepositoryPaths.joinLines {
            """
                maven { url = uri("$it") }
            """
        }

    private
    val futurePluginRules: String
        get() = futurePluginVersions.entries.joinLines { (id, version) ->
            """
                if (requested.id.id == "$id") {
                    useVersion("$version")
                }
            """
        }

    protected
    val futurePluginVersions by lazy {
        loadPropertiesFromResource("/future-plugin-versions.properties")
            ?: throw IllegalStateException("/future-plugin-versions.properties resource not found.")
    }

    private
    fun loadPropertiesFromResource(name: String): Properties? =
        javaClass.getResourceAsStream(name)?.use {
            Properties().apply { load(it) }
        }

    private
    val testRepositoryPaths: List<String>
        get() = IntegrationTestBuildContext().localRepository?.let { listOf(it.normalisedPath) } ?: emptyList()

    @Before
    fun useRepositoryMirrors() {
        executer.withRepositoryMirrors()
    }

    protected
    open val defaultSettingsScript
        get() = ""

    protected
    val repositoriesBlock
        get() = """
            repositories {
                gradlePluginPortal()
            }
        """

    protected
    val projectRoot: File
        get() = customProjectRoot ?: testDirectory

    private
    var customProjectRoot: File? = null

    protected
    fun <T> withProjectRoot(dir: File, action: () -> T): T {
        val previousProjectRoot = customProjectRoot
        try {
            customProjectRoot = dir
            return action()
        } finally {
            customProjectRoot = previousProjectRoot
        }
    }

    protected
    fun withFolders(folders: FoldersDslExpression) =
        projectRoot.withFolders(folders)

    protected
    fun withDefaultSettings() =
        withDefaultSettingsIn(".")

    protected
    fun withDefaultSettingsIn(baseDir: String) =
        withSettingsIn(baseDir, defaultSettingsScript)

    protected
    fun withSettings(script: String, produceFile: (String) -> File = ::newFile): File =
        withSettingsIn(".", script, produceFile)

    protected
    fun withSettingsIn(baseDir: String, script: String, produceFile: (String) -> File = ::newFile): File =
        withFile("$baseDir/settings.gradle.kts", script, produceFile)

    protected
    fun withBuildScript(script: String, produceFile: (String) -> File = ::newFile): File =
        withBuildScriptIn(".", script, produceFile)

    protected
    fun withBuildScriptIn(baseDir: String, script: String, produceFile: (String) -> File = ::newFile): File =
        withFile("$baseDir/build.gradle.kts", script, produceFile)

    protected
    fun withFile(fileName: String, text: String = "", produceFile: (String) -> File = ::newFile) =
        writeFile(produceFile(fileName), text)

    private
    fun writeFile(file: File, text: String): File =
        file.apply { writeText(text) }

    protected
    fun withBuildSrc() =
        withFile(
            "buildSrc/src/main/groovy/build/Foo.groovy",
            """
            package build
            class Foo {}
            """
        )

    protected
    fun withKotlinBuildSrc() {
        withDefaultSettingsIn("buildSrc")
        withBuildScriptIn(
            "buildSrc",
            """
            plugins {
                `kotlin-dsl`
            }

            $repositoriesBlock
            """
        )
    }

    protected
    fun givenPrecompiledKotlinScript(fileName: String, code: String): ExecutionResult {
        withKotlinDslPlugin()
        withPrecompiledKotlinScript(fileName, code)
        return compileKotlin()
    }

    protected
    fun withPrecompiledKotlinScript(fileName: String, code: String) =
        withFile("src/main/kotlin/$fileName", code)

    protected
    fun withKotlinDslPlugin() =
        withKotlinDslPluginIn(".")

    protected
    fun withKotlinDslPluginIn(baseDir: String) =
        withBuildScriptIn(baseDir, scriptWithKotlinDslPlugin())

    protected
    fun scriptWithKotlinDslPlugin(version: String? = null): String =
        """
            plugins {
                `kotlin-dsl`${if (version == null) "" else " version \"$version\""}
            }

            $repositoriesBlock
        """

    private
    fun testGradleInstallation() =
        GradleInstallation.Local(distribution.gradleHomeDir)

    protected
    fun compileKotlin(taskName: String = "classes"): ExecutionResult =
        build(taskName).assertTaskExecuted(":compileKotlin")

    protected
    fun withClassJar(fileName: String, vararg classes: Class<*>) =
        withZip(fileName, classEntriesFor(*classes))

    protected
    fun withZip(fileName: String, entries: Sequence<Pair<String, ByteArray>>): File =
        newFile(fileName).also {
            zipTo(it, entries)
        }

    protected
    fun newFile(fileName: String): File {
        return canonicalFile(fileName).apply {
            parentFile.mkdirs()
            createNewFile()
        }
    }

    protected
    fun newDir(relativePath: String): File =
        existing(relativePath).apply { assert(mkdirs()) }

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
        canonicalFile(relativePath)

    private
    fun canonicalFile(relativePath: String) =
        File(projectRoot, relativePath).canonicalFile

    fun build(vararg arguments: String): ExecutionResult =
        gradleExecuterFor(arguments).run()

    protected
    fun buildFailureOutput(vararg arguments: String): String =
        buildAndFail(*arguments).error

    protected
    fun buildAndFail(vararg arguments: String): ExecutionFailure =
        gradleExecuterFor(arguments).runWithFailure()

    protected
    fun build(rootDir: File, vararg arguments: String): ExecutionResult =
        gradleExecuterFor(arguments, rootDir).run()

    protected
    fun gradleExecuterFor(arguments: Array<out String>, rootDir: File = projectRoot) =
        inDirectory(rootDir).withArguments(*arguments)

    protected
    fun assumeJavaLessThan9() {
        assumeTrue("Test disabled under JDK 9 and higher", JavaVersion.current() < JavaVersion.VERSION_1_9)
    }

    protected
    fun assumeJavaLessThan11() {
        assumeTrue("Test disabled under JDK 11 and higher", JavaVersion.current() < JavaVersion.VERSION_11)
    }

    protected
    fun assumeJavaLessThan17() {
        assumeTrue("Test disabled under JDK 17 and higher", JavaVersion.current() < JavaVersion.VERSION_17)
    }

    protected
    fun assumeJava11OrHigher() {
        assumeTrue("Test requires Java 11 or higher", JavaVersion.current().isJava11Compatible)
    }

    protected
    fun assumeNonEmbeddedGradleExecuter() {
        assumeFalse(GradleContextualExecuter.isEmbedded())
    }
}
