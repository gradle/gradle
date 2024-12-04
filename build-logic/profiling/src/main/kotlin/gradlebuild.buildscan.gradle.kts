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

import com.gradle.develocity.agent.gradle.DevelocityConfiguration
import com.gradle.develocity.agent.gradle.scan.BuildScanConfiguration
import gradlebuild.basics.BuildEnvironment.isCiServer
import gradlebuild.basics.BuildEnvironment.isCodeQl
import gradlebuild.basics.BuildEnvironment.isGhActions
import gradlebuild.basics.BuildEnvironment.isTeamCity
import gradlebuild.basics.buildBranch
import gradlebuild.basics.environmentVariable
import gradlebuild.basics.isPromotionBuild
import gradlebuild.basics.isRetryBuild
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

// We can not use plugin {} because this is registered by a settings plugin.
// We do 'findByType' to make this script compile in pre-compiled script compilation.
// TODO to avoid the above, turn this into a settings plugin
val develocity = extensions.findByType<DevelocityConfiguration>()
val buildScan = develocity?.buildScan
inline fun buildScan(configure: BuildScanConfiguration.() -> Unit) {
    buildScan?.apply(configure)
}

develocity?.extractCiData()
buildScan?.extractWatchFsData()

buildScan {
    val testDistributionEnabled = project.testDistributionEnabled
    val predictiveTestSelectionEnabled = project.predictiveTestSelectionEnabled
    val logicalBranch = project.logicalBranch
    val buildBranch = project.buildBranch

    // TODO(https://github.com/gradle/gradle/issues/25474) background would be better, but it makes branch an input to CC because of the bug.
    buildFinished {
        if (testDistributionEnabled) {
            tag("TEST_DISTRIBUTION")
        }
        if (predictiveTestSelectionEnabled.getOrElse(false)) {
            tag("PTS")
        }
        if (logicalBranch.orNull != buildBranch.orNull) {
            tag("PRE_TESTED_COMMIT")
        }
    }
}

if ((project.gradle as GradleInternal).services.get(BuildType::class.java) != BuildType.TASKS) {
    buildScan?.tag("SYNC")
}

fun DevelocityConfiguration.extractCiData() {
    fun isEc2Agent() = InetAddress.getLocalHost().hostName.startsWith("ip-")

    fun String.urlEncode() = URLEncoder.encode(this, Charsets.UTF_8.name())

    fun DevelocityConfiguration.customValueSearchUrl(search: Map<String, String>): String {
        val query = search.map { (name, value) ->
            "search.names=${name.urlEncode()}&search.values=${value.urlEncode()}"
            // "search.names=${urlEncode(name)}&search.values=${urlEncode(value)}"
        }.joinToString("&")

        return "${server.get()}/scans?$query"
    }

    fun BuildScanConfiguration.setCompileAllScanSearch(commitId: String) {
        link("CI CompileAll Scan", customValueSearchUrl(mapOf("gitCommitId" to commitId)) + "&search.tags=CompileAll")
    }

    if (isCiServer) {
        buildScan {
            val execOps = serviceOf<ExecOperations>()
            background {
                setCompileAllScanSearch(execOps.execAndGetStdoutIgnoringError("git", "rev-parse", "--verify", "HEAD"))
            }
            if (isEc2Agent()) {
                tag("EC2")
                safeAddSystemPropertyToBuildScan(this, "EC2AmiId", "ec2.ami-id")
                safeAddSystemPropertyToBuildScan(this, "EC2InstanceType", "ec2.instance-type")
                safeAddSystemPropertyToBuildScan(this, "EC2InstanceId", "ec2.instance-id")
                safeAddSystemPropertyToBuildScan(this, "EC2CloudProfileId", "cloud.profile_id")
            }
            if (isGhActions) {
                tag("GH_ACTION")
            }
            whenEnvIsSet("BUILD_TYPE_ID") { buildType ->
                val tcBuildTypeName = "tcBuildType"
                value(tcBuildTypeName, buildType)
                link("Build Type Scans", customValueSearchUrl(mapOf(tcBuildTypeName to buildType)))
            }
            System.getProperty("buildScan.PartOf")?.let {
                it.toString().split(",").forEach { partOf ->
                    value("PartOf", partOf)
                }
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
            buildFinished {
                println("##teamcity[setParameter name='env.GRADLE_RUNNER_FINISHED' value='true']")
                if (failures.isEmpty() && isRetryBuild) {
                    println("##teamcity[buildStatus status='SUCCESS' text='Retried build succeeds']")
                }
            }
        }
    }
}

fun Project.safeAddSystemPropertyToBuildScan(buildScan: BuildScanConfiguration, customValueName: String, propertyName: String) {
    val propertyValue = findProperty(propertyName)
    if (propertyValue != null) {
        buildScan.value(customValueName, propertyValue.toString())
    }
}

fun BuildScanConfiguration.whenEnvIsSet(envName: String, action: BuildScanConfiguration.(envValue: String) -> Unit) {
    val envValue: String? = environmentVariable(envName).orNull
    if (!envValue.isNullOrEmpty()) {
        action(envValue)
    }
}

fun BuildScanConfiguration.extractWatchFsData() {
    val listenerManager = gradle.serviceOf<BuildOperationListenerManager>()
    background {
        listenerManager.addListener(FileSystemWatchingBuildOperationListener(listenerManager, this))
    }
}

open class FileSystemWatchingBuildOperationListener(private val buildOperationListenerManager: BuildOperationListenerManager, private val buildScan: BuildScanConfiguration) : BuildOperationListener {

    override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent) = Unit

    override fun progress(operationIdentifier: OperationIdentifier, progressEvent: OperationProgressEvent) = Unit

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
