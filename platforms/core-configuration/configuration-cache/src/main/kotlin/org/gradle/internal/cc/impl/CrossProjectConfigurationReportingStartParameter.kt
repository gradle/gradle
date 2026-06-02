/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.cc.impl

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import org.gradle.StartParameter
import org.gradle.TaskExecutionRequest
import org.gradle.api.artifacts.verification.DependencyVerificationMode
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.launcher.cli.WelcomeMessageConfiguration
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.api.logging.configuration.ConsoleUnicodeSupport
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.api.logging.configuration.WarningMode
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption
import org.gradle.initialization.layout.BuildLayoutConfiguration
import org.gradle.internal.buildoption.Option
import org.gradle.internal.configuration.problems.IsolatedProjectsProblemsReporter
import org.gradle.internal.watch.registry.WatchMode
import java.io.File
import java.time.Duration
import java.util.Objects

/**
 * IP-reporting wrapper around [StartParameterInternal] returned from
 * [CrossProjectConfigurationReportingGradle.getStartParameter].
 *
 * Reports an Isolated Projects violation on:
 *  - every setter on [StartParameter] / [StartParameterInternal]
 *  - the few non-setter mutators (`addInitScript`, `includeBuild`, `useEmptySettings`, `doNotSearchUpwards`)
 *
 * Returns defensive unmodifiable views for getters that expose mutable internal collections
 * (`projectProperties`, `systemPropertiesArgs`, `taskRequests`, `excludedTaskNames`, ...) so
 * indirect mutation via the returned object also fails.
 *
 * NOTE on super-constructor virtual calls: [StartParameter]'s constructor calls
 * `setTaskNames(null)` which dispatches to our override before our `delegate` field is set.
 * Every override therefore guards with `if (!::delegate.isInitialized) { super.setX(...); return }`
 * via the [duringSuperInit] helper. Once construction is complete, the guard is always false
 * and every call flows through reporting + delegation.
 */
