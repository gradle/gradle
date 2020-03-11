package org.gradle.gradlebuild.buildquality

import me.champeau.gradle.japicmp.JapicmpTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.tasks.testing.junit.result.TestResultSerializer
import org.gradle.api.reporting.Reporting
import org.gradle.api.tasks.testing.Test
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.gradlebuild.buildquality.classycle.Classycle
import org.gradle.gradlebuild.docs.FindBrokenInternalLinks
import org.gradle.gradlebuild.test.integrationtests.DistributionTest
import org.gradle.gradlebuild.testing.integrationtests.cleanup.TestFileCleanUpExtension
import org.gradle.gradlebuild.testing.integrationtests.cleanup.WhenNotEmpty
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.gradle.plugin.devel.tasks.ValidatePlugins
import org.gradle.testing.DistributedPerformanceTest
import java.io.File


/**
 * When run from a Continuous Integration environment, we only want to archive a subset of reports, mostly for
 * failing tasks only, to not use up unnecessary disk space on Team City. This also improves the performance of
 * artifact publishing by reducing the artifacts and packaging reports that consist of multiple files.
 *
 * Reducing the number of reports also makes it easier to find the important ones when analysing a failed build in
 * Team City.
 */
open class CiReportingPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        // Configure the testFilesCleanup policy in each subproject's build script
        subprojects.forEach { it.extensions.create<TestFileCleanUpExtension>("testFilesCleanup", objects) }

        if (BuildEnvironment.isCiServer) {
            gradle.buildFinished {
                val failedTasks = project.failedTasks()
                val executedTasks = project.executedTasks()
                val tmpTestFiles = project.subprojects.flatMap { it.tmpTestFiles() }
                prepareReportsForCiPublishing(failedTasks, executedTasks, tmpTestFiles)
                project.cleanUp(tmpTestFiles.map { it.first })
                project.verifyTestFilesCleanup(failedTasks, tmpTestFiles)
            }
        }
    }

    /**
     * After archiving the test files, do a cleanup to get rid of TeamCity "XX published a lot of small artifacts" warning
     */
    private
    fun Project.cleanUp(filesToCleanUp: List<File>) {
        try {
            delete(filesToCleanUp)
        } catch (e: Exception) {
            // https://github.com/gradle/gradle-private/issues/2983#issuecomment-596083202
            e.printStackTrace()
        }
    }

    private
    fun Project.getCleanUpPolicy(childProjectName: String) = childProjects[childProjectName]?.extensions?.getByType(TestFileCleanUpExtension::class.java)?.policy?.getOrElse(WhenNotEmpty.FAIL)


    private
    fun Project.verifyTestFilesCleanup(failedTasks: List<Task>, tmpTestFiles: List<Pair<File, String>>) {
        if (failedTasks.any { it is Test }) {
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

    private
    fun Project.prepareReportsForCiPublishing(failedTasks: List<Task>, executedTasks: List<Task>, tmpTestFiles: List<Pair<File, String>>) {
        val failedTaskCustomReports = failedTasks.flatMap { it.failedTaskGenericHtmlReports() }
        val attachedReports = executedTasks.flatMap { it.attachedReportLocations() }
        val executedTaskCustomReports = failedTasks.flatMap { it.failedTaskCustomReports() }

        val allReports = failedTaskCustomReports + attachedReports + executedTaskCustomReports + tmpTestFiles
        allReports.distinctBy { (report, _) -> report }.forEach { (report, projectName) ->
            prepareReportForCiPublishing(report, projectName)
        }
    }

    private
    fun Project.tmpTestFiles() =
        File(buildDir, "tmp/test files").listFiles()?.filter {
            var nonEmpty = false
            project.fileTree(it).visit {
                if (!isDirectory) {
                    nonEmpty = true
                }
            }
            nonEmpty
        }?.map {
            it to name
        } ?: emptyList()

    private
    fun Project.executedTasks() = gradle.taskGraph.allTasks.filter { it.state.executed }

    private
    fun Project.failedTasks() = gradle.taskGraph.allTasks.filter { it.state.failure != null || it.containsFailedTest() }

    // We count the test task containing flaky result as failed
    private
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

    private
    fun Task.failedTaskGenericHtmlReports() = when (this) {
        is Reporting<*> -> listOf(this.reports["html"].destination to project.name)
        else -> emptyList()
    }

    private
    fun Task.failedTaskCustomReports() = when (this) {
        is ValidatePlugins -> listOf(outputFile.get().asFile to project.name)
        is Classycle -> listOf(reportFile to project.name)
        is FindBrokenInternalLinks -> listOf(reportFile.get().asFile to project.name)
        is DistributionTest -> listOf(
            gradleInstallationForTest.gradleUserHomeDir.dir("worker-1/test-kit-daemon").get().asFile to "all-logs",
            gradleInstallationForTest.gradleUserHomeDir.dir("worker-1/kotlin-compiler-daemon").get().asFile to "all-logs",
            gradleInstallationForTest.daemonRegistry.get().asFile to "all-logs"
        )
        else -> emptyList()
    }

    private
    fun Task.attachedReportLocations() = when (this) {
        is JapicmpTask -> listOf(richReport.destinationDir.resolve(richReport.reportName) to project.name)
        is DistributedPerformanceTest -> listOf(reportDir.parentFile to project.name)
        else -> emptyList()
    }

    private
    fun Project.prepareReportForCiPublishing(report: File, projectName: String) {
        if (report.exists()) {
            if (report.isDirectory) {
                val destFile = rootProject.layout.buildDirectory.file("report-$projectName-${report.name}.zip").get().asFile
                ant.withGroovyBuilder {
                    "zip"("destFile" to destFile) {
                        "fileset"("dir" to report)
                    }
                }
            } else {
                copy {
                    from(report)
                    into(rootProject.buildDir)
                    rename { "report-$projectName-${report.parentFile.name}-${report.name}" }
                }
            }
        }
    }
}
