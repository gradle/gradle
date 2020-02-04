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

import org.gradle.StartParameter
import org.gradle.api.InvalidUserDataException
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileContents
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Optional
import org.gradle.build.BuildReceipt
import org.gradle.gradlebuild.BuildEnvironment
import org.gradle.gradlebuild.BuildEnvironment.CI_ENVIRONMENT_VARIABLE
import org.gradle.internal.Cast
import org.gradle.kotlin.dsl.*
import org.gradle.plugins.buildtypes.BuildType
import java.text.SimpleDateFormat
import java.util.Date


class BuildVersionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        require(project === project.rootProject)
        project.setBuildVersion()
    }
}


private
fun Project.setBuildVersion() {

    val isPromotionBuild = isPromotionBuild()
    if (isPromotionBuild) {
        logger.logStartParameter(gradle.startParameter)
    }

    val finalRelease: Any? by project
    val rcNumber: String? by project
    val milestoneNumber: String? by project
    if ((finalRelease != null && rcNumber != null) ||
        (finalRelease != null && milestoneNumber != null) ||
        (rcNumber != null && milestoneNumber != null)) {
        throw InvalidUserDataException(
            "Cannot set any combination of milestoneNumber, rcNumber and finalRelease " +
                "at the same time"
        )
    }

    val versionQualifier: String? by project
    val isSnapshot = finalRelease == null && rcNumber == null && milestoneNumber == null
    val isFinalRelease = finalRelease != null
    val baseVersion = rootProject.trimmedContentsOfFile("version.txt")

    val buildTimestamp = buildTimestamp()
    val versionNumber = when {
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
            "$baseVersion-$versionQualifier-${buildTimestamp.get()}"
        }
        else -> {
            "$baseVersion-${buildTimestamp.get()}"
        }
    }

    project.version = versionNumber

    registerBuildReceiptTask(versionNumber, baseVersion, isSnapshot, buildTimestamp)

    if (isPromotionBuild) {
        logger.logBuildVersion(versionNumber, baseVersion, isSnapshot, buildTimestamp.get())
    }

    extensions.add(
        "buildVersion",
        BuildVersion(baseVersion, isSnapshot)
    )
}


private
fun Project.registerBuildReceiptTask(
    versionNumber: String,
    baseVersion: String,
    isSnapshot: Boolean,
    buildTimestamp: Provider<String>
) {
    tasks {
        val determineCommitId by registering(DetermineCommitId::class)

        @Suppress("unused_variable")
        val createBuildReceipt by registering(BuildReceipt::class) {
            this.versionNumber.set(versionNumber)
            this.baseVersion.set(baseVersion)
            this.isSnapshot.set(isSnapshot)
            this.buildTimestampFrom(buildTimestamp)
            this.commitId.set(determineCommitId.get().determinedCommitId)
            this.destinationDir = rootProject.buildDir
        }
    }
}


private
fun Logger.logStartParameter(startParameter: StartParameter) {
    lifecycle(
        "Invocation tasks: ${startParameter.taskNames}\n" +
            "Invocation properties: ${startParameter.projectProperties}"
    )
}


private
fun Logger.logBuildVersion(
    versionNumber: String,
    baseVersion: String,
    isSnapshot: Boolean,
    buildTimestamp: String
) {
    lifecycle(
        "Version: $versionNumber " +
            "(base version: $baseVersion," +
            " timestamp: $buildTimestamp," +
            " snapshot: $isSnapshot)"
    )
    if (BuildEnvironment.isCiServer) {
        lifecycle(
            "##teamcity[buildStatus text='{build.status.text}, Promoted version $versionNumber']"
        )
    }
}


/**
 * Returns the trimmed contents of the file at the given [path] after
 * marking the file as a build logic input.
 */
private
fun Project.trimmedContentsOfFile(path: String): String =
    fileContentsOf(path).asText.get().trim()


private
fun Project.fileContentsOf(path: String): FileContents =
    providers.fileContents(layout.projectDirectory.file(path))


