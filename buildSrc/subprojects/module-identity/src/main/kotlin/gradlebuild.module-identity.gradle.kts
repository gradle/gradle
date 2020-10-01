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

import gradlebuild.basics.BuildEnvironment
import gradlebuild.identity.extension.ModuleIdentityExtension
import gradlebuild.identity.provider.BuildTimestampFromBuildReceiptValueSource
import gradlebuild.identity.provider.BuildTimestampValueSource
import gradlebuild.identity.tasks.BuildReceipt
import gradlebuild.identity.extension.ReleasedVersionsDetails
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.GradleVersion
import java.io.ByteArrayOutputStream

plugins {
    `java-base`
}

val moduleIdentity = extensions.create<ModuleIdentityExtension>("moduleIdentity")

group = "org.gradle"
version = collectVersionDetails(moduleIdentity)

fun Project.collectVersionDetails(moduleIdentity: ModuleIdentityExtension): String {
    moduleIdentity.baseName.convention("gradle-$name")

    val baseVersion = rootProject.trimmedContentsOfFile("version.txt")
        ?: return "" // Need to handle the case where the file does not exist and wheere 'gradleProperty' is not yet accesible for script compilation

    val finalRelease = gradleProperty("finalRelease")
    val rcNumber = gradleProperty("rcNumber")
    val milestoneNumber = gradleProperty("milestoneNumber")

    if (
        (finalRelease.isPresent && rcNumber.isPresent) ||
        (finalRelease.isPresent && milestoneNumber.isPresent) ||
        (rcNumber.isPresent && milestoneNumber.isPresent)
    ) {
        throw InvalidUserDataException("Cannot set any combination of milestoneNumber, rcNumber and finalRelease at the same time")
    }

    val versionQualifier = gradleProperty("versionQualifier")
    val isFinalRelease = finalRelease.isPresent

    val buildTimestamp = buildTimestamp()
    val versionNumber = when {
        isFinalRelease -> {
            baseVersion
        }
        rcNumber.isPresent -> {
            "$baseVersion-rc-${rcNumber.get()}"
        }
        milestoneNumber.isPresent -> {
            "$baseVersion-milestone-${milestoneNumber.get()}"
        }
        versionQualifier.isPresent -> {
            "$baseVersion-${versionQualifier.get()}-${buildTimestamp.get()}"
        }
        else -> {
            "$baseVersion-${buildTimestamp.get()}"
        }
    }

    val isSnapshot = !finalRelease.isPresent && !rcNumber.isPresent && !milestoneNumber.isPresent

    moduleIdentity.version.convention(GradleVersion.version(versionNumber))
    moduleIdentity.snapshot.convention(isSnapshot)
    moduleIdentity.buildTimestamp.convention(buildTimestamp)
    moduleIdentity.promotionBuild.convention(isPromotionBuild())

    moduleIdentity.gradleBuildBranch.convention(environmentVariable(BuildEnvironment.BUILD_BRANCH).orElse(currentGitBranch()))
    moduleIdentity.gradleBuildCommitId.convention(
        environmentVariable(BuildEnvironment.BUILD_COMMIT_ID)
            .orElse(gradleProperty("promotionCommitId"))
            .orElse(environmentVariable(BuildEnvironment.BUILD_VCS_NUMBER))
            .orElse(currentGitCommit())
    )

    moduleIdentity.releasedVersions.set(
        provider {
            ReleasedVersionsDetails(
                moduleIdentity.version.forUseAtConfigurationTime().get().baseVersion,
                rootProject.layout.projectDirectory.file("released-versions.json")
            )
        }
    )

    return versionNumber
}

/**
 * Is a promotion build task called?
 */
fun isPromotionBuild(): Boolean = gradle.startParameter.taskNames.contains("promotionBuild")

/**
 * Returns the trimmed contents of the file at the given [path] after
 * marking the file as a build logic input.
 */
fun Project.trimmedContentsOfFile(path: String): String? =
    providers.fileContents(rootProject.layout.projectDirectory.file(path)).asText.forUseAtConfigurationTime().orNull?.trim()

fun Project.environmentVariable(variableName: String): Provider<String> =
    providers.environmentVariable(variableName).forUseAtConfigurationTime()

fun Project.gradleProperty(propertyName: String): Provider<String> =
    providers.gradleProperty(propertyName).forUseAtConfigurationTime()

/**
 * We use command line Git instead of JGit, because JGit's [Repository.resolve] does not work with worktrees.
 */
fun currentGitBranch() = git(layout.projectDirectory, "rev-parse", "--abbrev-ref", "HEAD")
fun currentGitCommit() = git(layout.projectDirectory, "rev-parse", "HEAD")
fun git(checkoutDir: Directory, vararg args: String): Provider<String> = provider {
    val execOutput = ByteArrayOutputStream()
    val execResult = exec {
        workingDir = checkoutDir.asFile
        isIgnoreExitValue = true
        commandLine = listOf("git", *args)
        if (OperatingSystem.current().isWindows) {
            commandLine = listOf("cmd", "/c") + commandLine
        }
        standardOutput = execOutput
    }
    when {
        execResult.exitValue == 0 -> String(execOutput.toByteArray()).trim()
        checkoutDir.asFile.resolve(".git/HEAD").exists() -> {
            // Read commit id directly from filesystem
            val headRef = checkoutDir.asFile.resolve(".git/HEAD").readText()
                .replace("ref: ", "").trim()
            checkoutDir.asFile.resolve(".git/$headRef").readText().trim()
        }
        else -> "<unknown>" // It's a source distribution, we don't know.
    }
}

// TODO Simplify the buildTimestamp() calculation if possible
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
                environmentVariable(BuildEnvironment.CI_ENVIRONMENT_VARIABLE).presence()
            )
            runningInstallTask.set(
                provider { isRunningInstallTask() }
            )
        }
    }.forUseAtConfigurationTime()


fun Project.buildTimestampFromBuildReceipt(): Provider<String> =
    providers.of(BuildTimestampFromBuildReceiptValueSource::class) {
        parameters {
            ignoreIncomingBuildReceipt.set(
                gradleProperty("ignoreIncomingBuildReceipt")
                    .presence()
            )
            buildReceiptFileContents.set(
                rootProject.layout.projectDirectory
                    .dir("incoming-distributions")
                    .file(BuildReceipt.buildReceiptFileName)
                    .let(providers::fileContents)
                    .asText
                    .forUseAtConfigurationTime()
            )
        }
    }.forUseAtConfigurationTime()


fun isRunningInstallTask() =
    listOf("install", "installAll")
        .flatMap { listOf(":distributions-full:$it", "distributions-full:$it", it) }
        .any(gradle.startParameter.taskNames::contains)

/**
 * Creates a [Provider] that returns `true` when this [Provider] has a value
 * and `false` otherwise. The returned [Provider] always has a value.
 * @see Provider.isPresent
 */
fun <T> Provider<T>.presence(): Provider<Boolean> =
    map { true }.orElse(false)
