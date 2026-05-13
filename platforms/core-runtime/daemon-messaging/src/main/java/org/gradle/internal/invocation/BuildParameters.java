/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.internal.invocation;

import org.gradle.TaskExecutionRequest;
import org.gradle.api.artifacts.verification.DependencyVerificationMode;
import org.gradle.api.launcher.cli.WelcomeMessageDisplayMode;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.configuration.ConsoleOutput;
import org.gradle.api.logging.configuration.ConsoleUnicodeSupport;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.api.logging.configuration.WarningMode;
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption;
import org.gradle.internal.watch.registry.WatchMode;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Immutable data class containing all parameters needed to execute a build.
 * Field types mirror the converter outputs. Nullable fields from {@code ParsedOptions}
 * are passed through verbatim — defaults are applied later when bridging to internal types.
 */
public final class BuildParameters {

    // From DefaultLoggingConfiguration (non-null, converter provides defaults)
    private final LogLevel logLevel;
    private final ShowStacktrace showStacktrace;
    private final ConsoleOutput consoleOutput;
    private final ConsoleUnicodeSupport consoleUnicodeSupport;
    private final WarningMode warningMode;
    private final boolean nonInteractive;

    // From DefaultParallelismConfiguration (non-null, converter provides defaults)
    private final boolean parallelProjectExecutionEnabled;
    private final int maxWorkerCount;

    // From WelcomeMessageConfiguration (non-null, converter provides default)
    private final WelcomeMessageDisplayMode welcomeMessageDisplayMode;

    // TAPI-specific log level override (nullable, applied on top of logging converter result)
    private final @Nullable LogLevel logLevelOverride;

    // Tasks (resolved from CLI extra args or TAPI task requests)
    private final List<TaskExecutionRequest> taskRequests;

    // Layout (from BuildLayoutResult)
    private final @Nullable File projectDir;
    private final File currentDir;
    private final File gradleUserHomeDir;
    private final @Nullable File gradleHomeDir;

    // Always-present collections
    private final Map<String, String> projectProperties;
    private final Map<String, String> systemPropertiesArgs;

    // From ParsedOptions — all nullable, pass through verbatim
    private final @Nullable String projectCacheDir;
    private final @Nullable List<String> initScripts;
    private final @Nullable List<String> excludedTaskNames;
    private final @Nullable List<String> includedBuilds;
    private final @Nullable Boolean buildProjectDependencies;
    private final @Nullable Boolean dryRun;
    private final @Nullable Boolean rerunTasks;
    private final @Nullable Boolean profile;
    private final @Nullable Boolean continueOnFailure;
    private final @Nullable Boolean offline;
    private final @Nullable Boolean refreshDependencies;
    private final @Nullable Boolean buildCacheEnabled;
    private final @Nullable Boolean buildCacheDebugLogging;
    private final @Nullable WatchMode watchFileSystemMode;
    private final @Nullable Boolean vfsVerboseLogging;
    private final @Nullable Boolean configurationCache;
    private final @Nullable Boolean isolatedProjects;
    private final ConfigurationCacheProblemsOption.@Nullable Value configurationCacheProblems;
    private final @Nullable Boolean configurationCacheIgnoreInputsDuringStore;
    private final @Nullable Boolean configurationCacheIgnoreUnsupportedBuildEventsListeners;
    private final @Nullable Integer configurationCacheMaxProblems;
    private final @Nullable String configurationCacheIgnoredFileSystemCheckInputs;
    private final @Nullable Boolean configurationCacheDebug;
    private final @Nullable Boolean configurationCacheRecreateCache;
    private final @Nullable Boolean configurationCacheParallel;
    private final @Nullable Boolean configurationCacheReadOnly;
    private final @Nullable Boolean configurationCacheQuiet;
    private final @Nullable Boolean configurationCacheIntegrityCheckEnabled;
    private final @Nullable Integer configurationCacheEntriesPerKey;
    private final @Nullable String configurationCacheHeapDumpDir;
    private final @Nullable Boolean configurationCacheFineGrainedPropertyTracking;
    private final @Nullable Boolean configureOnDemand;
    private final @Nullable Boolean continuous;
    private final @Nullable Duration continuousBuildQuietPeriod;
    private final @Nullable Boolean buildScan;
    private final @Nullable Boolean writeDependencyLocks;
    private final @Nullable List<String> writeDependencyVerifications;
    private final @Nullable DependencyVerificationMode dependencyVerificationMode;
    private final @Nullable List<String> lockedDependenciesToUpdate;
    private final @Nullable Boolean refreshKeys;
    private final @Nullable Boolean exportKeys;
    private final @Nullable Boolean propertyUpgradeReportEnabled;
    private final @Nullable Boolean problemReportGenerationEnabled;
    private final @Nullable Boolean taskGraph;
    private final @Nullable Boolean parallelToolingModelBuilding;
    private final @Nullable String develocityUrl;
    private final @Nullable String develocityPluginVersion;

