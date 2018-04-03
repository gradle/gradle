package org.gradle.gradlebuild.buildquality

import me.champeau.gradle.japicmp.JapicmpTask
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.reporting.Reporting
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.gradle.plugin.devel.tasks.ValidateTaskProperties
import org.gradle.gradlebuild.buildquality.classycle.Classycle
import org.gradle.testing.DistributedPerformanceTest
import org.gradle.gradlebuild.test.integrationtests.DistributionTest
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
        if (BuildEnvironment.isCiServer) {
            gradle.buildFinished {
                prepareReportsForCiPublishing()
            }
        }
    }

    private
    fun Project.prepareReportsForCiPublishing() {
        val failedTaskGenericHtmlReports = failedTasks(allprojects).flatMap {
            it.failedTaskGenericHtmlReports()
        }
        val attachedReports = executedTasks(subprojects).flatMap {
            it.attachedReportLocations()
        }
        val failedTaskCustomReports = failedTasks(subprojects).flatMap {
            it.failedTaskCustomReports()
        }

        val allReports = failedTaskGenericHtmlReports + attachedReports + failedTaskCustomReports
        allReports.distinctBy { (report, _) -> report }.forEach { (report, projectName) ->
            prepareReportForCiPublishing(report, projectName)
        }
    }

    private
    fun failedTasks(projects: Set<Project>) = projects.flatMap { it.tasks.matching { it.state.failure != null } }

    private
    fun executedTasks(projects: Set<Project>) = projects.flatMap { it.tasks.matching { it.state.executed } }

    private
    fun Task.failedTaskGenericHtmlReports() = when (this) {
        is Reporting<*> -> listOf(this.reports["html"].destination to project.name)
        else -> emptyList()
    }

    private
    fun Task.failedTaskCustomReports() = when (this) {
        is ValidateTaskProperties -> listOf(outputFile.get().asFile to project.name)
        is Classycle -> listOf(reportFile to project.name)
        is DistributionTest -> listOf(
            gradleInstallationForTest.gradleUserHomeDir.dir("worker-1/test-kit-daemon").get().asFile to "all-logs",
            gradleInstallationForTest.daemonRegistry.get().asFile to "all-logs"
        )
        else -> emptyList()
    }

    private
    fun Task.attachedReportLocations() = when (this) {
        is JapicmpTask -> listOf(richReport.destinationDir.resolve(richReport.reportName) to project.name)
        is DistributedPerformanceTest -> listOf(scenarioReport.parentFile to project.name)
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
