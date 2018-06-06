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
package org.gradle.gradlebuild.profiling.buildscan

import com.gradle.scan.plugin.BuildScanExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.GradleInternal
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CodeNarc
import org.gradle.api.reporting.Reporting
import org.gradle.gradlebuild.BuildEnvironment.isCiServer
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher
import org.gradle.kotlin.dsl.*
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import kotlin.concurrent.thread


open class BuildScanPlugin : Plugin<Project> {

    private
    lateinit var buildScan: BuildScanExtension

    override fun apply(project: Project): Unit = project.run {
        apply(plugin = "com.gradle.build-scan")
        buildScan = the()

        extractCiOrLocalData()
        extractVcsData()

        if (isCiServer) {
            extractAllReportsFromCI()
        }

        extractCheckstyleAndCodenarcData()
        extractBuildCacheData()
    }

    private
    fun buildScan(configure: BuildScanExtension.() -> Unit) {
        buildScan.apply(configure)
    }

    private
    fun Project.extractCheckstyleAndCodenarcData() {
        gradle.taskGraph.afterTask {
            if (state.failure != null) {

                if (this is Checkstyle && reports.xml.destination.exists()) {
                    val checkstyle = Jsoup.parse(reports.xml.destination.readText(), "", Parser.xmlParser())
                    val errors = checkstyle.getElementsByTag("file").flatMap { file ->
                        file.getElementsByTag("error").map { error ->
                            val filePath = rootProject.relativePath(file.attr("name"))
                            "$filePath:${error.attr("line")}:${error.attr("column")} \u2192 ${error.attr("message")}"
                        }
                    }

                    errors.forEach { buildScan.value("Checkstyle Issue", it) }
                }

                if (this is CodeNarc && reports.xml.destination.exists()) {
                    val codenarc = Jsoup.parse(reports.xml.destination.readText(), "", Parser.xmlParser())
                    val errors = codenarc.getElementsByTag("Package").flatMap { codenarcPackage ->
                        codenarcPackage.getElementsByTag("File").flatMap { file ->
                            file.getElementsByTag("Violation").map { violation ->
                                val filePath = rootProject.relativePath(file.attr("name"))
                                val message = violation.getElementsByTag("Message").first() ?: violation.getElementsByTag("SourceLine").first()
                                "$filePath:${violation.attr("lineNumber")} \u2192 ${message.text()}"
                            }
                        }
                    }

                    errors.forEach { buildScan.value("CodeNarc Issue", it) }
                }
            }
        }
    }

    private
    fun Project.extractCiOrLocalData() {
        if (isCiServer) {
            buildScan {
                tag("CI")
                tag(System.getenv("TEAMCITY_BUILDCONF_NAME"))
                link("TeamCity Build", System.getenv("BUILD_URL"))
                value("Build ID", System.getenv("BUILD_ID"))
            }
            setCommitId(System.getenv("BUILD_VCS_NUMBER"))
        } else {
            buildScan.tag("LOCAL")
        }
    }

    private
    fun Project.extractVcsData() {

        fun fork(action: () -> Unit) = thread {
            try {
                action()
            } catch (e: Exception) {
                rootProject.logger.warn("Build scan user data async exec failed", e)
            }
        }

        val threads = listOf(

            fork {
                system("git", "rev-parse", "--verify", "HEAD").let { commitId ->
                    setCommitId(commitId)
                }
            },

            fork {
                system("git", "status", "--porcelain").let { status ->
                    if (status.isNotEmpty()) {
                        buildScan {
                            tag("dirty")
                            value("Git Status", status)
                        }
                    }
                }
            },

            fork {
                system("git", "rev-parse", "--abbrev-ref", "HEAD").let { branchName ->
                    if (branchName.isNotEmpty() && branchName != "HEAD") {
                        buildScan {
                            tag(branchName)
                            value("Git Branch Name", branchName)
                        }
                    }
                }
            })

        buildScan.buildFinished {
            awaitAll(threads)
        }
    }

    private
    fun Project.extractBuildCacheData() {
        if (gradle.startParameter.isBuildCacheEnabled) {
            buildScan.tag("CACHED")

            val tasksToInvestigate = System.getProperty("cache.investigate.tasks", ":baseServices:classpathManifest")
                .split(",")

            buildScan.buildFinished {
                allprojects.flatMap { gradle.taskGraph.allTasks }
                    .filter { it.state.executed && it.path in tasksToInvestigate }
                    .forEach { task ->
                        val hasher = (gradle as GradleInternal).services.get(ClassLoaderHierarchyHasher::class.java)
                        Visitor(buildScan, hasher, task).visit(task::class.java.classLoader)
                    }
            }
        }
    }

    private
    fun Project.extractAllReportsFromCI() {
        val capturedReportingTypes = listOf("html") // can add xml, text, junitXml if wanted
        val basePath = "${System.getenv("BUILD_SERVER_URL")}/repository/download/${System.getenv("BUILD_TYPE_ID")}/${System.getenv("BUILD_ID")}:id"

        gradle.taskGraph.afterTask {
            if (state.failure != null && this is Reporting<*>) {
                this.reports.filter { it.name in capturedReportingTypes && it.isEnabled && it.destination.exists() }
                    .forEach { report ->
                        val linkName = "${this::class.java.simpleName.split("_")[0]} Report ($path)" // Strip off '_Decorated' addition to class names
                        // see: ciReporting.gradle
                        val reportPath =
                            if (report.destination.isDirectory) "report-${project.name}-${report.destination.name}.zip"
                            else "report-${project.name}-${report.destination.parentFile.name}-${report.destination.name}"
                        val reportLink = "$basePath/$reportPath"
                        buildScan.link(linkName, reportLink)
                    }
            }
        }
    }

    private
    fun Project.setCommitId(commitId: String) =
        buildScan {
            value("Git Commit ID", commitId)
            link("Source", "https://github.com/gradle/gradle/commit/" + commitId)
        }
}


private
fun Project.system(vararg args: String): String =
    ProcessBuilder(args.toList())
        .directory(rootDir)
        .start()
        .run {
            assert(waitFor() == 0)
            inputStream.bufferedReader().use { it.readText().trim() }
        }


private
fun awaitAll(threads: Iterable<Thread>) =
    threads.forEach(Thread::join)
