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

import GitInformationExtension
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.gradle.api.Describable
import org.gradle.api.InvalidUserDataException
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
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date


class BuildVersionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        require(project === project.rootProject)
        project.setBuildVersion()
        project.setGitInformation()
    }
}


private
fun Project.setGitInformation() {
    val gitInfo by lazy { resolveGitInfo() }
    extensions.create<GitInformationExtension>(
        "gitInfo",
        environmentVariable(BuildEnvironment.BUILD_BRANCH)
            .orElse(providers.provider { gitInfo.branch }),
        environmentVariable(BuildEnvironment.BUILD_COMMIT_ID)
            .orElse(providers.provider { gitInfo.commitId })
    )
}


private
class LazyGitInformation(
    branch: () -> String,
    commitId: () -> String
) {
    val branch by lazy(branch)
    val commitId by lazy(commitId)
}


private
fun Project.resolveGitInfo(): LazyGitInformation {
    val projectDir = rootProject.projectDir
    val gitDirOrFile = projectDir.resolve(".git")
    return when {
        gitDirOrFile.isFile -> gitWorktreeInfoFor(projectDir)
        else -> gitRepositoryInfoFor(gitDirOrFile)
    }
}


private
fun gitRepositoryInfoFor(gitDir: File): LazyGitInformation =
    FileRepositoryBuilder().setGitDir(gitDir).build().let { repository ->
        LazyGitInformation(
            branch = { repository.branch },
            commitId = { repository.resolve(repository.fullBranch).name }
        )
    }


/**
 * Gets the worktree information by executing the native git utility.
 *
 * This is necessary because jgit's [Repository.resolve] does not work with worktrees.
 */
private
fun Project.gitWorktreeInfoFor(checkoutDir: File) =
    LazyGitInformation(
        branch = { git(checkoutDir, "rev-parse", "--abbrev-ref", "HEAD") },
        commitId = { git(checkoutDir, "rev-parse", "HEAD") }
    )


private
fun Project.setBuildVersion() {
    val finalRelease = gradleProperty("finalRelease").orNull
    val rcNumber = gradleProperty("rcNumber").orNull
    val milestoneNumber = gradleProperty("milestoneNumber").orNull
    if ((finalRelease != null && rcNumber != null) ||
        (finalRelease != null && milestoneNumber != null) ||
        (rcNumber != null && milestoneNumber != null)) {
        throw InvalidUserDataException(
            "Cannot set any combination of milestoneNumber, rcNumber and finalRelease " +
                "at the same time"
        )
    }

    val versionQualifier = gradleProperty("versionQualifier").orNull
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

    allprojects {
        group = "org.gradle"
        version = versionNumber
    }

    registerBuildReceiptTask(versionNumber, baseVersion, isSnapshot, buildTimestamp)

    if (isPromotionBuild()) {
        logger.logBuildVersion(versionNumber, baseVersion, isSnapshot, buildTimestamp.get())
    }

    extensions.add(
        "buildVersion",
        BuildVersion(baseVersion, isSnapshot)
    )
}


private
fun Project.isPromotionBuild(): Boolean = gradle.startParameter.taskNames.contains("promotionBuild")


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
    fileContentsOf(path).asText.forUseAtConfigurationTime().get().trim()


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
            )
            runningOnCi.set(
                environmentVariable(CI_ENVIRONMENT_VARIABLE)
                    .presence()
            )
            runningInstallTask.set(provider {
                isRunningInstallTask()
            })
        }
    }.forUseAtConfigurationTime()


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
                    .forUseAtConfigurationTime()
            )
        }
    }.forUseAtConfigurationTime()


abstract class BuildTimestampValueSource : ValueSource<String, BuildTimestampValueSource.Parameters>, Describable {

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

    override fun getDisplayName(): String =
        "the build timestamp ($timestampSource)"

    private
    val timestampSource: String
        get() = parameters.run {
            when {
                buildTimestampFromBuildReceipt.isPresent -> "from build receipt"
                buildTimestampFromGradleProperty.isPresent -> "from buildTimestamp property"
                runningInstallTask.get() -> "from current time because installing"
                runningOnCi.get() -> "from current time because CI"
                else -> "from current date"
            }
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
        .flatMap { listOf(":distributionsFull:$it", "distributionsFull:$it", it) }
        .any(gradle.startParameter.taskNames::contains)


private
fun Date.withoutTime(): Date = SimpleDateFormat("yyyy-MM-dd").run {
    parse(format(this@withoutTime))
}


private
fun Project.environmentVariable(variableName: String): Provider<String> =
    providers.environmentVariable(variableName).forUseAtConfigurationTime()


private
fun Project.gradleProperty(propertyName: String): Provider<String> =
    providers.gradleProperty(propertyName).forUseAtConfigurationTime()


/**
 * Creates a [Provider] that returns `true` when this [Provider] has a value
 * and `false` otherwise. The returned [Provider] always has a value.
 * @see Provider.isPresent
 */
private
fun <T> Provider<T>.presence(): Provider<Boolean> =
    map { true }.orElse(false)


private
fun Project.git(checkoutDir: File, vararg args: String): String {
    val execOutput = ByteArrayOutputStream()
    exec {
        workingDir = checkoutDir
        commandLine = listOf("git", *args)
        if (OperatingSystem.current().isWindows) {
            commandLine = listOf("cmd", "/c") + commandLine
        }
        standardOutput = execOutput
    }
    return execOutput.toString().trim()
}
