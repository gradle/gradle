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

import com.gradle.scan.plugin.BuildScanExtension
import gradlebuild.basics.BuildEnvironment.isCiServer
import gradlebuild.basics.testDistributionEnabled
import gradlebuild.basics.BuildEnvironment.isGhActions
import gradlebuild.basics.BuildEnvironment.isJenkins
import gradlebuild.basics.BuildEnvironment.isTravis
import gradlebuild.basics.kotlindsl.execAndGetStdout
import gradlebuild.basics.tasks.ClasspathManifest
import org.gradle.api.internal.BuildType
import org.gradle.api.internal.GradleInternal
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.internal.watch.vfs.BuildFinishedFileSystemWatchingBuildOperationType
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.launcher.exec.RunBuildBuildOperationType
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean

val serverUrl = "https://ge.gradle.org"
val gitCommitName = "gitCommitId"
val tcBuildTypeName = "tcBuildType"

val cacheMissTagged = AtomicBoolean(false)

val buildScan = the<BuildScanExtension>() // we can not use plugin {} because this is registered by a settings plugin
inline fun buildScan(configure: BuildScanExtension.() -> Unit) {
    buildScan.apply(configure)
}

extractCiData()

if (isCiServer) {
    if (!isTravis && !isJenkins) {
        extractAllReportsFromCI()
        monitorUnexpectedCacheMisses()
    }
}

if (project.testDistributionEnabled()) {
    buildScan.tag("TEST_DISTRIBUTION")
}

extractCheckstyleAndCodenarcData()

extractWatchFsData()

if ((project.gradle as GradleInternal).services.get(BuildType::class.java) != BuildType.TASKS) {
    buildScan.tag("SYNC")
}

fun monitorUnexpectedCacheMisses() {
    gradle.taskGraph.afterTask {
        if (buildCacheEnabled() && isCacheMiss() && isNotTaggedYet()) {
            buildScan.tag("CACHE_MISS")
        }
    }
}

fun buildCacheEnabled() = gradle.startParameter.isBuildCacheEnabled

fun isNotTaggedYet() = cacheMissTagged.compareAndSet(false, true)

fun Task.isCacheMiss() = !state.skipped && (isCompileCacheMiss() || isAsciidoctorCacheMiss())

fun Task.isCompileCacheMiss() = isMonitoredCompileTask() && !isExpectedCompileCacheMiss()

fun isAsciidoctorCacheMiss() = isMonitoredAsciidoctorTask() && !isExpectedAsciidoctorCacheMiss()

fun Task.isMonitoredCompileTask() = this is AbstractCompile || this is ClasspathManifest

fun isMonitoredAsciidoctorTask() = false // No asciidoctor tasks are cacheable for now

fun isExpectedAsciidoctorCacheMiss() =
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

fun isExpectedCompileCacheMiss() =
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

fun isInBuild(vararg buildTypeIds: String) = System.getenv("BUILD_TYPE_ID") in buildTypeIds

fun extractCheckstyleAndCodenarcData() {
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

fun isEc2Agent() = java.net.InetAddress.getLocalHost().hostName.startsWith("ip-")

fun Project.extractCiData() {
    if (isCiServer) {
        buildScan {
            background {
                setCompileAllScanSearch(execAndGetStdout("git", "rev-parse", "--verify", "HEAD"))
            }
            if (isEc2Agent()) {
                tag("EC2")
            }
            if (isGhActions) {
                tag("GH_ACTION")
            }
            whenEnvIsSet("BUILD_TYPE_ID") { buildType ->
                value(tcBuildTypeName, buildType)
                link("Build Type Scans", customValueSearchUrl(mapOf(tcBuildTypeName to buildType)))
            }
        }
    }
}

fun BuildScanExtension.whenEnvIsSet(envName: String, action: BuildScanExtension.(envValue: String) -> Unit) {
    val envValue: String? = System.getenv(envName)
    if (!envValue.isNullOrEmpty()) {
        action(envValue)
    }
}

fun Project.extractWatchFsData() {
    gradle.serviceOf<BuildOperationListenerManager>().let { listenerManager ->
        listenerManager.addListener(FileSystemWatchingBuildOperationListener(listenerManager, buildScan))
    }
}

open class FileSystemWatchingBuildOperationListener(private val buildOperationListenerManager: BuildOperationListenerManager, private val buildScan: BuildScanExtension) : BuildOperationListener {

    override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent) {
    }

    override fun progress(operationIdentifier: OperationIdentifier, progressEvent: OperationProgressEvent) {
    }

    override fun finished(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent) {
        when (val result = finishEvent.result) {
            is RunBuildBuildOperationType.Result -> buildOperationListenerManager.removeListener(this)
            is BuildFinishedFileSystemWatchingBuildOperationType.Result -> {
                if (result.isWatchingEnabled) {
                    buildScan.value("watchFsStoppedDuringBuild", result.isStoppedWatchingDuringTheBuild.toString())
                    result.statistics?.let {
                        buildScan.value("watchFsEventsReceivedDuringBuild", it.numberOfReceivedEvents.toString())
                        buildScan.value("watchFsRetainedDirectories", it.retainedDirectories.toString())
                        buildScan.value("watchFsRetainedFiles", it.retainedRegularFiles.toString())
                        buildScan.value("watchFsRetainedMissingFiles", it.retainedMissingFiles.toString())
                        buildScan.value("watchFsWatchedHierarchies", it.numberOfWatchedHierarchies.toString())
                    }
                }
            }
        }
    }
}

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

fun BuildScanExtension.setCompileAllScanSearch(commitId: String) {
    if (!isTravis) {
        link("CI CompileAll Scan", customValueSearchUrl(mapOf(gitCommitName to commitId)) + "&search.tags=CompileAll")
    }
}

fun customValueSearchUrl(search: Map<String, String>): String {
    val query = search.map { (name, value) ->
        "search.names=${name.urlEncode()}&search.values=${value.urlEncode()}"
    }.joinToString("&")

    return "$serverUrl/scans?$query"
}

fun String.urlEncode() = URLEncoder.encode(this, Charsets.UTF_8.name())
