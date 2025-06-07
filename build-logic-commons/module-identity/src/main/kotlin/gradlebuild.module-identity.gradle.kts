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
import gradlebuild.identity.extension.GradleModuleExtension
import gradlebuild.identity.extension.ReleasedVersionsDetails
import gradlebuild.identity.provider.BuildTimestampFromBuildReceiptValueSource
import gradlebuild.identity.provider.BuildTimestampValueSource
import gradlebuild.identity.tasks.BuildReceipt
import java.util.Optional

plugins {
    `java-base`
}

val gradleModule = extensions.create<GradleModuleExtension>(GradleModuleExtension.NAME).apply {
    targetRuntimes {
        // By default, assume a library targets only the daemon
        // TODO: Eventually, all projects should explicitly declare their target platform(s)
        usedInWorkers = false
        usedInClient = false
        usedInDaemon = true
    }

    // TODO: Most of these properties are the same across projects. We should
    // compute these at the settings-level instead of the project-level.
    identity {
        baseName = "gradle-$name"
        buildTimestamp = buildTimestamp()
        promotionBuild = isPromotionBuild

        val finalReleaseSuffix = buildFinalRelease.map { "" }
        val rcSuffix = buildRcNumber.map { "-rc-$it" }
        val milestoneSuffix = buildMilestoneNumber.map { "-milestone-$it" }
        val buildVersionQualifierSuffix = buildVersionQualifier.zip(buildTimestamp) { buildVersion, timestamp -> "-$buildVersion-$timestamp" }
        val buildTimestampSuffix = buildTimestamp.map { "-$it" }

        val specifiedSuffix = atMostOneOf(finalReleaseSuffix, rcSuffix, milestoneSuffix)
        val computedSuffix = specifiedSuffix
            .orElse(buildVersionQualifierSuffix)
            .orElse(buildTimestampSuffix)

        val baseVersion = trimmedContentsOfFile("version.txt")
        version = baseVersion.zip(computedSuffix) { base, suffix -> GradleVersion.version("$base$suffix") }
        snapshot = specifiedSuffix.map { false }.orElse(true)
        releasedVersions = version.map {
            ReleasedVersionsDetails(
                it.baseVersion,
                releasedVersionsFile()
            )
        }
    }
}

group = "org.gradle"
version = gradleModule.identity.version.get().version

/**
 * Returns the trimmed contents of the file at the given [path] after
 * marking the file as a build logic input.
 */
fun Project.trimmedContentsOfFile(path: String): Provider<String> =
    providers.fileContents(repoRoot().file(path)).asText.map { it.trim() }

// TODO Simplify the buildTimestamp() calculation if possible
fun Project.buildTimestamp(): Provider<String> =
    providers.of(BuildTimestampValueSource::class) {
        parameters {
            buildTimestampFromBuildReceipt = buildTimestampFromBuildReceipt()
            buildTimestampFromGradleProperty = buildTimestamp
            runningOnCi = buildRunningOnCi
            runningInstallTask = provider { isRunningInstallTask() }
            runningDocsTestTask = provider { isRunningDocsTestTask() }
            enableConfigurationCacheForDocsTests = providers.gradleProperty("enableConfigurationCacheForDocsTests").map { it.toBoolean() }
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


/**
 * Returns a new provider that takes its value from at most one
 * of the given providers. If no input provider is present, the output
 * provider will not be present. If more than one input provider
 * has a value specified, the resulting provider will throw an
 * exception when queried.
 */
fun <T: Any> atMostOneOf(vararg providers: Provider<T>): Provider<T> {
    return providers.map { provider ->
        provider.map {
            Optional.of(it)
        }.orElse(
            Optional.empty<T>()
        )
    }.reduce { acc, next ->
        acc.zip(next) { left, right ->
            when {
                left.isPresent -> {
                    require(!right.isPresent) {
                        "Expected at most one provider to be present"
                    }
                    left
                }
                else -> right
            }
        }
    }.map { it.orElse(null) }
}
