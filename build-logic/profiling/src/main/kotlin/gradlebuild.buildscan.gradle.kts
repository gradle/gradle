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
import gradlebuild.basics.BuildEnvironment.isTeamCity
import gradlebuild.basics.BuildEnvironment.isTravis
import gradlebuild.basics.buildBranch
import gradlebuild.basics.environmentVariable
import gradlebuild.basics.isPromotionBuild
import gradlebuild.basics.kotlindsl.execAndGetStdoutIgnoringError
import gradlebuild.basics.logicalBranch
import gradlebuild.basics.predictiveTestSelectionEnabled
import gradlebuild.basics.testDistributionEnabled
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
    id("gradlebuild.collect-failed-tasks")
    id("gradlebuild.cache-miss-monitor")
    id("gradlebuild.module-identity")
}

val serverUrl = "https://ge.gradle.org"
val gitCommitName = "gitCommitId"
val gitBranchCustomValueName = "gitBranchName"
val tcBuildTypeName = "tcBuildType"

// We can not use plugin {} because this is registered by a settings plugin.
// We do 'findByType' to make this script compile in pre-compiled script compilation.
// TODO to avoid the above, turn this into a settings plugin
val buildScan = extensions.findByType<BuildScanExtension>()
inline fun buildScan(configure: BuildScanExtension.() -> Unit) {
    buildScan?.apply(configure)
}

extractCiData()

if (project.testDistributionEnabled) {
    buildScan?.tag("TEST_DISTRIBUTION")
}

if (project.predictiveTestSelectionEnabled.orNull == true) {
    buildScan?.tag("PTS")
}

extractWatchFsData()

if (logicalBranch.orNull != buildBranch.orNull) {
    buildScan?.tag("PRE_TESTED_COMMIT")
}

if ((project.gradle as GradleInternal).services.get(BuildType::class.java) != BuildType.TASKS) {
    buildScan?.tag("SYNC")
}

fun isEc2Agent() = InetAddress.getLocalHost().hostName.startsWith("ip-")

fun Project.extractCiData() {
    if (isCiServer) {
        buildScan {
            background {
                setCompileAllScanSearch(execAndGetStdoutIgnoringError("git", "rev-parse", "--verify", "HEAD"))
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
            whenEnvIsSet("BUILD_BRANCH") { gitBranchName ->
                link("Scans On Branch $gitBranchName", customValueSearchUrl(mapOf(gitBranchCustomValueName to gitBranchName)))
            }
        }
    }
    if (isCodeQl) {
        buildScan {
            tag("CODEQL")
        }
    }
    if (isTeamCity && !isPromotionBuild) {
        // don't overwrite the nightly version in promotion build
        buildScan {
            buildScanPublished {
                println("##teamcity[buildStatus text='{build.status.text}: ${this.buildScanUri}']")
            }
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
