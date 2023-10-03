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

package org.gradle.configurationcache.initialization

import org.gradle.StartParameter
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.logging.LogLevel
import org.gradle.configurationcache.extensions.unsafeLazy
import org.gradle.configurationcache.serialization.Workarounds
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption
import org.gradle.initialization.layout.BuildLocations
import org.gradle.internal.Factory
import org.gradle.internal.buildoption.InternalFlag
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.buildoption.StringInternalOption
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.deprecation.DeprecationLogger
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.util.internal.SupportedEncryptionAlgorithm
import java.io.File


@ServiceScope(Scopes.BuildTree::class)
class ConfigurationCacheStartParameter(
    private val buildLocations: BuildLocations,
    private val startParameter: StartParameterInternal,
    options: InternalOptions,
    private val modelParameters: BuildModelParameters
) {
    val loadAfterStore: Boolean = !modelParameters.isRequiresBuildModel && options.getInternalFlag("org.gradle.configuration-cache.internal.load-after-store", true)

    val taskExecutionAccessPreStable: Boolean = options.getInternalFlag("org.gradle.configuration-cache.internal.task-execution-access-pre-stable")

    val encryptionRequested: Boolean = options.getInternalFlag("org.gradle.configuration-cache.internal.encryption", true)

    val keystoreDir: String? = options.getInternalString("org.gradle.configuration-cache.internal.key-store-dir", null)

    val encryptionAlgorithm: String = options.getInternalString("org.gradle.configuration-cache.internal.encryption-alg", SupportedEncryptionAlgorithm.AES_ECB_PADDING.transformation)

    /**
     * Should be provided if a link to the report is expected even if no errors were found.
     * Useful in testing.
     */
    val alwaysLogReportLinkAsWarning: Boolean = options.getInternalFlag("org.gradle.configuration-cache.internal.report-link-as-warning", false)

    val gradleProperties: Map<String, Any?>
        get() = startParameter.projectProperties
            .filter { !Workarounds.isIgnoredStartParameterProperty(it.key) }

    val configurationCacheLogLevel: LogLevel
        get() = modelParameters.configurationCacheLogLevel

    val isQuiet: Boolean
        get() = startParameter.isConfigurationCacheQuiet

    val isIgnoreInputsInTaskGraphSerialization: Boolean
        get() = startParameter.isConfigurationCacheIgnoreInputsInTaskGraphSerialization

    val maxProblems: Int
        get() = startParameter.configurationCacheMaxProblems

    val ignoredFileSystemCheckInputs: String?
        get() = startParameter.configurationCacheIgnoredFileSystemCheckInputs

    val isDebug: Boolean
        get() = startParameter.isConfigurationCacheDebug

    val failOnProblems: Boolean
        get() = startParameter.configurationCacheProblems == ConfigurationCacheProblemsOption.Value.FAIL

    val recreateCache: Boolean
        get() = startParameter.isConfigurationCacheRecreateCache

    /**
     * See [StartParameter.getProjectDir].
     */
    val projectDirectory: File?
        get() = startParameter.projectDir

    val currentDirectory: File
        get() = startParameter.currentDir

    val settingsDirectory: File
        get() = buildLocations.settingsDir

    @Suppress("DEPRECATION")
    val settingsFile: File?
        get() = DeprecationLogger.whileDisabled(Factory { startParameter.settingsFile })

    val rootDirectory: File
        get() = buildLocations.rootDirectory

    val isOffline
        get() = startParameter.isOffline

    val isRefreshDependencies
        get() = startParameter.isRefreshDependencies

    val isWriteDependencyLocks
        get() = startParameter.isWriteDependencyLocks && !isUpdateDependencyLocks

    val isUpdateDependencyLocks
        get() = startParameter.lockedDependenciesToUpdate.isNotEmpty()

    val requestedTaskNames: List<String> by unsafeLazy {
        startParameter.taskNames
    }

    val excludedTaskNames: Set<String>
        get() = startParameter.excludedTaskNames

    val allInitScripts: List<File>
        get() = startParameter.allInitScripts

    val gradleUserHomeDir: File
        get() = startParameter.gradleUserHomeDir

    val includedBuilds: List<File>
        get() = startParameter.includedBuilds

    val isBuildScan: Boolean
        get() = startParameter.isBuildScan

    val isNoBuildScan: Boolean
        get() = startParameter.isNoBuildScan

    /**
     * Determines whether Isolated Projects option was enabled.
     *
     * Uses a build model parameter rather than a start parameter as the latter is not final and can be affected by other options of the build.
     */
    val isIsolatedProjects: Boolean
        get() = modelParameters.isIsolatedProjects
}


private
fun InternalOptions.getInternalFlag(systemPropertyName: String, defaultValue: Boolean = false): Boolean =
    getOption(InternalFlag(systemPropertyName, defaultValue)).get()


private
fun InternalOptions.getInternalString(systemPropertyName: String, defaultValue: String?) =
    getOption(StringInternalOption(systemPropertyName, defaultValue)).get()
