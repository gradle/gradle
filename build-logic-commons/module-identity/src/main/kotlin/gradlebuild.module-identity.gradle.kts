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

import gradlebuild.basics.buildFinalRelease
import gradlebuild.basics.buildMilestoneNumber
import gradlebuild.basics.buildRcNumber
import gradlebuild.basics.buildRunningOnCi
import gradlebuild.basics.buildTimestamp
import gradlebuild.basics.buildVersionQualifier
import gradlebuild.basics.ignoreIncomingBuildReceipt
import gradlebuild.basics.isPromotionBuild
import gradlebuild.basics.releasedVersionsFile
import gradlebuild.basics.repoRoot
import gradlebuild.identity.extension.ModuleIdentityExtension
import gradlebuild.identity.extension.ReleasedVersionsDetails
import gradlebuild.identity.provider.BuildTimestampFromBuildReceiptValueSource
import gradlebuild.identity.provider.BuildTimestampValueSource
import gradlebuild.identity.tasks.BuildReceipt

plugins {
    `java-base`
}

val moduleIdentity = extensions.create<ModuleIdentityExtension>("moduleIdentity")

group = "org.gradle"
version = collectVersionDetails(moduleIdentity)

fun Project.collectVersionDetails(moduleIdentity: ModuleIdentityExtension): String {
    moduleIdentity.baseName.convention("gradle-$name")

    val baseVersion = trimmedContentsOfFile("version.txt")

    val finalRelease = buildFinalRelease
    val rcNumber = buildRcNumber
    val milestoneNumber = buildMilestoneNumber

    if (
        (buildFinalRelease.isPresent && buildRcNumber.isPresent) ||
        (buildFinalRelease.isPresent && buildMilestoneNumber.isPresent) ||
        (buildRcNumber.isPresent && buildMilestoneNumber.isPresent)
    ) {
        throw InvalidUserDataException("Cannot set any combination of milestoneNumber, rcNumber and finalRelease at the same time")
    }

    val versionQualifier = buildVersionQualifier
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
    moduleIdentity.promotionBuild.convention(isPromotionBuild)

    moduleIdentity.releasedVersions = provider {
        ReleasedVersionsDetails(
            moduleIdentity.version.get().baseVersion,
            releasedVersionsFile()
        )
    }

    return versionNumber
}

/**
 * Returns the trimmed contents of the file at the given [path] after
 * marking the file as a build logic input.
 */
fun Project.trimmedContentsOfFile(path: String): String =
    providers.fileContents(repoRoot().file(path)).asText.get().trim()

// TODO Simplify the buildTimestamp() calculation if possible
fun Project.buildTimestamp(): Provider<String> =
    providers.of(BuildTimestampValueSource::class) {
        parameters {
            buildTimestampFromBuildReceipt = buildTimestampFromBuildReceipt()
            buildTimestampFromGradleProperty = buildTimestamp
            runningOnCi = buildRunningOnCi
            runningInstallTask = provider { isRunningInstallTask() }
            runningDocsTestTask = provider { isRunningDocsTestTask() }
        }
    }


fun Project.buildTimestampFromBuildReceipt(): Provider<String> =
    providers.of(BuildTimestampFromBuildReceiptValueSource::class) {
        parameters {
            ignoreIncomingBuildReceipt = project.ignoreIncomingBuildReceipt
            buildReceiptFileContents = repoRoot()
                .dir("incoming-distributions")
                .file(BuildReceipt.buildReceiptFileName)
                .let(providers::fileContents)
                .asText
        }
    }


fun isRunningInstallTask() =
    listOf("install", "installAll")
        .flatMap { listOf(":distributions-full:$it", "distributions-full:$it", it) }
        .any(gradle.startParameter.taskNames::contains)

fun isRunningDocsTestTask() =
    setOf(":docs:docsTest", "docs:docsTest")
        .any(gradle.startParameter.taskNames::contains)