internal class CrossProjectConfigurationReportingStartParameter(
    private val delegateOrNull: StartParameterInternal?,
    private val referrer: ProjectIdentity?,
    private val ipProblems: IsolatedProjectsProblemsReporter?,
) : StartParameterInternal() {

    // Note: parameters are typed nullable so the type system tolerates the super-constructor
    // virtual calls (which run before our fields are initialized). At every actual call site we
    // pass non-null values; after construction completes, `delegate` is always non-null.

    private val delegate: StartParameterInternal
        get() = delegateOrNull ?: error("delegate accessed before initialization")

    private fun duringSuperInit(): Boolean = delegateOrNull == null

    @Suppress("ThrowingExceptionsWithoutMessageOrCause")
    private fun onMutation(what: String) {
        val problems = ipProblems ?: return
        val ref = referrer ?: return
        problems.report {
            problem {
                text("Project ")
                reference(ref.buildTreePath)
                text(" cannot access ")
                reference("StartParameter.$what")
                text(" functionality")
            }
                .exception()
                .build()
        }
    }

    // region StartParameter — LoggingConfiguration delegations

    override fun getLogLevel(): LogLevel =
        if (duringSuperInit()) super.getLogLevel() else delegate.logLevel

    override fun setLogLevel(logLevel: LogLevel) {
        if (duringSuperInit()) { super.setLogLevel(logLevel); return }
        onMutation("setLogLevel")
        delegate.logLevel = logLevel
    }

    override fun getShowStacktrace(): ShowStacktrace =
        if (duringSuperInit()) super.getShowStacktrace() else delegate.showStacktrace

    override fun setShowStacktrace(showStacktrace: ShowStacktrace) {
        if (duringSuperInit()) { super.setShowStacktrace(showStacktrace); return }
        onMutation("setShowStacktrace")
        delegate.showStacktrace = showStacktrace
    }

    override fun getConsoleOutput(): ConsoleOutput =
        if (duringSuperInit()) super.getConsoleOutput() else delegate.consoleOutput

    override fun setConsoleOutput(consoleOutput: ConsoleOutput) {
        if (duringSuperInit()) { super.setConsoleOutput(consoleOutput); return }
        onMutation("setConsoleOutput")
        delegate.consoleOutput = consoleOutput
    }

    override fun getConsoleUnicodeSupport(): ConsoleUnicodeSupport =
        if (duringSuperInit()) super.getConsoleUnicodeSupport() else delegate.consoleUnicodeSupport

    override fun setConsoleUnicodeSupport(unicodeSupport: ConsoleUnicodeSupport) {
        if (duringSuperInit()) { super.setConsoleUnicodeSupport(unicodeSupport); return }
        onMutation("setConsoleUnicodeSupport")
        delegate.consoleUnicodeSupport = unicodeSupport
    }

    override fun getWarningMode(): WarningMode =
        if (duringSuperInit()) super.getWarningMode() else delegate.warningMode

    override fun setWarningMode(warningMode: WarningMode) {
        if (duringSuperInit()) { super.setWarningMode(warningMode); return }
        onMutation("setWarningMode")
        delegate.warningMode = warningMode
    }

    override fun isNonInteractive(): Boolean =
        if (duringSuperInit()) super.isNonInteractive() else delegate.isNonInteractive

    override fun setNonInteractive(nonInteractive: Boolean) {
        if (duringSuperInit()) { super.setNonInteractive(nonInteractive); return }
        onMutation("setNonInteractive")
        delegate.isNonInteractive = nonInteractive
    }

    // endregion

    // region StartParameter — ParallelismConfiguration delegations

    override fun isParallelProjectExecutionEnabled(): Boolean =
        if (duringSuperInit()) super.isParallelProjectExecutionEnabled() else delegate.isParallelProjectExecutionEnabled

    override fun setParallelProjectExecutionEnabled(parallelProjectExecution: Boolean) {
        if (duringSuperInit()) { super.setParallelProjectExecutionEnabled(parallelProjectExecution); return }
        onMutation("setParallelProjectExecutionEnabled")
        delegate.isParallelProjectExecutionEnabled = parallelProjectExecution
    }

    override fun getMaxWorkerCount(): Int =
        if (duringSuperInit()) super.getMaxWorkerCount() else delegate.maxWorkerCount

    override fun setMaxWorkerCount(maxWorkerCount: Int) {
        if (duringSuperInit()) { super.setMaxWorkerCount(maxWorkerCount); return }
        onMutation("setMaxWorkerCount")
        delegate.maxWorkerCount = maxWorkerCount
    }

    // endregion

    // region StartParameter — tasks / requests

    /** Returns a fresh list (matches [StartParameter.getTaskNames] semantics). Safe to leave un-wrapped. */
    override fun getTaskNames(): MutableList<String> =
        if (duringSuperInit()) super.getTaskNames() else delegate.taskNames

    override fun setTaskNames(taskNames: Iterable<String>?) {
        if (duringSuperInit()) { super.setTaskNames(taskNames); return }
        onMutation("setTaskNames")
        delegate.setTaskNames(taskNames)
    }

    override fun getTaskRequests(): List<TaskExecutionRequest> =
        if (duringSuperInit()) super.getTaskRequests()
        else ImmutableList.copyOf(delegate.taskRequests)

    override fun setTaskRequests(taskParameters: Iterable<TaskExecutionRequest>) {
        if (duringSuperInit()) { super.setTaskRequests(taskParameters); return }
        onMutation("setTaskRequests")
        delegate.setTaskRequests(taskParameters)
    }

    override fun getExcludedTaskNames(): Set<String> =
        if (duringSuperInit()) super.getExcludedTaskNames()
        else ImmutableSet.copyOf(delegate.excludedTaskNames)

    override fun setExcludedTaskNames(excludedTaskNames: Iterable<String>) {
        if (duringSuperInit()) { super.setExcludedTaskNames(excludedTaskNames); return }
        onMutation("setExcludedTaskNames")
        delegate.setExcludedTaskNames(excludedTaskNames)
    }

    // endregion

    // region StartParameter — directories / files

    override fun getCurrentDir(): File =
        if (duringSuperInit()) super.getCurrentDir() else delegate.currentDir

    override fun setCurrentDir(currentDir: File?) {
        if (duringSuperInit()) { super.setCurrentDir(currentDir); return }
        onMutation("setCurrentDir")
        delegate.setCurrentDir(currentDir)
    }

    override fun getProjectDir(): File? =
        if (duringSuperInit()) super.getProjectDir() else delegate.projectDir

    override fun setProjectDir(projectDir: File?) {
        if (duringSuperInit()) { super.setProjectDir(projectDir); return }
        onMutation("setProjectDir")
        delegate.setProjectDir(projectDir)
    }

    override fun getGradleUserHomeDir(): File =
        if (duringSuperInit()) super.getGradleUserHomeDir() else delegate.gradleUserHomeDir

    override fun setGradleUserHomeDir(gradleUserHomeDir: File?) {
        if (duringSuperInit()) { super.setGradleUserHomeDir(gradleUserHomeDir); return }
        onMutation("setGradleUserHomeDir")
        delegate.setGradleUserHomeDir(gradleUserHomeDir)
    }

    override fun getProjectCacheDir(): File? =
        if (duringSuperInit()) super.getProjectCacheDir() else delegate.projectCacheDir

    override fun setProjectCacheDir(projectCacheDir: File?) {
        if (duringSuperInit()) { super.setProjectCacheDir(projectCacheDir); return }
        onMutation("setProjectCacheDir")
        delegate.projectCacheDir = projectCacheDir
    }

    // endregion

    // region StartParameter — properties (mutable maps!)

    override fun getProjectProperties(): Map<String, String> =
        if (duringSuperInit()) super.getProjectProperties()
        else ImmutableMap.copyOf(delegate.projectProperties)

    override fun setProjectProperties(projectProperties: MutableMap<String, String>) {
        if (duringSuperInit()) { super.setProjectProperties(projectProperties); return }
        onMutation("setProjectProperties")
        delegate.setProjectProperties(projectProperties)
    }

    override fun getSystemPropertiesArgs(): Map<String, String> =
        if (duringSuperInit()) super.getSystemPropertiesArgs()
        else ImmutableMap.copyOf(delegate.systemPropertiesArgs)

    override fun setSystemPropertiesArgs(systemPropertiesArgs: MutableMap<String, String>) {
        if (duringSuperInit()) { super.setSystemPropertiesArgs(systemPropertiesArgs); return }
        onMutation("setSystemPropertiesArgs")
        delegate.setSystemPropertiesArgs(systemPropertiesArgs)
    }

    // endregion

    // region StartParameter — booleans / misc

    override fun isBuildProjectDependencies(): Boolean =
        if (duringSuperInit()) super.isBuildProjectDependencies() else delegate.isBuildProjectDependencies

    override fun setBuildProjectDependencies(build: Boolean): StartParameter {
        if (duringSuperInit()) return super.setBuildProjectDependencies(build)
        onMutation("setBuildProjectDependencies")
        delegate.isBuildProjectDependencies = build
        return this
    }

    override fun isDryRun(): Boolean =
        if (duringSuperInit()) super.isDryRun() else delegate.isDryRun

    override fun setDryRun(dryRun: Boolean) {
        if (duringSuperInit()) { super.setDryRun(dryRun); return }
        onMutation("setDryRun")
        delegate.isDryRun = dryRun
    }

    override fun isProfile(): Boolean =
        if (duringSuperInit()) super.isProfile() else delegate.isProfile

    override fun setProfile(profile: Boolean) {
        if (duringSuperInit()) { super.setProfile(profile); return }
        onMutation("setProfile")
        delegate.isProfile = profile
    }

    override fun isContinueOnFailure(): Boolean =
        if (duringSuperInit()) super.isContinueOnFailure() else delegate.isContinueOnFailure

    override fun setContinueOnFailure(continueOnFailure: Boolean) {
        if (duringSuperInit()) { super.setContinueOnFailure(continueOnFailure); return }
        onMutation("setContinueOnFailure")
        delegate.isContinueOnFailure = continueOnFailure
    }

    override fun isOffline(): Boolean =
        if (duringSuperInit()) super.isOffline() else delegate.isOffline

    override fun setOffline(offline: Boolean) {
        if (duringSuperInit()) { super.setOffline(offline); return }
        onMutation("setOffline")
        delegate.isOffline = offline
    }

    override fun isRefreshDependencies(): Boolean =
        if (duringSuperInit()) super.isRefreshDependencies() else delegate.isRefreshDependencies

    override fun setRefreshDependencies(refreshDependencies: Boolean) {
        if (duringSuperInit()) { super.setRefreshDependencies(refreshDependencies); return }
        onMutation("setRefreshDependencies")
        delegate.isRefreshDependencies = refreshDependencies
    }

    override fun isRerunTasks(): Boolean =
        if (duringSuperInit()) super.isRerunTasks() else delegate.isRerunTasks

    override fun setRerunTasks(rerunTasks: Boolean) {
        if (duringSuperInit()) { super.setRerunTasks(rerunTasks); return }
        onMutation("setRerunTasks")
        delegate.isRerunTasks = rerunTasks
    }

    override fun isTaskGraph(): Boolean =
        if (duringSuperInit()) super.isTaskGraph() else delegate.isTaskGraph

    override fun setTaskGraph(taskGraph: Boolean) {
        if (duringSuperInit()) { super.setTaskGraph(taskGraph); return }
        onMutation("setTaskGraph")
        delegate.isTaskGraph = taskGraph
    }

    override fun isBuildCacheEnabled(): Boolean =
        if (duringSuperInit()) super.isBuildCacheEnabled() else delegate.isBuildCacheEnabled

    override fun setBuildCacheEnabled(buildCacheEnabled: Boolean) {
        if (duringSuperInit()) { super.setBuildCacheEnabled(buildCacheEnabled); return }
        onMutation("setBuildCacheEnabled")
        delegate.isBuildCacheEnabled = buildCacheEnabled
    }

    override fun isBuildCacheDebugLogging(): Boolean =
        if (duringSuperInit()) super.isBuildCacheDebugLogging() else delegate.isBuildCacheDebugLogging

    override fun setBuildCacheDebugLogging(buildCacheDebugLogging: Boolean) {
        if (duringSuperInit()) { super.setBuildCacheDebugLogging(buildCacheDebugLogging); return }
        onMutation("setBuildCacheDebugLogging")
        delegate.isBuildCacheDebugLogging = buildCacheDebugLogging
    }

    override fun isConfigureOnDemand(): Boolean =
        if (duringSuperInit()) super.isConfigureOnDemand() else delegate.isConfigureOnDemand

    override fun setConfigureOnDemand(configureOnDemand: Boolean) {
        if (duringSuperInit()) { super.setConfigureOnDemand(configureOnDemand); return }
        onMutation("setConfigureOnDemand")
        delegate.isConfigureOnDemand = configureOnDemand
    }

    override fun isContinuous(): Boolean =
        if (duringSuperInit()) super.isContinuous() else delegate.isContinuous

    override fun setContinuous(enabled: Boolean) {
        if (duringSuperInit()) { super.setContinuous(enabled); return }
        onMutation("setContinuous")
        delegate.isContinuous = enabled
    }

    override fun isBuildScan(): Boolean =
        if (duringSuperInit()) super.isBuildScan() else delegate.isBuildScan

    override fun setBuildScan(buildScan: Boolean) {
        if (duringSuperInit()) { super.setBuildScan(buildScan); return }
        onMutation("setBuildScan")
        delegate.isBuildScan = buildScan
    }

    override fun isNoBuildScan(): Boolean =
        if (duringSuperInit()) super.isNoBuildScan() else delegate.isNoBuildScan

    override fun setNoBuildScan(noBuildScan: Boolean) {
        if (duringSuperInit()) { super.setNoBuildScan(noBuildScan); return }
        onMutation("setNoBuildScan")
        delegate.isNoBuildScan = noBuildScan
    }

    override fun isWriteDependencyLocks(): Boolean =
        if (duringSuperInit()) super.isWriteDependencyLocks() else delegate.isWriteDependencyLocks

    override fun setWriteDependencyLocks(writeDependencyLocks: Boolean) {
        if (duringSuperInit()) { super.setWriteDependencyLocks(writeDependencyLocks); return }
        onMutation("setWriteDependencyLocks")
        delegate.isWriteDependencyLocks = writeDependencyLocks
    }

    override fun isRefreshKeys(): Boolean =
        if (duringSuperInit()) super.isRefreshKeys() else delegate.isRefreshKeys

    override fun setRefreshKeys(refresh: Boolean) {
        if (duringSuperInit()) { super.setRefreshKeys(refresh); return }
        onMutation("setRefreshKeys")
        delegate.isRefreshKeys = refresh
    }

    override fun isExportKeys(): Boolean =
        if (duringSuperInit()) super.isExportKeys() else delegate.isExportKeys

    override fun setExportKeys(exportKeys: Boolean) {
        if (duringSuperInit()) { super.setExportKeys(exportKeys); return }
        onMutation("setExportKeys")
        delegate.isExportKeys = exportKeys
    }

    // endregion

    // region StartParameter — init scripts and included builds (also mutators!)

    override fun getInitScripts(): List<File> =
        if (duringSuperInit()) super.getInitScripts() else delegate.initScripts

    override fun setInitScripts(initScripts: List<File>) {
        if (duringSuperInit()) { super.setInitScripts(initScripts); return }
        onMutation("setInitScripts")
        delegate.initScripts = initScripts
    }

    override fun addInitScript(initScriptFile: File) {
        if (duringSuperInit()) { super.addInitScript(initScriptFile); return }
        onMutation("addInitScript")
        delegate.addInitScript(initScriptFile)
    }

    override fun getAllInitScripts(): List<File> =
        if (duringSuperInit()) super.getAllInitScripts() else delegate.allInitScripts

    override fun getIncludedBuilds(): List<File> =
        if (duringSuperInit()) super.getIncludedBuilds() else delegate.includedBuilds

    override fun setIncludedBuilds(includedBuilds: List<File>) {
        if (duringSuperInit()) { super.setIncludedBuilds(includedBuilds); return }
        onMutation("setIncludedBuilds")
        delegate.setIncludedBuilds(includedBuilds)
    }

    override fun includeBuild(includedBuild: File) {
        if (duringSuperInit()) { super.includeBuild(includedBuild); return }
        onMutation("includeBuild")
        delegate.includeBuild(includedBuild)
    }

    // endregion

    // region StartParameter — dependency verification / locking

    override fun getLockedDependenciesToUpdate(): List<String> =
        if (duringSuperInit()) super.getLockedDependenciesToUpdate()
        else ImmutableList.copyOf(delegate.lockedDependenciesToUpdate)

    override fun setLockedDependenciesToUpdate(lockedDependenciesToUpdate: List<String>) {
        if (duringSuperInit()) { super.setLockedDependenciesToUpdate(lockedDependenciesToUpdate); return }
        onMutation("setLockedDependenciesToUpdate")
        delegate.setLockedDependenciesToUpdate(lockedDependenciesToUpdate)
    }

    override fun getWriteDependencyVerifications(): List<String> =
        if (duringSuperInit()) super.getWriteDependencyVerifications()
        else ImmutableList.copyOf(delegate.writeDependencyVerifications)

    override fun setWriteDependencyVerifications(checksums: List<String>) {
        if (duringSuperInit()) { super.setWriteDependencyVerifications(checksums); return }
        onMutation("setWriteDependencyVerifications")
        delegate.writeDependencyVerifications = checksums
    }

    override fun getDependencyVerificationMode(): DependencyVerificationMode =
        if (duringSuperInit()) super.getDependencyVerificationMode() else delegate.dependencyVerificationMode

    override fun setDependencyVerificationMode(verificationMode: DependencyVerificationMode) {
        if (duringSuperInit()) { super.setDependencyVerificationMode(verificationMode); return }
        onMutation("setDependencyVerificationMode")
        delegate.dependencyVerificationMode = verificationMode
    }

    // endregion

    // region StartParameter — welcome message

    override fun getWelcomeMessageConfiguration(): WelcomeMessageConfiguration =
        if (duringSuperInit()) super.getWelcomeMessageConfiguration() else delegate.welcomeMessageConfiguration

    override fun setWelcomeMessageConfiguration(welcomeMessageConfiguration: WelcomeMessageConfiguration) {
        if (duringSuperInit()) { super.setWelcomeMessageConfiguration(welcomeMessageConfiguration); return }
        onMutation("setWelcomeMessageConfiguration")
        delegate.welcomeMessageConfiguration = welcomeMessageConfiguration
    }

    // endregion

    // region StartParameter — deprecated CC API

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun isConfigurationCacheRequested(): Boolean =
        if (duringSuperInit()) super.isConfigurationCacheRequested() else delegate.isConfigurationCacheRequested

    // endregion

    // region StartParameter — factory methods (return copies, no need to wrap)

    override fun newInstance(): StartParameterInternal =
        if (duringSuperInit()) super.newInstance() else delegate.newInstance()

    override fun newBuild(): StartParameterInternal =
        if (duringSuperInit()) super.newBuild() else delegate.newBuild()

    // endregion

    // region StartParameterInternal — additional setters

    override fun getGradleHomeDir(): File =
        if (duringSuperInit()) super.getGradleHomeDir() else delegate.gradleHomeDir

    override fun setGradleHomeDir(gradleHomeDir: File) {
        if (duringSuperInit()) { super.setGradleHomeDir(gradleHomeDir); return }
        onMutation("setGradleHomeDir")
        delegate.setGradleHomeDir(gradleHomeDir)
    }

    override fun isSearchUpwards(): Boolean =
        if (duringSuperInit()) super.isSearchUpwards() else delegate.isSearchUpwards

    override fun doNotSearchUpwards() {
        if (duringSuperInit()) { super.doNotSearchUpwards(); return }
        onMutation("doNotSearchUpwards")
        delegate.doNotSearchUpwards()
    }

    override fun isUseEmptySettings(): Boolean =
        if (duringSuperInit()) super.isUseEmptySettings() else delegate.isUseEmptySettings

    override fun useEmptySettings() {
        if (duringSuperInit()) { super.useEmptySettings(); return }
        onMutation("useEmptySettings")
        delegate.useEmptySettings()
    }

    override fun getWatchFileSystemMode(): WatchMode =
        if (duringSuperInit()) super.getWatchFileSystemMode() else delegate.watchFileSystemMode

    override fun setWatchFileSystemMode(watchFileSystemMode: WatchMode) {
        if (duringSuperInit()) { super.setWatchFileSystemMode(watchFileSystemMode); return }
        onMutation("setWatchFileSystemMode")
        delegate.watchFileSystemMode = watchFileSystemMode
    }

    override fun isVfsVerboseLogging(): Boolean =
        if (duringSuperInit()) super.isVfsVerboseLogging() else delegate.isVfsVerboseLogging

    override fun setVfsVerboseLogging(vfsVerboseLogging: Boolean) {
        if (duringSuperInit()) { super.setVfsVerboseLogging(vfsVerboseLogging); return }
        onMutation("setVfsVerboseLogging")
        delegate.isVfsVerboseLogging = vfsVerboseLogging
    }

    override fun getConfigurationCache(): Option.Value<Boolean> =
        if (duringSuperInit()) super.getConfigurationCache() else delegate.configurationCache

    override fun setConfigurationCache(configurationCache: Option.Value<Boolean>) {
        if (duringSuperInit()) { super.setConfigurationCache(configurationCache); return }
        onMutation("setConfigurationCache")
        delegate.configurationCache = configurationCache
    }

    override fun getIsolatedProjects(): Option.Value<Boolean> =
        if (duringSuperInit()) super.getIsolatedProjects() else delegate.isolatedProjects

    override fun setIsolatedProjects(isolatedProjects: Option.Value<Boolean>) {
        if (duringSuperInit()) { super.setIsolatedProjects(isolatedProjects); return }
        onMutation("setIsolatedProjects")
        delegate.isolatedProjects = isolatedProjects
    }

    override fun getConfigurationCacheProblems(): ConfigurationCacheProblemsOption.Value =
        if (duringSuperInit()) super.getConfigurationCacheProblems() else delegate.configurationCacheProblems

    override fun setConfigurationCacheProblems(configurationCacheProblems: ConfigurationCacheProblemsOption.Value) {
        if (duringSuperInit()) { super.setConfigurationCacheProblems(configurationCacheProblems); return }
        onMutation("setConfigurationCacheProblems")
        delegate.configurationCacheProblems = configurationCacheProblems
    }

    override fun isConfigurationCacheDebug(): Boolean =
        if (duringSuperInit()) super.isConfigurationCacheDebug() else delegate.isConfigurationCacheDebug

    override fun setConfigurationCacheDebug(configurationCacheDebug: Boolean) {
        if (duringSuperInit()) { super.setConfigurationCacheDebug(configurationCacheDebug); return }
        onMutation("setConfigurationCacheDebug")
        delegate.isConfigurationCacheDebug = configurationCacheDebug
    }

    override fun isConfigurationCacheIgnoreInputsDuringStore(): Boolean =
        if (duringSuperInit()) super.isConfigurationCacheIgnoreInputsDuringStore()
        else delegate.isConfigurationCacheIgnoreInputsDuringStore

    override fun setConfigurationCacheIgnoreInputsDuringStore(ignoreInputsDuringStore: Boolean) {
        if (duringSuperInit()) { super.setConfigurationCacheIgnoreInputsDuringStore(ignoreInputsDuringStore); return }
        onMutation("setConfigurationCacheIgnoreInputsDuringStore")
        delegate.isConfigurationCacheIgnoreInputsDuringStore = ignoreInputsDuringStore
    }

    override fun isConfigurationCacheIgnoreUnsupportedBuildEventsListeners(): Boolean =
        if (duringSuperInit()) super.isConfigurationCacheIgnoreUnsupportedBuildEventsListeners()
        else delegate.isConfigurationCacheIgnoreUnsupportedBuildEventsListeners

    override fun setConfigurationCacheIgnoreUnsupportedBuildEventsListeners(value: Boolean) {
        if (duringSuperInit()) { super.setConfigurationCacheIgnoreUnsupportedBuildEventsListeners(value); return }
        onMutation("setConfigurationCacheIgnoreUnsupportedBuildEventsListeners")
        delegate.isConfigurationCacheIgnoreUnsupportedBuildEventsListeners = value
    }

    override fun isConfigurationCacheParallel(): Boolean =
        if (duringSuperInit()) super.isConfigurationCacheParallel() else delegate.isConfigurationCacheParallel

    override fun setConfigurationCacheParallel(parallel: Boolean) {
        if (duringSuperInit()) { super.setConfigurationCacheParallel(parallel); return }
        onMutation("setConfigurationCacheParallel")
        delegate.isConfigurationCacheParallel = parallel
    }

    override fun isConfigurationCacheReadOnly(): Boolean =
        if (duringSuperInit()) super.isConfigurationCacheReadOnly() else delegate.isConfigurationCacheReadOnly

    override fun setConfigurationCacheReadOnly(readOnly: Boolean) {
        if (duringSuperInit()) { super.setConfigurationCacheReadOnly(readOnly); return }
        onMutation("setConfigurationCacheReadOnly")
        delegate.isConfigurationCacheReadOnly = readOnly
    }

    override fun getConfigurationCacheEntriesPerKey(): Int =
        if (duringSuperInit()) super.getConfigurationCacheEntriesPerKey()
        else delegate.configurationCacheEntriesPerKey

    override fun setConfigurationCacheEntriesPerKey(configurationCacheEntriesPerKey: Int) {
        if (duringSuperInit()) { super.setConfigurationCacheEntriesPerKey(configurationCacheEntriesPerKey); return }
        onMutation("setConfigurationCacheEntriesPerKey")
        delegate.configurationCacheEntriesPerKey = configurationCacheEntriesPerKey
    }

    override fun getConfigurationCacheMaxProblems(): Int =
        if (duringSuperInit()) super.getConfigurationCacheMaxProblems()
        else delegate.configurationCacheMaxProblems

    override fun setConfigurationCacheMaxProblems(configurationCacheMaxProblems: Int) {
        if (duringSuperInit()) { super.setConfigurationCacheMaxProblems(configurationCacheMaxProblems); return }
        onMutation("setConfigurationCacheMaxProblems")
        delegate.configurationCacheMaxProblems = configurationCacheMaxProblems
    }

    override fun getConfigurationCacheIgnoredFileSystemCheckInputs(): String? =
        if (duringSuperInit()) super.getConfigurationCacheIgnoredFileSystemCheckInputs()
        else delegate.configurationCacheIgnoredFileSystemCheckInputs

    override fun setConfigurationCacheIgnoredFileSystemCheckInputs(value: String?) {
        if (duringSuperInit()) { super.setConfigurationCacheIgnoredFileSystemCheckInputs(value); return }
        onMutation("setConfigurationCacheIgnoredFileSystemCheckInputs")
        delegate.configurationCacheIgnoredFileSystemCheckInputs = value
    }

    override fun isConfigurationCacheRecreateCache(): Boolean =
        if (duringSuperInit()) super.isConfigurationCacheRecreateCache()
        else delegate.isConfigurationCacheRecreateCache

    override fun setConfigurationCacheRecreateCache(configurationCacheRecreateCache: Boolean) {
        if (duringSuperInit()) { super.setConfigurationCacheRecreateCache(configurationCacheRecreateCache); return }
        onMutation("setConfigurationCacheRecreateCache")
        delegate.isConfigurationCacheRecreateCache = configurationCacheRecreateCache
    }

    override fun isConfigurationCacheQuiet(): Boolean =
        if (duringSuperInit()) super.isConfigurationCacheQuiet() else delegate.isConfigurationCacheQuiet

    override fun setConfigurationCacheQuiet(configurationCacheQuiet: Boolean) {
        if (duringSuperInit()) { super.setConfigurationCacheQuiet(configurationCacheQuiet); return }
        onMutation("setConfigurationCacheQuiet")
        delegate.isConfigurationCacheQuiet = configurationCacheQuiet
    }

    override fun isConfigurationCacheIntegrityCheckEnabled(): Boolean =
        if (duringSuperInit()) super.isConfigurationCacheIntegrityCheckEnabled()
        else delegate.isConfigurationCacheIntegrityCheckEnabled

    override fun setConfigurationCacheIntegrityCheckEnabled(value: Boolean) {
        if (duringSuperInit()) { super.setConfigurationCacheIntegrityCheckEnabled(value); return }
        onMutation("setConfigurationCacheIntegrityCheckEnabled")
        delegate.isConfigurationCacheIntegrityCheckEnabled = value
    }

    override fun getConfigurationCacheHeapDumpDir(): String? =
        if (duringSuperInit()) super.getConfigurationCacheHeapDumpDir()
        else delegate.configurationCacheHeapDumpDir

    override fun setConfigurationCacheHeapDumpDir(value: String?) {
        if (duringSuperInit()) { super.setConfigurationCacheHeapDumpDir(value); return }
        onMutation("setConfigurationCacheHeapDumpDir")
        delegate.configurationCacheHeapDumpDir = value
    }

    override fun isConfigurationCacheFineGrainedPropertyTracking(): Boolean =
        if (duringSuperInit()) super.isConfigurationCacheFineGrainedPropertyTracking()
        else delegate.isConfigurationCacheFineGrainedPropertyTracking

    override fun setConfigurationCacheFineGrainedPropertyTracking(value: Boolean) {
        if (duringSuperInit()) { super.setConfigurationCacheFineGrainedPropertyTracking(value); return }
        onMutation("setConfigurationCacheFineGrainedPropertyTracking")
        delegate.isConfigurationCacheFineGrainedPropertyTracking = value
    }

    override fun isIsolatedProjectsDiagnostics(): Boolean =
        if (duringSuperInit()) super.isIsolatedProjectsDiagnostics() else delegate.isIsolatedProjectsDiagnostics

    override fun setIsolatedProjectsDiagnostics(isolatedProjectsDiagnostics: Boolean) {
        if (duringSuperInit()) { super.setIsolatedProjectsDiagnostics(isolatedProjectsDiagnostics); return }
        onMutation("setIsolatedProjectsDiagnostics")
        delegate.isIsolatedProjectsDiagnostics = isolatedProjectsDiagnostics
    }

    override fun getContinuousBuildQuietPeriod(): Duration =
        if (duringSuperInit()) super.getContinuousBuildQuietPeriod() else delegate.continuousBuildQuietPeriod

    override fun setContinuousBuildQuietPeriod(continuousBuildQuietPeriod: Duration) {
        if (duringSuperInit()) { super.setContinuousBuildQuietPeriod(continuousBuildQuietPeriod); return }
        onMutation("setContinuousBuildQuietPeriod")
        delegate.continuousBuildQuietPeriod = continuousBuildQuietPeriod
    }

    override fun isPropertyUpgradeReportEnabled(): Boolean =
        if (duringSuperInit()) super.isPropertyUpgradeReportEnabled() else delegate.isPropertyUpgradeReportEnabled

    override fun setPropertyUpgradeReportEnabled(propertyUpgradeReportEnabled: Boolean) {
        if (duringSuperInit()) { super.setPropertyUpgradeReportEnabled(propertyUpgradeReportEnabled); return }
        onMutation("setPropertyUpgradeReportEnabled")
        delegate.isPropertyUpgradeReportEnabled = propertyUpgradeReportEnabled
    }

    override fun isProblemReportGenerationEnabled(): Boolean =
        if (duringSuperInit()) super.isProblemReportGenerationEnabled() else delegate.isProblemReportGenerationEnabled

    override fun enableProblemReportGeneration(enableProblemReportGeneration: Boolean) {
        if (duringSuperInit()) { super.enableProblemReportGeneration(enableProblemReportGeneration); return }
        onMutation("enableProblemReportGeneration")
        delegate.enableProblemReportGeneration(enableProblemReportGeneration)
    }

    override fun isDaemonJvmCriteriaConfigured(): Boolean =
        if (duringSuperInit()) super.isDaemonJvmCriteriaConfigured() else delegate.isDaemonJvmCriteriaConfigured

    override fun setDaemonJvmCriteriaConfigured(daemonJvmCriteriaConfigured: Boolean) {
        if (duringSuperInit()) { super.setDaemonJvmCriteriaConfigured(daemonJvmCriteriaConfigured); return }
        onMutation("setDaemonJvmCriteriaConfigured")
        delegate.isDaemonJvmCriteriaConfigured = daemonJvmCriteriaConfigured
    }

    override fun getParallelToolingModelBuilding(): Option.Value<Boolean> =
        if (duringSuperInit()) super.getParallelToolingModelBuilding() else delegate.parallelToolingModelBuilding

    override fun setParallelToolingModelBuilding(parallelToolingModelBuilding: Option.Value<Boolean>) {
        if (duringSuperInit()) { super.setParallelToolingModelBuilding(parallelToolingModelBuilding); return }
        onMutation("setParallelToolingModelBuilding")
        delegate.parallelToolingModelBuilding = parallelToolingModelBuilding
    }

    override fun getDevelocityUrl(): String? =
        if (duringSuperInit()) super.getDevelocityUrl() else delegate.develocityUrl

    override fun setDevelocityUrl(develocityUrl: String?) {
        if (duringSuperInit()) { super.setDevelocityUrl(develocityUrl); return }
        onMutation("setDevelocityUrl")
        delegate.develocityUrl = develocityUrl
    }

    override fun getDevelocityPluginVersion(): String? =
        if (duringSuperInit()) super.getDevelocityPluginVersion() else delegate.develocityPluginVersion

    override fun setDevelocityPluginVersion(develocityPluginVersion: String?) {
        if (duringSuperInit()) { super.setDevelocityPluginVersion(develocityPluginVersion); return }
        onMutation("setDevelocityPluginVersion")
        delegate.develocityPluginVersion = develocityPluginVersion
    }

    override fun getProjectPropertiesUntracked(): Map<String, String> =
        if (duringSuperInit()) super.getProjectPropertiesUntracked()
        else ImmutableMap.copyOf(delegate.projectPropertiesUntracked)

    override fun toBuildLayoutConfiguration(): BuildLayoutConfiguration =
        if (duringSuperInit()) super.toBuildLayoutConfiguration() else delegate.toBuildLayoutConfiguration()

    // endregion

    // region equals / hashCode / toString — delegate-based

    override fun equals(other: Any?): Boolean {
        if (duringSuperInit()) return super.equals(other)
        // Only wrapper-to-wrapper equality: comparing equal to the bare delegate would be
        // asymmetric (`delegate.equals(wrapper)` is false) and violate the equals contract.
        return other is CrossProjectConfigurationReportingStartParameter &&
            delegate == other.delegateOrNull &&
            referrer == other.referrer
    }

    override fun hashCode(): Int =
        if (duringSuperInit()) super.hashCode() else Objects.hash(delegate, referrer)

    override fun toString(): String =
        if (duringSuperInit()) "CrossProjectConfigurationReportingStartParameter(uninitialized)"
        else "CrossProjectConfigurationReportingStartParameter($delegate)"

    // endregion
}