    @SuppressWarnings("ParameterNumber")
    public BuildParameters(
        // From DefaultLoggingConfiguration
        LogLevel logLevel,
        ShowStacktrace showStacktrace,
        ConsoleOutput consoleOutput,
        ConsoleUnicodeSupport consoleUnicodeSupport,
        WarningMode warningMode,
        boolean nonInteractive,
        // From DefaultParallelismConfiguration
        boolean parallelProjectExecutionEnabled,
        int maxWorkerCount,
        // From WelcomeMessageConfiguration
        WelcomeMessageDisplayMode welcomeMessageDisplayMode,
        // TAPI override
        @Nullable LogLevel logLevelOverride,
        // Tasks
        List<TaskExecutionRequest> taskRequests,
        // Layout
        @Nullable File projectDir,
        File currentDir,
        File gradleUserHomeDir,
        @Nullable File gradleHomeDir,
        // Always-present collections
        Map<String, String> projectProperties,
        Map<String, String> systemPropertiesArgs,
        // From ParsedOptions
        @Nullable String projectCacheDir,
        @Nullable List<String> initScripts,
        @Nullable List<String> excludedTaskNames,
        @Nullable List<String> includedBuilds,
        @Nullable Boolean buildProjectDependencies,
        @Nullable Boolean dryRun,
        @Nullable Boolean rerunTasks,
        @Nullable Boolean profile,
        @Nullable Boolean continueOnFailure,
        @Nullable Boolean offline,
        @Nullable Boolean refreshDependencies,
        @Nullable Boolean buildCacheEnabled,
        @Nullable Boolean buildCacheDebugLogging,
        @Nullable WatchMode watchFileSystemMode,
        @Nullable Boolean vfsVerboseLogging,
        @Nullable Boolean configurationCache,
        @Nullable Boolean isolatedProjects,
        ConfigurationCacheProblemsOption.@Nullable Value configurationCacheProblems,
        @Nullable Boolean configurationCacheIgnoreInputsDuringStore,
        @Nullable Boolean configurationCacheIgnoreUnsupportedBuildEventsListeners,
        @Nullable Integer configurationCacheMaxProblems,
        @Nullable String configurationCacheIgnoredFileSystemCheckInputs,
        @Nullable Boolean configurationCacheDebug,
        @Nullable Boolean configurationCacheRecreateCache,
        @Nullable Boolean configurationCacheParallel,
        @Nullable Boolean configurationCacheReadOnly,
        @Nullable Boolean configurationCacheQuiet,
        @Nullable Boolean configurationCacheIntegrityCheckEnabled,
        @Nullable Integer configurationCacheEntriesPerKey,
        @Nullable String configurationCacheHeapDumpDir,
        @Nullable Boolean configurationCacheFineGrainedPropertyTracking,
        @Nullable Boolean configureOnDemand,
        @Nullable Boolean continuous,
        @Nullable Duration continuousBuildQuietPeriod,
        @Nullable Boolean buildScan,
        @Nullable Boolean writeDependencyLocks,
        @Nullable List<String> writeDependencyVerifications,
        @Nullable DependencyVerificationMode dependencyVerificationMode,
        @Nullable List<String> lockedDependenciesToUpdate,
        @Nullable Boolean refreshKeys,
        @Nullable Boolean exportKeys,
        @Nullable Boolean propertyUpgradeReportEnabled,
        @Nullable Boolean problemReportGenerationEnabled,
        @Nullable Boolean taskGraph,
        @Nullable Boolean parallelToolingModelBuilding,
        @Nullable String develocityUrl,
        @Nullable String develocityPluginVersion
    ) {
        this.logLevel = logLevel;
        this.showStacktrace = showStacktrace;
        this.consoleOutput = consoleOutput;
        this.consoleUnicodeSupport = consoleUnicodeSupport;
        this.warningMode = warningMode;
        this.nonInteractive = nonInteractive;
        this.parallelProjectExecutionEnabled = parallelProjectExecutionEnabled;
        this.maxWorkerCount = maxWorkerCount;
        this.welcomeMessageDisplayMode = welcomeMessageDisplayMode;
        this.logLevelOverride = logLevelOverride;
        this.taskRequests = taskRequests;
        this.projectDir = projectDir;
        this.currentDir = currentDir;
        this.gradleUserHomeDir = gradleUserHomeDir;
        this.gradleHomeDir = gradleHomeDir;
        this.projectProperties = projectProperties;
        this.systemPropertiesArgs = systemPropertiesArgs;
        this.projectCacheDir = projectCacheDir;
        this.initScripts = initScripts;
        this.excludedTaskNames = excludedTaskNames;
        this.includedBuilds = includedBuilds;
        this.buildProjectDependencies = buildProjectDependencies;
        this.dryRun = dryRun;
        this.rerunTasks = rerunTasks;
        this.profile = profile;
        this.continueOnFailure = continueOnFailure;
        this.offline = offline;
        this.refreshDependencies = refreshDependencies;
        this.buildCacheEnabled = buildCacheEnabled;
        this.buildCacheDebugLogging = buildCacheDebugLogging;
        this.watchFileSystemMode = watchFileSystemMode;
        this.vfsVerboseLogging = vfsVerboseLogging;
        this.configurationCache = configurationCache;
        this.isolatedProjects = isolatedProjects;
        this.configurationCacheProblems = configurationCacheProblems;
        this.configurationCacheIgnoreInputsDuringStore = configurationCacheIgnoreInputsDuringStore;
        this.configurationCacheIgnoreUnsupportedBuildEventsListeners = configurationCacheIgnoreUnsupportedBuildEventsListeners;
        this.configurationCacheMaxProblems = configurationCacheMaxProblems;
        this.configurationCacheIgnoredFileSystemCheckInputs = configurationCacheIgnoredFileSystemCheckInputs;
        this.configurationCacheDebug = configurationCacheDebug;
        this.configurationCacheRecreateCache = configurationCacheRecreateCache;
        this.configurationCacheParallel = configurationCacheParallel;
        this.configurationCacheReadOnly = configurationCacheReadOnly;
        this.configurationCacheQuiet = configurationCacheQuiet;
        this.configurationCacheIntegrityCheckEnabled = configurationCacheIntegrityCheckEnabled;
        this.configurationCacheEntriesPerKey = configurationCacheEntriesPerKey;
        this.configurationCacheHeapDumpDir = configurationCacheHeapDumpDir;
        this.configurationCacheFineGrainedPropertyTracking = configurationCacheFineGrainedPropertyTracking;
        this.configureOnDemand = configureOnDemand;
        this.continuous = continuous;
        this.continuousBuildQuietPeriod = continuousBuildQuietPeriod;
        this.buildScan = buildScan;
        this.writeDependencyLocks = writeDependencyLocks;
        this.writeDependencyVerifications = writeDependencyVerifications;
        this.dependencyVerificationMode = dependencyVerificationMode;
        this.lockedDependenciesToUpdate = lockedDependenciesToUpdate;
        this.refreshKeys = refreshKeys;
        this.exportKeys = exportKeys;
        this.propertyUpgradeReportEnabled = propertyUpgradeReportEnabled;
        this.problemReportGenerationEnabled = problemReportGenerationEnabled;
        this.taskGraph = taskGraph;
        this.parallelToolingModelBuilding = parallelToolingModelBuilding;
        this.develocityUrl = develocityUrl;
        this.develocityPluginVersion = develocityPluginVersion;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public ShowStacktrace getShowStacktrace() {
        return showStacktrace;
    }

    public ConsoleOutput getConsoleOutput() {
        return consoleOutput;
    }

    public ConsoleUnicodeSupport getConsoleUnicodeSupport() {
        return consoleUnicodeSupport;
    }

    public WarningMode getWarningMode() {
        return warningMode;
    }

    public boolean isNonInteractive() {
        return nonInteractive;
    }

    public boolean isParallelProjectExecutionEnabled() {
        return parallelProjectExecutionEnabled;
    }

    public int getMaxWorkerCount() {
        return maxWorkerCount;
    }

    public WelcomeMessageDisplayMode getWelcomeMessageDisplayMode() {
        return welcomeMessageDisplayMode;
    }

    public @Nullable LogLevel getLogLevelOverride() {
        return logLevelOverride;
    }

    public List<TaskExecutionRequest> getTaskRequests() {
        return taskRequests;
    }

    public @Nullable File getProjectDir() {
        return projectDir;
    }

    public File getCurrentDir() {
        return currentDir;
    }

    public File getGradleUserHomeDir() {
        return gradleUserHomeDir;
    }

    public @Nullable File getGradleHomeDir() {
        return gradleHomeDir;
    }

    public Map<String, String> getProjectProperties() {
        return projectProperties;
    }

    public Map<String, String> getSystemPropertiesArgs() {
        return systemPropertiesArgs;
    }

    public @Nullable String getProjectCacheDir() {
        return projectCacheDir;
    }

    public @Nullable List<String> getInitScripts() {
        return initScripts;
    }

    public @Nullable List<String> getExcludedTaskNames() {
        return excludedTaskNames;
    }

    public @Nullable List<String> getIncludedBuilds() {
        return includedBuilds;
    }

    public @Nullable Boolean getBuildProjectDependencies() {
        return buildProjectDependencies;
    }

    public @Nullable Boolean getDryRun() {
        return dryRun;
    }

    public @Nullable Boolean getRerunTasks() {
        return rerunTasks;
    }

    public @Nullable Boolean getProfile() {
        return profile;
    }

    public @Nullable Boolean getContinueOnFailure() {
        return continueOnFailure;
    }

    public @Nullable Boolean getOffline() {
        return offline;
    }

    public @Nullable Boolean getRefreshDependencies() {
        return refreshDependencies;
    }

    public @Nullable Boolean getBuildCacheEnabled() {
        return buildCacheEnabled;
    }

    public @Nullable Boolean getBuildCacheDebugLogging() {
        return buildCacheDebugLogging;
    }

    public @Nullable WatchMode getWatchFileSystemMode() {
        return watchFileSystemMode;
    }

    public @Nullable Boolean getVfsVerboseLogging() {
        return vfsVerboseLogging;
    }

    public @Nullable Boolean getConfigurationCache() {
        return configurationCache;
    }

    public @Nullable Boolean getIsolatedProjects() {
        return isolatedProjects;
    }

    public ConfigurationCacheProblemsOption.@Nullable Value getConfigurationCacheProblems() {
        return configurationCacheProblems;
    }

    public @Nullable Boolean getConfigurationCacheIgnoreInputsDuringStore() {
        return configurationCacheIgnoreInputsDuringStore;
    }

    public @Nullable Boolean getConfigurationCacheIgnoreUnsupportedBuildEventsListeners() {
        return configurationCacheIgnoreUnsupportedBuildEventsListeners;
    }

    public @Nullable Integer getConfigurationCacheMaxProblems() {
        return configurationCacheMaxProblems;
    }

    public @Nullable String getConfigurationCacheIgnoredFileSystemCheckInputs() {
        return configurationCacheIgnoredFileSystemCheckInputs;
    }

    public @Nullable Boolean getConfigurationCacheDebug() {
        return configurationCacheDebug;
    }

    public @Nullable Boolean getConfigurationCacheRecreateCache() {
        return configurationCacheRecreateCache;
    }

    public @Nullable Boolean getConfigurationCacheParallel() {
        return configurationCacheParallel;
    }

    public @Nullable Boolean getConfigurationCacheReadOnly() {
        return configurationCacheReadOnly;
    }

    public @Nullable Boolean getConfigurationCacheQuiet() {
        return configurationCacheQuiet;
    }

    public @Nullable Boolean getConfigurationCacheIntegrityCheckEnabled() {
        return configurationCacheIntegrityCheckEnabled;
    }

    public @Nullable Integer getConfigurationCacheEntriesPerKey() {
        return configurationCacheEntriesPerKey;
    }

    public @Nullable String getConfigurationCacheHeapDumpDir() {
        return configurationCacheHeapDumpDir;
    }

    public @Nullable Boolean getConfigurationCacheFineGrainedPropertyTracking() {
        return configurationCacheFineGrainedPropertyTracking;
    }

    public @Nullable Boolean getConfigureOnDemand() {
        return configureOnDemand;
    }

    public @Nullable Boolean getContinuous() {
        return continuous;
    }

    public @Nullable Duration getContinuousBuildQuietPeriod() {
        return continuousBuildQuietPeriod;
    }

    public @Nullable Boolean getBuildScan() {
        return buildScan;
    }

    public @Nullable Boolean getWriteDependencyLocks() {
        return writeDependencyLocks;
    }

    public @Nullable List<String> getWriteDependencyVerifications() {
        return writeDependencyVerifications;
    }

    public @Nullable DependencyVerificationMode getDependencyVerificationMode() {
        return dependencyVerificationMode;
    }

    public @Nullable List<String> getLockedDependenciesToUpdate() {
        return lockedDependenciesToUpdate;
    }

    public @Nullable Boolean getRefreshKeys() {
        return refreshKeys;
    }

    public @Nullable Boolean getExportKeys() {
        return exportKeys;
    }

    public @Nullable Boolean getPropertyUpgradeReportEnabled() {
        return propertyUpgradeReportEnabled;
    }

    public @Nullable Boolean getProblemReportGenerationEnabled() {
        return problemReportGenerationEnabled;
    }

    public @Nullable Boolean getTaskGraph() {
        return taskGraph;
    }

    public @Nullable Boolean getParallelToolingModelBuilding() {
        return parallelToolingModelBuilding;
    }

    public @Nullable String getDevelocityUrl() {
        return develocityUrl;
    }

    public @Nullable String getDevelocityPluginVersion() {
        return develocityPluginVersion;
    }
}
