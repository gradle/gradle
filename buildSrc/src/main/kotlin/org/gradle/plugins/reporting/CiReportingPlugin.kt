package org.gradle.plugins.reporting

import me.champeau.gradle.japicmp.JapicmpTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.reporting.Reporting
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.gradle.plugin.devel.tasks.ValidateTaskProperties
import org.gradle.plugins.classycle.Classycle
import org.gradle.testing.DistributedPerformanceTest
import org.gradle.testing.DistributionTest
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

    override fun apply(project: Project) = project.run {

        val isCiServer = System.getenv().containsKey("CI")

        if (isCiServer) {
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

        (failedTaskGenericHtmlReports + attachedReports + failedTaskCustomReports).toMap().forEach {
            prepareReportForCIPublishing(it.key, it.value)
        }
    }

    private
    fun failedTasks(projects: Set<Project>) = projects.flatMap { it.tasks.matching { it.state.failure != null } }

    private
    fun executedTasks(projects: Set<Project>) = projects.flatMap { it.tasks.matching { it.state.executed } }

    private
    fun Task.failedTaskGenericHtmlReports() = when(this) {
        is Reporting<*> -> {
            val reportContainer = this.reports
            val reportDestination = reportContainer.getByName("html").destination
            listOf(reportDestination to project.name)
        }
        else -> listOf()
    }

    private
    fun Task.failedTaskCustomReports() = when (this) {
        is ValidateTaskProperties -> listOf(outputFile.asFile.get() to project.name)
        is Classycle -> listOf(reportFile to project.name)
        is DistributionTest -> listOf(
            File(gradleInstallationForTest.gradleUserHomeDir.asFile.get(), "worker-1/test-kit-daemon") to "all-logs",
            gradleInstallationForTest.daemonRegistry.asFile.get() to "all-logs"
        )
        else -> listOf()
    }

    private
    fun Task.attachedReportLocations(): Collection<Pair<File, String>> = when (this) {
        is JapicmpTask -> listOf(File(richReport.destinationDir, richReport.reportName) to project.name)
        is DistributedPerformanceTest -> listOf(scenarioReport.parentFile to project.name)
        else -> listOf()
    }

    private
    fun Project.prepareReportForCIPublishing(report: File, projectName: String) {
        if (report.exists()) {
            if (report.isDirectory) {
                val destFile = File("${rootProject.buildDir}/report-$projectName-${report.name}.zip")
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
