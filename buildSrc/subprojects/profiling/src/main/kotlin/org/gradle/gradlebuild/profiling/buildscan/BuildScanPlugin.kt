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
import org.gradle.api.Task
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CodeNarc
import org.gradle.api.reporting.Reporting
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.build.ClasspathManifest
import org.gradle.gradlebuild.BuildEnvironment.isCiServer
import org.gradle.gradlebuild.BuildEnvironment.isJenkins
import org.gradle.gradlebuild.BuildEnvironment.isTravis
import org.gradle.internal.service.scopes.VirtualFileSystemServices
import org.gradle.kotlin.dsl.*
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.component1
import kotlin.collections.component2


const val serverUrl = "https://e.grdev.net"


private
const val gitCommitName = "Git Commit ID"


private
const val ciBuildTypeName = "CI Build Type"


private
const val vfsRetentionEnabledName = "vfsRetentionEnabled"


@Suppress("unused") // consumed as plugin gradlebuild.buildscan
open class BuildScanPlugin : Plugin<Project> {

    private
    lateinit var buildScan: BuildScanExtension

    private
    val cacheMissTagged = AtomicBoolean(false)

    override fun apply(project: Project): Unit = project.run {
        buildScan = the()

        extractCiOrLocalData()
        extractVcsData()

        if (isCiServer && !isTravis && !isJenkins) {
            extractAllReportsFromCI()
            monitorUnexpectedCacheMisses()
        }

        extractCheckstyleAndCodenarcData()
        extractBuildCacheData()
        extractVfsRetentionData()
    }

    private
    fun Project.monitorUnexpectedCacheMisses() {
        gradle.taskGraph.afterTask {
            if (buildCacheEnabled() && isCacheMiss() && isNotTaggedYet()) {
                buildScan.tag("CACHE_MISS")
            }
        }
    }

    private
    fun Project.buildCacheEnabled() = gradle.startParameter.isBuildCacheEnabled

    private
    fun isNotTaggedYet() = cacheMissTagged.compareAndSet(false, true)

    private
    fun Task.isCacheMiss() = !state.skipped && (isCompileCacheMiss() || isAsciidoctorCacheMiss())

    private
    fun Task.isCompileCacheMiss() = isMonitoredCompileTask() && !isExpectedCompileCacheMiss()

    private
    fun Task.isAsciidoctorCacheMiss() = isMonitoredAsciidoctorTask() && !isExpectedAsciidoctorCacheMiss()

    private
    fun Task.isMonitoredCompileTask() = this is AbstractCompile || this is ClasspathManifest

    private
    fun Task.isMonitoredAsciidoctorTask() = false // No asciidoctor tasks are cacheable for now

    private
    fun Task.isExpectedAsciidoctorCacheMiss() =
    // Expected cache-miss for asciidoctor task:
    // 1. CompileAll is the seed build for docs:distDocs
    // 2. Gradle_Check_BuildDistributions is the seed build for other asciidoctor tasks
    // 3. buildScanPerformance test, which doesn't depend on compileAll
    // 4. buildScanPerformance test, which doesn't depend on compileAll
        isInBuild(
            "Gradle_Check_CompileAll",
            "Gradle_Check_BuildDistributions",
            "Enterprise_Master_Components_GradleBuildScansPlugin_Performance_PerformanceLinux",
            "Enterprise_Release_Components_BuildScansPlugin_Performance_PerformanceLinux"
        )

    private
    fun Task.isExpectedCompileCacheMiss() =
    // Expected cache-miss:
    // 1. CompileAll is the seed build
    // 2. Gradleception which re-builds Gradle with a new Gradle version
    // 3. buildScanPerformance test, which doesn't depend on compileAll
    // 4. buildScanPerformance test, which doesn't depend on compileAll
        isInBuild(
            "Gradle_Check_CompileAll",
            "Enterprise_Master_Components_GradleBuildScansPlugin_Performance_PerformanceLinux",
            "Enterprise_Release_Components_BuildScansPlugin_Performance_PerformanceLinux",
            "Gradle_Check_Gradleception"
        )