private
fun Project.buildTimestamp(): Provider<String> =
    providers.of(BuildTimestampValueSource::class) {
        parameters {
            buildTimestampFromBuildReceipt.set(
                buildTimestampFromBuildReceipt()
            )
            buildTimestampFromGradleProperty.set(
                gradleProperty("buildTimestamp")
                    .uncheckedCast<Provider<String>>()
            )
            runningOnCi.set(
                providers.environmentVariable(CI_ENVIRONMENT_VARIABLE)
                    .presence()
            )
            runningInstallTask.set(provider {
                isRunningInstallTask()
            })
        }
    }


private
fun Project.buildTimestampFromBuildReceipt(): Provider<String> =
    providers.of(BuildTimestampFromBuildReceipt::class) {
        parameters {
            ignoreIncomingBuildReceipt.set(
                gradleProperty("ignoreIncomingBuildReceipt")
                    .presence()
            )
            buildReceiptFileContents.set(
                layout.projectDirectory
                    .dir("incoming-distributions")
                    .file(BuildReceipt.BUILD_RECEIPT_FILE_NAME)
                    .let(providers::fileContents)
                    .asText
            )
        }
    }


abstract class BuildTimestampValueSource : ValueSource<String, BuildTimestampValueSource.Parameters> {

    interface Parameters : ValueSourceParameters {

        @get:Optional
        val buildTimestampFromBuildReceipt: Property<String>

        @get:Optional
        val buildTimestampFromGradleProperty: Property<String>

        val runningOnCi: Property<Boolean>

        val runningInstallTask: Property<Boolean>
    }

    override fun obtain(): String? = parameters.run {

        val buildTimestampFromReceipt = buildTimestampFromBuildReceipt.orNull
        if (buildTimestampFromReceipt != null) {
            println("Using timestamp from incoming build receipt: $buildTimestampFromReceipt")
            return buildTimestampFromReceipt
        }

        val timestampFormat = BuildReceipt.createTimestampDateFormat()
        val buildTimestampFromProperty = buildTimestampFromGradleProperty.orNull
        val buildTime = when {
            buildTimestampFromProperty != null -> {
                timestampFormat.parse(buildTimestampFromProperty)
            }
            runningInstallTask.get() || runningOnCi.get() -> {
                Date()
            }
            else -> {
                Date().withoutTime()
            }
        }
        return timestampFormat.format(buildTime)
    }
}


abstract class BuildTimestampFromBuildReceipt : ValueSource<String, BuildTimestampFromBuildReceipt.Parameters> {

    interface Parameters : ValueSourceParameters {

        val ignoreIncomingBuildReceipt: Property<Boolean>

        @get:Optional
        val buildReceiptFileContents: Property<String>
    }

    override fun obtain(): String? = parameters.run {
        buildReceiptString()
            ?.let(BuildReceipt::readBuildReceiptFromString)
            ?.let { buildReceipt ->
                buildReceipt["buildTimestamp"] as String
            }
    }

    private
    fun Parameters.buildReceiptString(): String? =
        if (ignoreIncomingBuildReceipt.get()) null
        else buildReceiptFileContents.orNull
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


private
fun Project.isPromotionBuild(): Boolean =
    buildTypes["promotionBuild"].active


private
val Project.buildTypes
    get() = extensions.getByName<NamedDomainObjectContainer<BuildType>>("buildTypes")


// TODO: move to ProviderFactory and make it a build logic input
private
fun Project.gradleProperty(propertyName: String): Provider<Any> =
    provider { findProperty(propertyName) }


/**
 * Creates a [Provider] that returns `true` when this [Provider] has a value
 * and `false` otherwise. The returned [Provider] always has a value.
 * @see Provider.isPresent
 */
private
fun <T> Provider<T>.presence(): Provider<Boolean> =
    map { true }.orElse(false)


private
fun <T> Any.uncheckedCast(): T =
    Cast.uncheckedNonnullCast(this)
