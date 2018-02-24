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
package org.gradle.plugins.buildscan

import com.gradle.scan.plugin.BuildScanExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CodeNarc
import org.gradle.api.reporting.ReportContainer
import org.gradle.api.reporting.Reporting
import org.gradle.kotlin.dsl.the
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.gradle.api.internal.GradleInternal
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import kotlin.concurrent.thread


class BuildScanConfigurationPlugin : Plugin<Project> {
    private
    val isCiServer = System.getenv().containsKey("CI")

    override fun apply(project: Project): Unit = project.run {
        apply {
            plugin("com.gradle.build-scan")
        }

        extractCiOrLocalData()
        extractVcsData()

        if (isCiServer) {
            extractAllReportsFromCI(project)
        }

        extractCheckstyleAndCodenarcData(project)
        extractBuildCacheData()
    }

    private
    fun Project.extractCheckstyleAndCodenarcData(project: Project) {
        gradle.taskGraph.afterTask {
            if (state.failure != null) {

                if (this is Checkstyle && reports.xml.destination.exists()) {
                    val checkstyle = Jsoup.parse(reports.xml.destination.readText(), "", Parser.xmlParser())
                    val errors = checkstyle.getElementsByTag("file").map { file ->
                        file.getElementsByTag("error").map { error ->
                            val filePath = project.rootProject.relativePath(file.attr("name"))
                            "$filePath:${error.attr("line")}:${error.attr("column")} \u2192 ${error.attr("message")}"
                        }
                    }.flatten()

                    errors.forEach { project.buildScan.value("Checkstyle Issue", it) }
                }

                if (this is CodeNarc && reports.xml.destination.exists()) {
                    val codenarc = Jsoup.parse(reports.xml.destination.readText(), "", Parser.xmlParser())
                    val errors = codenarc.getElementsByTag("Package").map { codenarcPackage ->
                        codenarcPackage.getElementsByTag("File").map { file ->
                            file.getElementsByTag("Violation").map { violation ->
                                val filePath = project.rootProject.relativePath(file.attr("name"))
                                "$filePath:${violation.attr("lineNumber")} \u2192 ${violation.getElementsByTag("Message").first().text()}"
                            }
                        }.flatten()
                    }.flatten()

                    errors.forEach { project.buildScan.value("CodeNarc Issue", it) }
                }
            }
        }
    }

    private
    fun Project.extractCiOrLocalData() {
        if (isCiServer) {
            buildScan.tag("CI")
            buildScan.tag(System.getenv("TEAMCITY_BUILDCONF_NAME"))
            buildScan.link("TeamCity Build", System.getenv("BUILD_URL"))
            buildScan.value("Build ID", System.getenv("BUILD_ID"))
            setCommitId(System.getenv("BUILD_VCS_NUMBER"))
        } else {
            buildScan.tag("LOCAL")
        }
    }

    private
    fun Project.extractVcsData() {

        fun Project.run(vararg args: String): String {
            val process = ProcessBuilder(args.toList())
                .directory(rootDir)
                .start()
            assert(process.waitFor() == 0)
            return process.inputStream.bufferedReader().readText().trim()
        }

        fun Project.execAsync(f: Runnable) {
            val latch = java.util.concurrent.CountDownLatch(1)
            thread(start = true) {
                try {
                    f.run()
                } catch (e: Exception) {
                    rootProject.logger.warn("Build scan user data async exec failed", e)
                } finally {
                    latch.countDown()
                }
            }
        }

        execAsync(Runnable {
            val commitId = run("git", "rev-parse", "--verify", "HEAD")
            setCommitId(commitId)
            val status = run("git", "status", "--porcelain")
            if (!status.isEmpty()) {
                buildScan {
                    tag("dirty")
                    value("Git Status", status)
                }
            }
        })

        execAsync(Runnable {
            val branchName = run("git", "rev-parse", "--abbrev-ref", "HEAD")
            if (!branchName.isEmpty() && branchName != "HEAD") {
                buildScan.tag(branchName)
                buildScan.value("Git Branch Name", branchName)
            }
        })
    }

    private fun Project.extractBuildCacheData() {
        if (gradle.startParameter.isBuildCacheEnabled) {
            buildScan.tag("CACHED")

            val tasksToInvestigateForCacheWithPaths = if (project.hasProperty("cache.investigate.tasks.paths"))
                project.property("cache.investigate.tasks.paths").toString().split(",") else listOf(":baseServices:classpathManifest")

            val taskPropertiesWithFullFileSnapshot = mapOf(
                ":baseServices:compileJava" to listOf("classpath"))

            project.buildScan.buildFinished({
                allprojects.map { it.tasks }.flatten().forEach {
                    if (it.state.executed && (tasksToInvestigateForCacheWithPaths.contains(it.path)
                            || taskPropertiesWithFullFileSnapshot.keys.contains(it.path))) {
                        if (tasksToInvestigateForCacheWithPaths.contains(it.path)) {
                            val hasher = (gradle as GradleInternal).services.get(ClassLoaderHierarchyHasher::class.java)
                            Visitor(project.buildScan, hasher, it).visit(it::class.java.classLoader)
                        }
                    }
                }
            })
        }
    }

    private fun Project.extractAllReportsFromCI(project: Project) {
        val capturedReportingTypes = listOf("html") // can add xml, text, junitXml if wanted
        val basePath = "${System.getenv("BUILD_SERVER_URL")}/repository/download/${System.getenv("BUILD_TYPE_ID")}/${System.getenv("BUILD_ID")}:id"

        gradle.taskGraph.afterTask {
            if (state.failure != null) {
                if (this is Reporting<*>) {
                    val reportContainer = this as ReportContainer<*>
                    reportContainer
                        .filter { it.name in capturedReportingTypes && it.isEnabled && it.destination.exists() }
                        .forEach {
                            val linkName = "${this::class.java.simpleName.split("_")[0]} Report ($path)" // Strip off '_Decorated' addition to class names
                            // see: ciReporting.gradle
                            val reportPath = if (it.destination.isDirectory) {
                                "report-${project.name}-${it.destination.name}.zip"
                            } else {
                                "report-${project.name}-${it.destination.parentFile.name}-${it.destination.name}"
                            }
                            val reportLink = "$basePath/$reportPath"
                            project.buildScan.link(linkName, reportLink)
                        }
                }
            }
        }
    }

    private
    fun Project.setCommitId(commitId: String) {
        buildScan {
            value("Git Commit ID", commitId)
            link("Source", "https://github.com/gradle/gradle/commit/" + commitId)
        }
    }
}

fun Project.buildScan(configure: BuildScanExtension.() -> Unit): Unit =
    extensions.configure("buildScan", configure)

val Project.buildScan
    get() = the<BuildScanExtension>()

