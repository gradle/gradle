/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.binarycompatibility

import org.gradle.kotlin.dsl.embeddedKotlinVersion

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner

import org.hamcrest.CoreMatchers.equalTo

import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder

import java.io.File


abstract class AbstractKotlinBinaryCompatibilityTest {

    @get:Rule
    val tmpDir = TemporaryFolder()

    private
    val rootDir: File
        get() = tmpDir.root


    internal
    fun checkBinaryCompatible(v1: String = "", v2: String): CheckResult =
        checkBinaryCompatibility(true, v1, v2)

    internal
    fun checkNotBinaryCompatible(v1: String = "", v2: String): CheckResult =
        checkBinaryCompatibility(false, v1, v2)

    private
    fun checkBinaryCompatibility(compatible: Boolean, v1: String, v2: String): CheckResult {

        val inputBuildDir = rootDir.withDirectory("input-build").apply {

            withSettings("""include("v1", "v2", "binaryCompatibility")""")
            withBuildScript("""
                    plugins {
                        base
                        kotlin("jvm") version "$embeddedKotlinVersion" apply false
                    }
                    subprojects {
                        apply(plugin = "kotlin")
                        base.archivesBaseName = "api-module"
                        repositories {
                            jcenter()
                        }
                        dependencies {
                            "implementation"(gradleApi())
                            "implementation"(kotlin("stdlib"))
                        }
                    }
                    project(":v1") {
                        version = "1.0"
                    }
                    project(":v2") {
                        version = "2.0"
                    }
                """)
            withFile("v1/src/main/kotlin/com/example/Source.kt", """
                    package com.example

                    import org.gradle.api.Incubating

                    $v1
                """)
            withFile("v2/src/main/kotlin/com/example/Source.kt", """
                    package com.example

                    import org.gradle.api.Incubating

                    $v2
                """)
            withDirectory("binaryCompatibility").apply {
                withBuildScript("""
                    import japicmp.model.JApiChangeStatus
                    import me.champeau.gradle.japicmp.JapicmpTask
                    import org.gradle.binarycompatibility.*
                    import org.gradle.binarycompatibility.filters.*

                    tasks.register<JapicmpTask>("checkBinaryCompatibility") {

                        dependsOn(":v1:jar", ":v2:jar")

                        val v1 = rootProject.project(":v1")
                        val v1Jar = v1.tasks.named("jar")
                        val v2 = rootProject.project(":v2")
                        val v2Jar = v2.tasks.named("jar")

                        oldArchives = files(v1Jar)
                        oldClasspath = files(v1.configurations.named("runtimeClasspath"), v1Jar)

                        newArchives = files(v2Jar)
                        newClasspath = files(v2.configurations.named("runtimeClasspath"), v2Jar)

                        onlyModified = true
                        failOnModification = false // we rely on the rich report to fail

                        txtOutputFile = file("build/japi-report.txt")

                        richReport {

                            title = "Gradle Binary Compatibility Check"
                            destinationDir = file("build/japi")
                            reportName = "japi.html"

                            includedClasses = listOf(".*")
                            excludedClasses = emptyList()

                        }

                        BinaryCompatibilityHelper.setupJApiCmpRichReportRules(
                            this,
                            AcceptedApiChanges.parse("{acceptedApiChanges:[]}"),
                            setOf(rootProject.file("v2/src/main/kotlin")),
                            "2.0"
                        )
                    }
                """)
            }
        }

        val runner = GradleRunner.create()
            .withProjectDir(inputBuildDir)
            .withPluginClasspath()
            .withArguments(":binaryCompatibility:checkBinaryCompatibility", "-s")

        val richReportFile = inputBuildDir.resolve("binaryCompatibility/build/japi/japi.html")

        val result: BuildResult =
            if (compatible) runner.build()
            else runner.buildAndFail()

        println(result.output)

        assertTrue(
            "Rich report file exists",
            richReportFile.isFile
        )

        return CheckResult(compatible, scrapeRichReport(richReportFile), result).also {
            println(it.richReport.toText())
        }
    }

    internal
    data class CheckResult(
        val isBinaryCompatible: Boolean,
        val richReport: RichReport,
        val buildResult: BuildResult
    ) {

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
            assertThat("Has errors", richReport.errors.map { it.message }, equalTo(errors.toList()))
        }

        fun assertHasWarnings(vararg warnings: String) {
            assertThat("Has warnings", richReport.warnings.map { it.message }, equalTo(warnings.toList()))
        }

        fun assertHasInformation(vararg information: String) {
            assertThat("Has information", richReport.information.map { it.message }, equalTo(information.toList()))
        }

        fun assertHasErrors(vararg errors: List<String>) {
            assertHasErrors(*errors.toList().flatten().toTypedArray())
        }

        fun assertHasErrors(vararg errorWithDetail: Pair<String, List<String>>) {
            assertThat("Has errors", richReport.errors, equalTo(errorWithDetail.map { ReportMessage(it.first, it.second) }))
        }

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

    private
    fun File.withFile(path: String, text: String = ""): File =
        resolve(path).apply {
            parentFile.mkdirs()
            writeText(text.trimIndent())
        }
}
