/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.binarycompatibility

import org.gradle.kotlin.dsl.*
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.hamcrest.Matcher
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files


abstract class AbstractBinaryCompatibilityTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    private
    val rootDir: File
        get() = tmpDir.root

    internal
    fun checkBinaryCompatibleKotlin(v1: String = "", v2: String, block: CheckResult.() -> Unit = {}): CheckResult =
        runKotlinBinaryCompatibilityCheck(v1, v2) {
            assertBinaryCompatible()
            block()
        }

    internal
    fun checkNotBinaryCompatibleKotlin(v1: String = "", v2: String, block: CheckResult.() -> Unit = {}): CheckResult =
        runKotlinBinaryCompatibilityCheck(v1, v2) {
            assertNotBinaryCompatible()
            block()
        }

    internal
    fun checkBinaryCompatibleJava(v1: String = "", v2: String, block: CheckResult.() -> Unit = {}): CheckResult =
        runJavaBinaryCompatibilityCheck(v1, v2) {
            assertBinaryCompatible()
            block()
        }

    internal
    fun checkNotBinaryCompatibleJava(v1: String = "", v2: String, block: CheckResult.() -> Unit = {}): CheckResult =
        runJavaBinaryCompatibilityCheck(v1, v2) {
            assertNotBinaryCompatible()
            block()
        }

    internal
    fun checkBinaryCompatible(v1: File.() -> Unit = {}, v2: File.() -> Unit = {}, block: CheckResult.() -> Unit = {}): CheckResult =
        runBinaryCompatibilityCheck(v1, v2) {
            assertBinaryCompatible()
            block()
        }

    internal
    fun checkNotBinaryCompatible(v1: File.() -> Unit = {}, v2: File.() -> Unit = {}, block: CheckResult.() -> Unit = {}): CheckResult =
        runBinaryCompatibilityCheck(v1, v2) {
            assertNotBinaryCompatible()
            block()
        }

    internal
    fun checkBinaryCompatibleFailsWithoutReport(v1: File.() -> Unit = {}, v2: File.() -> Unit = {}, block: BuildResult.() -> Unit = {}): BuildResult =
        runBinaryCompatibilityCheckWithFailure(v1, v2) {
            block()
        }

    private
    fun CheckResult.assertBinaryCompatible() {
        assertTrue(richReport.toAssertionMessage("Expected to be compatible but the check failed"), isBinaryCompatible)
    }

    private
    fun CheckResult.assertNotBinaryCompatible() {
        assertFalse(richReport.toAssertionMessage("Expected to be breaking but the check passed"), isBinaryCompatible)
    }

    private
    fun RichReport.toAssertionMessage(message: String) =
        if (isEmpty) "$message with an empty report"
        else "$message\n${toText().prependIndent("    ")}"

    private
    fun runKotlinBinaryCompatibilityCheck(v1: String, v2: String, block: CheckResult.() -> Unit = {}): CheckResult =
        runBinaryCompatibilityCheck(
            v1 = {
                withFile(
                    "kotlin/com/example/Source.kt",
                    """
                    package com.example

                    import org.gradle.api.Incubating

                    $v1
                    """
                )
            },
            v2 = {
                withFile(
                    "kotlin/com/example/Source.kt",
                    """
                    package com.example

                    import org.gradle.api.Incubating

                    $v2
                    """
                )
            },
            block = block
        )

    private
    fun runJavaBinaryCompatibilityCheck(v1: String, v2: String, block: CheckResult.() -> Unit = {}): CheckResult =
        runBinaryCompatibilityCheck(
            v1 = {
                withFile(
                    "java/com/example/Source.java",
                    """
                    package com.example;

                    import org.gradle.api.Incubating;

                    $v1
                    """
                )
            },
            v2 = {
                withFile(
                    "java/com/example/Source.java",
                    """
                    package com.example;

                    import org.gradle.api.Incubating;

                    $v2
                    """
                )
            },
            block = block
        )

    /**
     * Runs the binary compatibility check against two source trees.
     *
     * The fixture build supports both Java and Kotlin sources.
     *
     * @param v1 sources producer for V1, receiver is the `src/main` directory
     * @param v2 sources producer for V2, receiver is the `src/main` directory
     * @param block convenience block invoked on the result
     * @return the check result
     */
    private
    fun runBinaryCompatibilityCheck(v1: File.() -> Unit, v2: File.() -> Unit, block: CheckResult.() -> Unit = {}): CheckResult {
        rootDir.withFile("version.txt", "1.0")

        val inputBuildDir = setupRunBinaryCompatibility(v1, v2)

        val runner = GradleRunner.create()
            .withProjectDir(inputBuildDir)
            .withPluginClasspath()
            .withArguments(":binary-compatibility:checkBinaryCompatibility", "-s")

        val (buildResult, failure) = try {
            runner.build()!! to null
        } catch (ex: UnexpectedBuildFailure) {
            ex.buildResult!! to ex
        }

        println(buildResult.output)

        val richReportFile = inputBuildDir.resolve("binary-compatibility/build/japi/japi.html").apply {
            assertTrue("Rich report file doesn't exist", isFile)
        }

        return CheckResult(failure, scrapeRichReport(richReportFile), buildResult).apply {
            println(richReport.toText())
            block()
        }
    }

    /**
     * Runs the binary compatibility check against two source trees, but fails without generating a rich report.
     *
     * The fixture build supports both Java and Kotlin sources.
     *
     * @param v1 sources producer for V1, receiver is the `src/main` directory
     * @param v2 sources producer for V2, receiver is the `src/main` directory
     * @param block convenience block invoked on the result
     * @return the check result
     */
    private
    fun runBinaryCompatibilityCheckWithFailure(v1: File.() -> Unit, v2: File.() -> Unit, block: BuildResult.() -> Unit = {}): BuildResult {
        rootDir.withFile("version.txt", "1.0")

        val inputBuildDir = setupRunBinaryCompatibility(v1, v2)

        val runner = GradleRunner.create()
            .withProjectDir(inputBuildDir)
            .withPluginClasspath()
            .withArguments(":binary-compatibility:checkBinaryCompatibility", "-s")

        val (buildResult, failure) = try {
            runner.build()!! to null
        } catch (ex: UnexpectedBuildFailure) {
            ex.buildResult!! to ex
        }

        println(buildResult.output)

        inputBuildDir.resolve("binary-compatibility/build/japi/japi.html").apply {
            assertFalse("Rich report file exists", isFile)
        }

        return buildResult.apply {
            assertTrue("Build result is not a failure", failure != null)
            println(failure?.message)
            block()
        }
    }

    private
    fun setupRunBinaryCompatibility(v1: File.() -> Unit, v2: File.() -> Unit): File {
        rootDir.withFile("version.txt", "1.0")

        return rootDir.withUniqueDirectory("input-build").apply {

            withSettings("""include("v1", "v2", "binary-compatibility")""")
            withBuildScript(
                """
                    import gradlebuild.identity.extension.ModuleIdentityExtension

                    plugins {
                        base
                        kotlin("jvm") version "$embeddedKotlinVersion" apply false
                    }
                    subprojects {
                        apply(plugin = "gradlebuild.module-identity")
                        apply(plugin = "kotlin")
                        the<ModuleIdentityExtension>().baseName.set("api-module")
                        repositories {
                            mavenCentral()
                        }
                        dependencies {
                            "implementation"(gradleApi())
                            "implementation"(kotlin("stdlib"))
                            // TODO remove once JSpecify is part of the Gradle API
                            "implementation"("org.jspecify:jspecify:1.0.0")
                        }
                    }
                    project(":v1") {
                        version = "1.0"
                    }
                    project(":v2") {
                        version = "2.0"
                    }
                """
            )
            withDirectory("v1/src/main").v1()
            withDirectory("v2/src/main").v2()
            val sourceRoots = if (File(withDirectory("v2/src/main"), "java").exists()) {
                "v2/src/main/java"
            } else {
                "v2/src/main/kotlin"
            }
            withDirectory("binary-compatibility").apply {
                withBuildScript(
                    """
                    import japicmp.model.JApiChangeStatus
                    import gradlebuild.binarycompatibility.*
                    import gradlebuild.binarycompatibility.filters.*

                    val v1 = rootProject.project(":v1")
                    val v1Jar = v1.tasks.named("jar")
                    val v2 = rootProject.project(":v2")
                    val v2Jar = v2.tasks.named("jar")
                    val newUpgradedPropertiesFile = layout.buildDirectory.file("gradle-api-info/new-upgraded-properties.json")
                    val oldUpgradedPropertiesFile = layout.buildDirectory.file("gradle-api-info/old-upgraded-properties.json")
                    val extractGradleApiInfo = tasks.register<ExtractGradleApiInfoTask>("extractGradleApiInfo") {
                        gradleApiInfoJarPrefix = "v"
                        currentDistributionJars = files(v2Jar)
                        baselineDistributionJars = files(v1Jar)
                        currentUpgradedProperties = newUpgradedPropertiesFile
                        baselineUpgradedProperties = oldUpgradedPropertiesFile
                    }

                    tasks.register<JapicmpTask>("checkBinaryCompatibility") {

                        dependsOn(":v1:jar", ":v2:jar")
                        inputs.files(extractGradleApiInfo)

                        oldArchives.from(v1Jar)
                        oldClasspath.from(v1.configurations.named("runtimeClasspath"), v1Jar)

                        newArchives.from(v2Jar)
                        newClasspath.from(v2.configurations.named("runtimeClasspath"), v2Jar)

                        onlyModified.set(false)
                        failOnModification.set(false) // we rely on the rich report to fail

                        txtOutputFile.set(file("build/japi-report.txt"))

                        richReport {

                            title.set("Gradle Binary Compatibility Check")
                            destinationDir.set(file("build/japi"))
                            reportName.set("japi.html")

                            includedClasses.set(listOf(".*"))
                            excludedClasses.set(emptyList())

                        }

                        BinaryCompatibilityHelper.setupJApiCmpRichReportRules(
                            this,
                            AcceptedApiChanges.parse(listOf("{acceptedApiChanges:[]}")),
                            rootProject.files("$sourceRoots"),
                            "2.0",
                            file("test-api-changes.json"),
                            rootProject.layout.projectDirectory,
                            newUpgradedPropertiesFile.get().asFile,
                            oldUpgradedPropertiesFile.get().asFile
                        )
                    }
                    """
                )
            }
        }
    }

    internal
    data class CheckResult(
        val checkFailure: UnexpectedBuildFailure?,
        val richReport: RichReport,
        val buildResult: BuildResult
    ) {

        val isBinaryCompatible = checkFailure == null

        fun assertEmptyReport() {
            assertHasNoError()
            assertHasNoWarning()
            assertHasNoInformation()
        }

        fun assertHasNoError() {
            assertTrue("Has no error (${richReport.errors})", richReport.errors.isEmpty())
        }

        fun assertHasNoWarning() {
            assertTrue("Has no warning (${richReport.warnings})", richReport.warnings.isEmpty())
        }

        fun assertHasNoInformation() {
            assertTrue("Has no information (${richReport.information})", richReport.information.isEmpty())
        }

        fun assertHasErrors(vararg errors: String) {
            assertThat("Has errors", richReport.errors.map { it.message }, inAnyOrder(errors))
        }

        fun assertHasWarnings(vararg warnings: String) {
            assertThat("Has warnings", richReport.warnings.map { it.message }, inAnyOrder(warnings))
        }

        fun assertHasInformation(vararg information: String) {
            assertThat("Has information", richReport.information.map { it.message }, inAnyOrder(information))
        }

        fun assertHasAccepted(vararg accepted: String) {
            assertThat("Has accepted", richReport.accepted.map { it.message }, inAnyOrder(accepted))
        }

        fun assertHasAccepted(vararg accepted: Pair<String, List<String>>) {
            assertThat("Has accepted", richReport.accepted, inAnyOrder(accepted.map { ReportMessage(it.first, it.second) }))
        }

        fun assertHasErrors(vararg errors: List<String>) {
            assertHasErrors(*errors.toList().flatten().toTypedArray())
        }

        fun assertHasErrors(vararg errorWithDetail: Pair<String, List<String>>) {
            assertThat("Has errors", richReport.errors, inAnyOrder(errorWithDetail.map { ReportMessage(it.first, it.second) }))
        }

        private
        inline fun <reified T> inAnyOrder(items: List<T>): Matcher<Iterable<T>> = inAnyOrder(items.toTypedArray())

        /**
         * Matcher checking each item is present exactly once in a given iterable, but an any position,
         * and that there are no unexpected items.
         */
        private
        fun <T> inAnyOrder(items: Array<out T>): Matcher<Iterable<T>> = Matchers.containsInAnyOrder(*items)

        fun newApi(thing: String, desc: String): String =
            "$thing ${describe(thing, desc)}: New public API in 2.0 (@Incubating)"

        fun added(thing: String, desc: String): List<String> =
            listOf(
                "$thing ${describe(thing, desc)}: Is not annotated with @Incubating.",
                "$thing ${describe(thing, desc)}: Is not annotated with @since 2.0."
            )

        fun removed(thing: String, desc: String): Pair<String, List<String>> =
            "$thing ${describe(thing, desc)}: Is not binary compatible." to listOf("$thing has been removed")

        private
        fun describe(thing: String, desc: String) =
            if (thing == "Field") desc else "com.example.$desc"
    }

    fun BuildResult.assertOutputContains(text: String) =
        assertTrue("Output should contain text:\n'$text',\nbut given text was not matched.\n", output.contains(text))

    protected
    fun File.withFile(path: String, text: String = ""): File =
        resolve(path).apply {
            parentFile.mkdirs()
            writeText(text.trimIndent())
        }

    private
    fun File.withUniqueDirectory(prefixPath: String): File =
        Files.createTempDirectory(
            withDirectory(prefixPath.substringBeforeLast("/")).toPath(),
            prefixPath.substringAfterLast("/")
        ).toFile()

    private
    fun File.withDirectory(path: String): File =
        resolve(path).apply {
            mkdirs()
        }

    private
    fun File.withSettings(text: String = ""): File =
        withFile("settings.gradle.kts", text)

    private
    fun File.withBuildScript(text: String = ""): File =
        withFile("build.gradle.kts", text)
}
