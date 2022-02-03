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
import gradlebuild.basics.BuildEnvironment.isCodeQl
import gradlebuild.basics.BuildEnvironment.isGhActions
import gradlebuild.basics.BuildEnvironment.isJenkins
import gradlebuild.basics.BuildEnvironment.isTravis
import gradlebuild.basics.buildConfigurationId
import gradlebuild.basics.buildId
import gradlebuild.basics.buildServerUrl
import gradlebuild.basics.environmentVariable
import gradlebuild.basics.kotlindsl.execAndGetStdout
import gradlebuild.basics.testDistributionEnabled
import gradlebuild.buildscan.tasks.ExtractCheckstyleBuildScanData
import gradlebuild.buildscan.tasks.ExtractCodeNarcBuildScanData
import gradlebuild.identity.extension.ModuleIdentityExtension
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
import java.net.InetAddress
import java.net.URLEncoder

plugins {
    id("gradlebuild.cache-miss-monitor")
    id("gradlebuild.module-identity")
}

val serverUrl = "https://ge.gradle.org"
val gitCommitName = "gitCommitId"
val tcBuildTypeName = "tcBuildType"

// We can not use plugin {} because this is registered by a settings plugin.
// We do 'findByType' to make this script compile in pre-compiled script compilation.
// TODO to avoid the above, turn this into a settings plugin
val buildScan = extensions.findByType<BuildScanExtension>()
inline fun buildScan(configure: BuildScanExtension.() -> Unit) {
    buildScan?.apply(configure)
}

extractCiData()

if (isCiServer) {
    if (!isTravis && !isJenkins) {
        extractAllReportsFromCI()
    }
}

if (project.testDistributionEnabled) {
    buildScan?.tag("TEST_DISTRIBUTION")
}

extractCheckstyleAndCodenarcData()

extractWatchFsData()

project.the<ModuleIdentityExtension>().apply {
    if (logicalBranch.get() != gradleBuildBranch.get()) {
        buildScan?.tag("PRE_TESTED_COMMIT")
    }
}

if ((project.gradle as GradleInternal).services.get(BuildType::class.java) != BuildType.TASKS) {
    buildScan?.tag("SYNC")
}

fun extractCheckstyleAndCodenarcData() {

    val extractCheckstyleBuildScanData by tasks.registering(ExtractCheckstyleBuildScanData::class) {
        rootDir.set(layout.projectDirectory)
        buildScanExt = buildScan
    }

    val extractCodeNarcBuildScanData by tasks.registering(ExtractCodeNarcBuildScanData::class) {
        rootDir.set(layout.projectDirectory)
        buildScanExt = buildScan
    }

    allprojects {
        tasks.withType<Checkstyle>().configureEach {
            finalizedBy(extractCheckstyleBuildScanData)
            extractCheckstyleBuildScanData {
                xmlOutputs.from(reports.xml.outputLocation)
            }
        }
        tasks.withType<CodeNarc>().configureEach {
            finalizedBy(extractCodeNarcBuildScanData)
            extractCodeNarcBuildScanData {
                xmlOutputs.from(reports.xml.outputLocation)
            }
        }
    }
}

fun isEc2Agent() = InetAddress.getLocalHost().hostName.startsWith("ip-")

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
    if (isCodeQl) {
        buildScan {
            tag("CODEQL")
        }
    }
}

fun BuildScanExtension.whenEnvIsSet(envName: String, action: BuildScanExtension.(envValue: String) -> Unit) {
    val envValue: String? = environmentVariable(envName).orNull
    if (!envValue.isNullOrEmpty()) {
        action(envValue)
    }
}

fun Project.extractWatchFsData() {
    val listenerManager = gradle.serviceOf<BuildOperationListenerManager>()
    buildScan?.background {
        listenerManager.addListener(FileSystemWatchingBuildOperationListener(listenerManager, this))
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
                    buildScan.value("watchFsStateInvalidatedAtStart", result.isStateInvalidatedAtStartOfBuild.toString())
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
    val teamCityServerUrl = buildServerUrl.orNull ?: return
    val capturedReportingTypes = listOf("html") // can add xml, text, junitXml if wanted
    val basePath = "$teamCityServerUrl/repository/download/${buildConfigurationId.get()}/${buildId.get()}:id/.teamcity/gradle-logs"

    gradle.taskGraph.afterTask {
        if (state.failure != null && this is Reporting<*>) {
            this.reports.filter { it.name in capturedReportingTypes && it.required.get() && it.outputLocation.get().asFile.exists() }
                .forEach { report ->
                    val linkName = "${this::class.java.simpleName.split("_")[0]} Report ($path)" // Strip off '_Decorated' addition to class names
                    // see: ciReporting.gradle
                    val reportPath =
                        if (report.outputLocation.get().asFile.isDirectory) "report-${project.name}-${report.outputLocation.get().asFile.name}.zip"
                        else "report-${project.name}-${report.outputLocation.get().asFile.parentFile.name}-${report.outputLocation.get().asFile.name}"
                    val reportLink = "$basePath/$reportPath"
                    buildScan?.link(linkName, reportLink)
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
