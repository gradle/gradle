/*
 * Copyright 2022 the original author or authors.
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
import gradlebuild.basics.BuildEnvironment
import gradlebuild.docs.FindBrokenInternalLinks
import gradlebuild.integrationtests.tasks.DistributionTest
import gradlebuild.performance.tasks.PerformanceTest
import gradlebuild.testcleanup.extension.TestFileCleanUpExtension
import me.champeau.gradle.japicmp.JapicmpTask
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dir
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.withType
import org.gradle.plugin.devel.tasks.ValidatePlugins

if (BuildEnvironment.isCiServer && project.name != "gradle-kotlin-dsl-accessors") {
    val testFileCleanUpExtension = project.extensions.getByType<TestFileCleanUpExtension>()
    testFileCleanUpExtension.testPathToBinaryResultsDirs = project.tasks.withType<Test>().associate { it.path to it.binaryResultsDirectory.get().asFile }

    val taskPathToReports = mutableMapOf<String, MutableSet<File>>()

    fun addReports(taskPath: String, reports: Iterable<File>) {
        taskPathToReports.getOrPut(taskPath) { hashSetOf() }.addAll(reports)
    }

    project.tasks.withType<ValidatePlugins>().forEach {
        addReports(it.path, it.validatePluginsReports())
    }

    project.tasks.withType<FindBrokenInternalLinks>().forEach {
        addReports(it.path, it.findBrokenInternalLinksReports())
    }
    project.tasks.withType<DistributionTest>().forEach {
        addReports(it.path, it.distributionReports())
    }
    project.tasks.withType<JapicmpTask>().forEach {
        addReports(it.path, it.japicmpReports())
    }
    project.tasks.withType<PerformanceTest>().forEach {
        addReports(it.path, it.performanceTestReports())
    }

    project.tasks.withType<Test>().forEach {
        addReports(it.path, it.traceJson())
        addReports(it.path, it.genericHtmlReports())
    }

    testFileCleanUpExtension.taskPathToReports.putAll(taskPathToReports.mapValues { it.value.toSet() })
}

fun ValidatePlugins.validatePluginsReports() = listOf(outputFile.get().asFile)

fun FindBrokenInternalLinks.findBrokenInternalLinksReports() = listOf(reportFile.get().asFile)

fun DistributionTest.distributionReports() = listOf(
    gradleInstallationForTest.gradleUserHomeDir.dir("test-kit-daemon").get().asFile,
    gradleInstallationForTest.gradleUserHomeDir.dir("kotlin-compiler-daemon").get().asFile,
    gradleInstallationForTest.daemonRegistry.get().asFile
)

fun JapicmpTask.japicmpReports() = listOf(richReport.get().destinationDir.get().asFile.resolve(richReport.get().reportName.get()))

fun PerformanceTest.performanceTestReports() = listOf(reportDir.parentFile)

// e.g. build/test-results/embeddedIntegTest/trace.json
fun Test.traceJson() = listOf(project.layout.buildDirectory.file("test-results/$name/trace.json").get().asFile)

fun Test.genericHtmlReports() = listOf(reports.html.outputLocation.get().asFile)

