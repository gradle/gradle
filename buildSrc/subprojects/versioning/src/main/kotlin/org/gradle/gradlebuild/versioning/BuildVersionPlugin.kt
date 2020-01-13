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

package org.gradle.gradlebuild.versioning

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileContents
import org.gradle.build.BuildReceipt
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.kotlin.dsl.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date


class BuildVersionPlugin : Plugin<Project> {
    override fun apply(project: Project) = project.setBuildVersionProperties()
}


private
fun Project.setBuildVersionProperties() {
    val milestoneNumber: String? by project
    val rcNumber: String? by project
    val finalRelease: Any? by project
    val versionQualifier: String? by project
    if ((milestoneNumber != null && rcNumber != null) ||
        (rcNumber != null && finalRelease != null) ||
        (milestoneNumber != null && finalRelease != null)) {
        throw InvalidUserDataException(
            "Cannot set any combination of milestoneNumber, rcNumber and finalRelease " +
                "at the same time"
        )
    }

    val isSnapshot = finalRelease == null && rcNumber == null && milestoneNumber == null
    val isFinalRelease = finalRelease != null
    val baseVersion = rootProject.trimmedContentsOfFile("version.txt")
    val buildTimestamp = computeBuildTimestamp()
    project.version = when {
        isFinalRelease -> {
            baseVersion
        }
        rcNumber != null -> {
            "$baseVersion-rc-$rcNumber"
        }
        milestoneNumber != null -> {
            "$baseVersion-milestone-$milestoneNumber"
        }
        versionQualifier != null -> {
            "$baseVersion-$versionQualifier-$buildTimestamp"
        }
        else -> {
            "$baseVersion-$buildTimestamp"
        }
    }
    extra.let { ext ->
        ext["milestoneNumber"] = milestoneNumber?.toInt()
        ext["rcNumber"] = rcNumber?.toInt()
        ext["finalRelease"] = isFinalRelease
        ext["versionQualifier"] = versionQualifier
        ext["isSnapshot"] = isSnapshot
        ext["baseVersion"] = baseVersion
        ext["buildTimestamp"] = buildTimestamp
    }
}


/**
 * Returns the trimmed contents of the file at the given [path] after
 * marking the file as a build logic input.
 */
private
fun Project.trimmedContentsOfFile(path: String): String =
    fileContentsOf(path).asText.map { it.trim() }.get()


private
fun Project.fileContentsOf(path: String): FileContents =
    providers.fileContents(layout.projectDirectory.file(path))


private
fun Project.computeBuildTimestamp(): String {
    val ignoreIncomingBuildReceipt: Any? by project
    val incomingBuildReceiptDir = file("incoming-distributions")
    if (ignoreIncomingBuildReceipt == null && File(incomingBuildReceiptDir, BuildReceipt.BUILD_RECEIPT_FILE_NAME).exists()) {
        val incomingDistributionsBuildReceipt = BuildReceipt.readBuildReceipt(incomingBuildReceiptDir)
        val buildTimestamp = incomingDistributionsBuildReceipt["buildTimestamp"] as String
        println("Using timestamp from incoming build receipt: $buildTimestamp")
        return buildTimestamp
    } else {
        val timestampFormat = BuildReceipt.createTimestampDateFormat()
        val buildTimestamp: String? by project
        val buildTime = when {
            buildTimestamp != null -> {
                timestampFormat.parse(buildTimestamp)
            }
            BuildEnvironment.isCiServer || isRunningInstallTask() -> {
                Date()
            }
            else -> {
                Date().withoutTime()
            }
        }
        return timestampFormat.format(buildTime)
    }
}


private
fun Project.isRunningInstallTask() =
    listOf("install", "installAll")
        .flatMap { listOf(":$it", it) }
        .any(gradle.startParameter.taskNames::contains)


private
fun Date.withoutTime(): Date = SimpleDateFormat("yyyy-MM-dd").run {
    parse(format(this@withoutTime))
}
