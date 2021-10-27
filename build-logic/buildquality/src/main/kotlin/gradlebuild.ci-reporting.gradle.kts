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

import gradlebuild.basics.BuildEnvironment
import gradlebuild.classycle.tasks.Classycle
import gradlebuild.cleanup.tasks.KillLeakingJavaProcesses
import gradlebuild.docs.FindBrokenInternalLinks
import gradlebuild.integrationtests.tasks.DistributionTest
import gradlebuild.performance.tasks.PerformanceTest
import gradlebuild.testcleanup.extension.TestFileCleanUpExtension
import me.champeau.gradle.japicmp.JapicmpTask
import org.gradle.api.internal.tasks.testing.junit.result.TestResultSerializer
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * When run from a Continuous Integration environment, we only want to archive a subset of reports, mostly for
 * failing tasks only, to not use up unnecessary disk space on Team City. This also improves the performance of
 * artifact publishing by reducing the artifacts and packaging reports that consist of multiple files.
 *
 * Reducing the number of reports also makes it easier to find the important ones when analysing a failed build in
 * Team City.
 */

val testFilesCleanup = extensions.create<TestFileCleanUpExtension>("testFilesCleanup").apply {
    reportOnly.convention(false)
}

if (BuildEnvironment.isCiServer && project.name != "gradle-kotlin-dsl-accessors") {
    gradle.buildFinished {
        val failedTasks = failedTasks()
        val executedTasks = executedTasks()
        val tmpTestFiles = tmpTestFiles()
        prepareReportsForCiPublishing(if (tmpTestFiles.isEmpty()) failedTasks else executedTasks, executedTasks, tmpTestFiles.keys)
        cleanUp(tmpTestFiles.keys)
        if (!isCleanupRunnerStep(gradle!!)) {
            // Disable it before we fix https://github.com/gradle/gradle-private/issues/3463
            // verifyTestFilesCleanup(failedTasks, tmpTestFiles)
        }
    }
}


fun isCleanupRunnerStep(gradle: Gradle) =
    gradle.taskGraph.allTasks.any { it.state.executed && it is KillLeakingJavaProcesses }


/**
 * After archiving the test files, do a cleanup to get rid of TeamCity "XX published a lot of small artifacts" warning.
 */
fun cleanUp(filesToCleanUp: Collection<File>) {
    try {
        delete(filesToCleanUp)
    } catch (e: Exception) {
        // https://github.com/gradle/gradle-private/issues/2983#issuecomment-596083202
        e.printStackTrace()
    }
}

fun verifyTestFilesCleanup(failedTasks: List<Task>, tmpTestFiles: Map<File, List<String>>) {
    if (failedTasks.any { it is Test }) {
        println("Leftover files: $tmpTestFiles")
        return
    }

    if (tmpTestFiles.isNotEmpty()) {
        val nonEmptyDirs = tmpTestFiles.entries.joinToString("\n") { (dir, relativePaths) ->
            "${dir.absolutePath}:\n ${relativePaths.joinToString("\n ")}"
        }
        val errorMessage = "Found non-empty test files dir:\n$nonEmptyDirs"
        if (testFilesCleanup.reportOnly.get()) {
            println(errorMessage)
        } else {
            throw GradleException(errorMessage)
        }
    }
}

fun prepareReportsForCiPublishing(tasksToCollectReports: List<Task>, executedTasks: List<Task>, tmpTestFiles: Collection<File>) {
    val collectedTaskHtmlReports = tasksToCollectReports.flatMap { it.failedTaskGenericHtmlReports() }
    val attachedReports = executedTasks.flatMap { it.attachedReportLocations() }
    val executedTaskCustomReports = tasksToCollectReports.flatMap { it.customReports() }
    val testDistributionTraceJsons = executedTasks.filterIsInstance<Test>().flatMap { it.findTraceJson() }

    val allReports = collectedTaskHtmlReports + attachedReports + executedTaskCustomReports + tmpTestFiles + testDistributionTraceJsons
    allReports.forEach { report ->
        prepareReportForCiPublishing(report)
    }
}

fun Task.findTraceJson(): List<File> {
    // build/test-results/embeddedIntegTest/trace.json
    val traceJson = project.buildDir.resolve("test-results/$name/trace.json")
    return if (traceJson.isFile) {
        listOf(traceJson)
    } else {
        emptyList()
    }
}

/**
 * Returns non-empty directories: the mapping of directory to at most 4 leftover files' relative path in the directory.
 */
fun tmpTestFiles(): Map<File, List<String>> = layout.buildDirectory.dir("tmp/test files").get().asFile
    .listFiles()
    ?.associateWith { dir ->
        val dirPath = dir.toPath()
        Files.walk(dirPath).use { paths ->
            paths.filter { !it.toFile().isDirectory }
                .limit(4)
                .map { dirPath.relativize(it).toString() }
                .collect(Collectors.toList())
        }
    }?.filter {
        it.value.isNotEmpty()
    } ?: emptyMap()

fun executedTasks() = gradle.taskGraph.allTasks.filter { it.project == project && it.state.executed }

fun failedTasks() = gradle.taskGraph.allTasks.filter { it.project == project && (it.state.failure != null || it.containsFailedTest()) }

// We count the test task containing flaky result as failed
fun Task.containsFailedTest(): Boolean {
    if (this !is Test) {
        return false
    }

    var containingFailures = false

    val serializer = TestResultSerializer(binaryResultsDirectory.get().asFile)
    if (serializer.isHasResults) {
        serializer.read {
            if (failuresCount > 0) {
                containingFailures = true
            }
        }
    }
    return containingFailures
}

fun Task.failedTaskGenericHtmlReports() = when (this) {
    is Reporting<*> -> listOf(this.reports["html"].outputLocation.get().asFile)
    else -> emptyList()
}

fun Task.customReports() = when (this) {
    is ValidatePlugins -> listOf(outputFile.get().asFile)
    is Classycle -> listOf(reportFile.get().asFile)
    is FindBrokenInternalLinks -> listOf(reportFile.get().asFile)
    is DistributionTest -> listOf(
        gradleInstallationForTest.gradleUserHomeDir.dir("test-kit-daemon").get().asFile,
        gradleInstallationForTest.gradleUserHomeDir.dir("kotlin-compiler-daemon").get().asFile,
        gradleInstallationForTest.daemonRegistry.get().asFile
    )
    else -> emptyList()
}

fun Task.attachedReportLocations() = when (this) {
    is JapicmpTask -> listOf(richReport.destinationDir.resolve(richReport.reportName))
    is PerformanceTest -> listOf(reportDir.parentFile)
    else -> emptyList()
}

fun zip(destZip: File, srcDir: File) {
    destZip.parentFile.mkdirs()
    ZipOutputStream(FileOutputStream(destZip), StandardCharsets.UTF_8).use { zipOutput ->
        val srcPath = srcDir.toPath()
        Files.walk(srcPath).use { paths ->
            paths
                .filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .forEach { path ->
                    val zipEntry = ZipEntry(srcPath.relativize(path).toString())
                    zipOutput.putNextEntry(zipEntry)
                    Files.copy(path, zipOutput)
                    zipOutput.closeEntry()
                }
        }
    }
}

fun prepareReportForCiPublishing(report: File) {
    if (report.exists()) {
        if (report.isDirectory) {
            val destFile = rootProject.layout.buildDirectory.file("report-${project.name}-${report.name}.zip").get().asFile
            zip(destFile, report)
        } else {
            copy {
                from(report)
                into(rootProject.layout.buildDirectory)
                rename { "report-${project.name}-${report.parentFile.name}-${report.name}" }
            }
        }
    }
}
