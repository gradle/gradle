package org.gradle.plugins.reporting

import me.champeau.gradle.japicmp.JapicmpTask
import org.gradle.api.Plugin
import org.gradle.api.Project
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
                val reports = mutableMapOf<File, String>()
                allprojects {
                    tasks.all {
                        if (this is Reporting<*> && state.failure != null) {
                            val reportContainer = this.reports
                            val reportDestination = reportContainer.getByName("html").destination
                            reports[reportDestination] = project.name
                        }
                    }
                }

                subprojects {
                    val projectName = name
                    tasks.all {
                        if (state.failure != null) {
                            when (this) {
                                is ValidateTaskProperties -> reports[outputFile.asFile.get()] = projectName
                                is Classycle -> reports[reportFile] = projectName
                                is DistributionTest -> {
                                    reports[File(gradleInstallationForTest.gradleUserHomeDir.asFile.get(), "worker-1/test-kit-daemon")] = "all-logs"
                                    reports[gradleInstallationForTest.daemonRegistry.asFile.get()] = "all-logs"
                                }
                            }
                        }
                        when (this) {
                            is JapicmpTask -> reports[File(richReport.destinationDir, richReport.reportName)] = projectName
                            is DistributedPerformanceTest -> reports[scenarioReport.parentFile] = projectName
                        }
                    }
                }

                reports.forEach {
                    prepareReportForCIPublishing(it.key, it.value)
                }
            }
        }
    }

    private fun Project.prepareReportForCIPublishing(report: File, projectName: String) {
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