    private
    fun Task.isInBuild(vararg buildTypeIds: String) = System.getenv("BUILD_TYPE_ID") in buildTypeIds

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
                                val message = violation.run {
                                    getElementsByTag("Message").first()
                                        ?: getElementsByTag("SourceLine").first()
                                }
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
                when {
                    isTravis -> {
                        link("Travis Build", System.getenv("TRAVIS_BUILD_WEB_URL"))
                        value("Build ID", System.getenv("TRAVIS_BUILD_ID"))
                        setCommitId(System.getenv("TRAVIS_COMMIT"))
                    }
                    isJenkins -> {
                        link("Jenkins Build", System.getenv("BUILD_URL"))
                        value("Build ID", System.getenv("BUILD_ID"))
                        setCommitId(System.getenv("GIT_COMMIT"))
                    }
                    else -> {
                        link("TeamCity Build", System.getenv("BUILD_URL"))
                        value("Build ID", System.getenv("BUILD_ID"))
                        setCommitId(System.getenv("BUILD_VCS_NUMBER"))
                    }
                }
                whenEnvIsSet("BUILD_TYPE_ID") { buildType ->
                    value(ciBuildTypeName, buildType)
                    link("Build Type Scans", customValueSearchUrl(mapOf(ciBuildTypeName to buildType)))
                }
            }
        } else {
            buildScan.tag("LOCAL")
            if (listOf("idea.registered", "idea.active", "idea.paths.selector").map(System::getProperty).filterNotNull().isNotEmpty()) {
                buildScan.tag("IDEA")
                System.getProperty("idea.paths.selector")?.let { ideaVersion ->
                    buildScan.value("IDEA version", ideaVersion)
                }
            }
        }
    }

    private
    fun BuildScanExtension.whenEnvIsSet(envName: String, action: BuildScanExtension.(envValue: String) -> Unit) {
        val envValue: String? = System.getenv(envName)
        if (!envValue.isNullOrEmpty()) {
            action(envValue)
        }
    }

    private
    fun Project.extractVcsData() {
        buildScan {

            if (!isCiServer) {
                background {
                    setCommitId(execAndGetStdout("git", "rev-parse", "--verify", "HEAD"))
                }
            }

            background {
                execAndGetStdout("git", "status", "--porcelain").takeIf { it.isNotEmpty() }?.let { status ->
                    tag("dirty")
                    value("Git Status", status)
                }
            }

            background {
                execAndGetStdout("git", "rev-parse", "--abbrev-ref", "HEAD").takeIf { it.isNotEmpty() && it != "HEAD" }?.let { branchName ->
                    tag(branchName)
                    value("Git Branch Name", branchName)
                }
            }
        }
    }

    private
    fun Project.extractBuildCacheData() {
        if (gradle.startParameter.isBuildCacheEnabled) {
            buildScan.tag("CACHED")
        }
    }

    private
    fun Project.extractVfsRetentionData() {
        val vfsRetentionEnabled = VirtualFileSystemServices.isRetentionEnabled(gradle.startParameter.systemPropertiesArgs)
        buildScan.value(vfsRetentionEnabledName, vfsRetentionEnabled.toString())
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
    fun BuildScanExtension.setCommitId(commitId: String) {
        value(gitCommitName, commitId)
        link("Source", "https://github.com/gradle/gradle/commit/$commitId")
        if (!isTravis) {
            link("Git Commit Scans", customValueSearchUrl(mapOf(gitCommitName to commitId)))
            link("CI CompileAll Scan", customValueSearchUrl(mapOf(gitCommitName to commitId)) + "&search.tags=CompileAll")
        }
    }

    private
    inline fun buildScan(configure: BuildScanExtension.() -> Unit) {
        buildScan.apply(configure)
    }
}


private
fun customValueSearchUrl(search: Map<String, String>): String {
    val query = search.map { (name, value) ->
        "search.names=${name.urlEncode()}&search.values=${value.urlEncode()}"
    }.joinToString("&")

    return "$serverUrl/scans?$query"
}


private
fun String.urlEncode() = URLEncoder.encode(this, Charsets.UTF_8.name())
