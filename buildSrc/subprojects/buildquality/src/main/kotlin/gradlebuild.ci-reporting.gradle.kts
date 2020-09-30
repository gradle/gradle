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
import gradlebuild.cleanup.WhenNotEmpty
import gradlebuild.cleanup.extension.TestFileCleanUpExtension
import gradlebuild.docs.FindBrokenInternalLinks
import gradlebuild.integrationtests.tasks.DistributionTest
import gradlebuild.performance.tasks.DistributedPerformanceTest
import me.champeau.gradle.japicmp.JapicmpTask
import org.gradle.api.internal.tasks.testing.junit.result.TestResultSerializer
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
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
subprojects.forEach {
    // Configure the testFilesCleanup policy in each subproject's build script (This should be done directly in each subproject)
    it.extensions.create<TestFileCleanUpExtension>("testFilesCleanup")
}

if (BuildEnvironment.isCiServer) {
    gradle.buildFinished {
        val failedTasks = failedTasks()
        val executedTasks = executedTasks()
        val tmpTestFiles = subprojects.flatMap { it.tmpTestFiles() }
        prepareReportsForCiPublishing(failedTasks, executedTasks, tmpTestFiles)
        cleanUp(tmpTestFiles.map { it.first })
        verifyTestFilesCleanup(failedTasks, tmpTestFiles)
    }
}


/**
 * After archiving the test files, do a cleanup to get rid of TeamCity "XX published a lot of small artifacts" warning
 */
fun cleanUp(filesToCleanUp: List<File>) {
    try {
        delete(filesToCleanUp)
    } catch (e: Exception) {
        // https://github.com/gradle/gradle-private/issues/2983#issuecomment-596083202
        e.printStackTrace()
    }
}

fun getCleanUpPolicy(childProjectName: String) = childProjects[childProjectName]?.extensions?.getByType(TestFileCleanUpExtension::class.java)?.policy?.getOrElse(WhenNotEmpty.FAIL)

fun verifyTestFilesCleanup(failedTasks: List<Task>, tmpTestFiles: List<Pair<File, String>>) {
    if (failedTasks.any { it is Test }) {
        println("Leftover files: $tmpTestFiles")
        return
    }

    val testFilesToFail = tmpTestFiles.filter { getCleanUpPolicy(it.second) != WhenNotEmpty.REPORT }
    val testFilesToReport = tmpTestFiles.filter { getCleanUpPolicy(it.second) == WhenNotEmpty.REPORT }

    if (testFilesToReport.isNotEmpty()) {
        println("Found non-empty test files dir:\n${testFilesToReport.joinToString("\n") { it.first.absolutePath }}")
    }

    if (testFilesToFail.isNotEmpty()) {
        throw GradleException("Found non-empty test files dir:\n${tmpTestFiles.joinToString("\n") { it.first.absolutePath }}")
    }
}

fun prepareReportsForCiPublishing(failedTasks: List<Task>, executedTasks: List<Task>, tmpTestFiles: List<Pair<File, String>>) {
    val failedTaskCustomReports = failedTasks.flatMap { it.failedTaskGenericHtmlReports() }
    val attachedReports = executedTasks.flatMap { it.attachedReportLocations() }
    val executedTaskCustomReports = failedTasks.flatMap { it.failedTaskCustomReports() }

    val allReports = failedTaskCustomReports + attachedReports + executedTaskCustomReports + tmpTestFiles
    allReports.distinctBy { (report, _) -> report }.forEach { (report, projectName) ->
        prepareReportForCiPublishing(report, projectName)
    }
}

fun Project.tmpTestFiles() =
    layout.buildDirectory.dir("tmp/test files").get().asFile.listFiles()?.filter {
        Files.walk(it.toPath()).use { paths -> !paths.allMatch { it.toFile().isDirectory } }
    }?.map {
        it to name
    } ?: emptyList()

fun executedTasks() = gradle.taskGraph.allTasks.filter { it.state.executed }

fun failedTasks() = gradle.taskGraph.allTasks.filter { it.state.failure != null || it.containsFailedTest() }

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
    is Reporting<*> -> listOf(this.reports["html"].destination to project.name)
    else -> emptyList()
}

fun Task.failedTaskCustomReports() = when (this) {
    is ValidatePlugins -> listOf(outputFile.get().asFile to project.name)
    is Classycle -> listOf(reportFile to project.name)
    is FindBrokenInternalLinks -> listOf(reportFile.get().asFile to project.name)
    is DistributionTest -> listOf(
        gradleInstallationForTest.gradleUserHomeDir.dir("test-kit-daemon").get().asFile to "all-logs",
        gradleInstallationForTest.gradleUserHomeDir.dir("kotlin-compiler-daemon").get().asFile to "all-logs",
        gradleInstallationForTest.daemonRegistry.get().asFile to "all-logs"
    )
    else -> emptyList()
}

fun Task.attachedReportLocations() = when (this) {
    is JapicmpTask -> listOf(richReport.destinationDir.resolve(richReport.reportName) to project.name)
    is DistributedPerformanceTest -> listOf(reportDir.parentFile to project.name)
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

fun prepareReportForCiPublishing(report: File, projectName: String) {
    if (report.exists()) {
        if (report.isDirectory) {
            val destFile = rootProject.layout.buildDirectory.file("report-$projectName-${report.name}.zip").get().asFile
            zip(destFile, report)
        } else {
            copy {
                from(report)
                into(rootProject.layout.buildDirectory)
                rename { "report-$projectName-${report.parentFile.name}-${report.name}" }
            }
        }
    }
}
