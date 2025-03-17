/*
 * Copyright 2024 the original author or authors.
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

/**
 * TODO: Remove once with Gradle 9.0, used so org.gradle.kotlin.dsl.* is kept
 */
@file:Suppress("UnusedImport")

package gradlebuild.basics

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.os.OperatingSystem
// Using star import to workaround https://youtrack.jetbrains.com/issue/KTIJ-24390
import org.gradle.kotlin.dsl.*
import javax.inject.Inject
/**
 * Used to import assign for Gradle 9.0
 * TODO: Remove once with Gradle 9.0
 */
import org.gradle.kotlin.dsl.*


abstract class BuildEnvironmentService : BuildService<BuildEnvironmentService.Parameters> {

    companion object {
        // Keep in sync with the corresponding constants in `gradlebuild.basics.BuildParams`,
        // which this project cannot depend on (`basics` depends on `build-environment`, not the other way around).
        private
        const val BUILD_TIMESTAMP_PROPERTY = "buildTimestamp"

        private
        const val IGNORE_INCOMING_BUILD_RECEIPT_PROPERTY = "ignoreIncomingBuildReceipt"

        private
        const val ENABLE_CONFIGURATION_CACHE_FOR_DOCS_TESTS_PROPERTY = "enableConfigurationCacheForDocsTests"

        private
        const val CI_ENVIRONMENT_VARIABLE = "CI"
    }

    interface Parameters : BuildServiceParameters {
        val rootProjectDir: DirectoryProperty
        val rootProjectBuildDir: DirectoryProperty

        /**
         * Whether `install`/`installAll` was requested on the command line.
         *
         * Computed from `StartParameter` at settings time, since a build service cannot see it.
         */
        val runningInstallTask: Property<Boolean>

        /**
         * Whether `:docs:docsTest` was requested on the command line.
         */
        val runningDocsTestTask: Property<Boolean>
    }

    @get:Inject
    abstract val providers: ProviderFactory

    val gitCommitId = git("rev-parse", "HEAD")
    val gitBranch = git("rev-parse", "--abbrev-ref", "HEAD")
    val scriptTemplateCommitId = git("log", "-1", "--format=%H", "--", "platforms/jvm/plugins-application/src/main/resources/org/gradle/api/internal/plugins/unixStartScript.txt")

    /**
     * The timestamp identifying the distribution built by this build, shared by every project.
     *
     * This service is a build-scoped singleton, so the value source below is instantiated once
     * per build and its value is memoized. That matters: when `install`, `:docs:docsTest` or CI
     * selects the current instant rather than local midnight, obtaining the value source more
     * than once yields *different* timestamps. Creating it per project (as this used to be) made
     * the projects of a single build disagree about the version of the distribution being built,
     * so e.g. the installed distribution and the tests exercising it ended up on different versions.
     *
     * It stays a value source rather than a plain `Date` captured at settings time so that the
     * configuration cache keeps tracking it as an input and re-obtains it on the next build.
     */
    val buildTimestamp: Provider<String> = providers.of(BuildTimestampValueSource::class) {
        parameters {
            buildTimestampFromBuildReceipt.set(this@BuildEnvironmentService.buildTimestampFromBuildReceipt())
            buildTimestampFromGradleProperty.set(providers.gradleProperty(BUILD_TIMESTAMP_PROPERTY))
            enableConfigurationCacheForDocsTests.set(providers.gradleProperty(ENABLE_CONFIGURATION_CACHE_FOR_DOCS_TESTS_PROPERTY).map { it.toBoolean() })
            runningOnCi.set(providers.environmentVariable(CI_ENVIRONMENT_VARIABLE).map { true }.orElse(false))
            runningInstallTask.set(this@BuildEnvironmentService.parameters.runningInstallTask)
            runningDocsTestTask.set(this@BuildEnvironmentService.parameters.runningDocsTestTask)
        }
    }

    private
    fun buildTimestampFromBuildReceipt(): Provider<String> =
        providers.of(BuildTimestampFromBuildReceiptValueSource::class) {
            parameters {
                ignoreIncomingBuildReceipt.set(providers.gradleProperty(IGNORE_INCOMING_BUILD_RECEIPT_PROPERTY).map { true }.orElse(false))
                buildReceiptFileContents.set(
                    providers.fileContents(
                        this@BuildEnvironmentService.parameters.rootProjectDir.get()
                            .dir("incoming-distributions")
                            .file(BuildTimestampFromBuildReceiptValueSource.BUILD_RECEIPT_FILE_NAME)
                    ).asText
                )
            }
        }

    @Suppress("UnstableApiUsage")
    private
    fun git(vararg args: String): Provider<String> {
        val projectDir = parameters.rootProjectDir.asFile.get()
        val execOutput = providers.exec {
            workingDir = projectDir
            DeprecationLogger.whileDisabled {
                isIgnoreExitValue = true
            }
            commandLine = listOf("git", *args)
            if (OperatingSystem.current().isWindows) {
                commandLine = listOf("cmd.exe", "/d", "/c") + commandLine
            }
        }
        return execOutput.result.zip(execOutput.standardOutput.asText) { result, outputText ->
            if (result.exitValue == 0) outputText.trim()
            else "<unknown>" // It's a source distribution, we don't know.
        }
    }
}
