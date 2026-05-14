/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.StartParameter;
import org.gradle.api.Transformer;
import org.gradle.api.internal.file.BasicFileResolver;
import org.gradle.api.launcher.cli.WelcomeMessageConfiguration;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.initialization.layout.BuildLayoutConfiguration;
import org.gradle.internal.buildoption.Option;
import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.configuration.inputs.InstrumentedInputs;
import org.gradle.internal.deprecation.StartParameterDeprecations;
import org.gradle.internal.invocation.parameters.BuildParameters;
import org.gradle.internal.invocation.parameters.ConfigurationCacheProblemsMode;
import org.gradle.internal.watch.registry.WatchMode;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.time.Duration;
import java.util.Map;

public class StartParameterInternal extends StartParameter {
    private WatchMode watchFileSystemMode = WatchMode.DEFAULT;
    private boolean vfsVerboseLogging;

    private Option.Value<Boolean> configurationCache = Option.Value.defaultValue(false);
    private Option.Value<Boolean> isolatedProjects = Option.Value.defaultValue(false);
    private ConfigurationCacheProblemsMode configurationCacheProblems = ConfigurationCacheProblemsMode.FAIL;
    private boolean configurationCacheDebug;
    private boolean configurationCacheIgnoreInputsDuringStore = false;
    private boolean configurationCacheIgnoreUnsupportedBuildEventsListeners = false;
    private int configurationCacheMaxProblems = 512;
    private @Nullable String configurationCacheIgnoredFileSystemCheckInputs = null;
    private boolean configurationCacheParallel;
    private boolean configurationCacheReadOnly;
    private boolean configurationCacheRecreateCache;
    private boolean configurationCacheQuiet;
    private int configurationCacheEntriesPerKey = 1;
    private boolean configurationCacheIntegrityCheckEnabled;
    private @Nullable String configurationCacheHeapDumpDir;
    private boolean configurationCacheFineGrainedPropertyTracking = true;
    private boolean searchUpwards = true;
    private boolean useEmptySettings = false;
    private Duration continuousBuildQuietPeriod = Duration.ofMillis(250);
    private boolean propertyUpgradeReportEnabled;
    private boolean enableProblemReportGeneration = true;
    private Option.Value<Boolean> parallelToolingModelBuilding = Option.Value.defaultValue(false);
    private @Nullable String develocityUrl;
    private @Nullable String develocityPluginVersion;

    public StartParameterInternal() {
    }

    protected StartParameterInternal(BuildLayoutParameters layoutParameters) {
        super(layoutParameters);
    }

    public static StartParameterInternal fromBuildParameters(BuildParameters params) {
        StartParameterInternal sp = new StartParameterInternal();

        // Logging
        sp.setLogLevel(params.getLogLevel());
        sp.setShowStacktrace(params.getShowStacktrace());
        sp.setConsoleOutput(params.getConsoleOutput());
        sp.setConsoleUnicodeSupport(params.getConsoleUnicodeSupport());
        sp.setWarningMode(params.getWarningMode());
        sp.setNonInteractive(params.isNonInteractive());

        // Parallelism
        sp.setParallelProjectExecutionEnabled(params.isParallelProjectExecutionEnabled());
        sp.setMaxWorkerCount(params.getMaxWorkerCount());

        // Welcome message
        sp.setWelcomeMessageConfiguration(new WelcomeMessageConfiguration(params.getWelcomeMessageDisplayMode()));

        // Log level override
        if (params.getLogLevelOverride() != null) {
            sp.setLogLevel(params.getLogLevelOverride());
        }

        // Tasks
        sp.setTaskRequests(params.getTaskRequests());

        // Layout
        if (params.getProjectDir() != null) {
            sp.setProjectDir(params.getProjectDir());
        }
        sp.setCurrentDir(params.getCurrentDir());
        sp.setGradleUserHomeDir(params.getGradleUserHomeDir());
        if (params.getGradleHomeDir() != null) {
            sp.setGradleHomeDir(params.getGradleHomeDir());
        }

        sp.setProjectProperties(params.getProjectProperties());
        sp.setSystemPropertiesArgs(params.getSystemPropertiesArgs());

        Transformer<File, String> resolver = new BasicFileResolver(params.getCurrentDir());

        if (params.getProjectCacheDir() != null) {
            sp.setProjectCacheDir(resolver.transform(params.getProjectCacheDir()));
        }
        if (params.getInitScripts() != null) {
            for (String script : params.getInitScripts()) {
                sp.addInitScript(resolver.transform(script));
            }
        }
        if (params.getExcludedTaskNames() != null) {
            sp.setExcludedTaskNames(params.getExcludedTaskNames());
        }
        if (params.getIncludedBuilds() != null) {
            for (String includedBuild : params.getIncludedBuilds()) {
                sp.includeBuild(resolver.transform(includedBuild));
            }
        }
        if (params.getBuildProjectDependencies() != null) {
            sp.setBuildProjectDependencies(params.getBuildProjectDependencies());
        }
        if (params.getDryRun() != null) {
            sp.setDryRun(params.getDryRun());
        }
        if (params.getRerunTasks() != null) {
            sp.setRerunTasks(params.getRerunTasks());
        }
        if (params.getProfile() != null) {
            sp.setProfile(params.getProfile());
        }
        if (params.getContinueOnFailure() != null) {
            sp.setContinueOnFailure(params.getContinueOnFailure());
        }
        if (params.getOffline() != null) {
            sp.setOffline(params.getOffline());
        }
        if (params.getRefreshDependencies() != null) {
            sp.setRefreshDependencies(params.getRefreshDependencies());
        }
        if (params.getBuildCacheEnabled() != null) {
            sp.setBuildCacheEnabled(params.getBuildCacheEnabled());
        }
        if (params.getBuildCacheDebugLogging() != null) {
            sp.setBuildCacheDebugLogging(params.getBuildCacheDebugLogging());
        }
        if (params.getWatchFileSystemMode() != null) {
            sp.setWatchFileSystemMode(params.getWatchFileSystemMode());
        }
        if (params.getVfsVerboseLogging() != null) {
            sp.setVfsVerboseLogging(params.getVfsVerboseLogging());
        }
        if (params.getConfigurationCache() != null) {
            sp.setConfigurationCache(Option.Value.value(params.getConfigurationCache()));
        }
        if (params.getIsolatedProjects() != null) {
            sp.setIsolatedProjects(Option.Value.value(params.getIsolatedProjects()));
        }
        if (params.getConfigurationCacheProblems() != null) {
            sp.setConfigurationCacheProblems(params.getConfigurationCacheProblems());
        }
        if (params.getConfigurationCacheIgnoreInputsDuringStore() != null) {
            sp.setConfigurationCacheIgnoreInputsDuringStore(params.getConfigurationCacheIgnoreInputsDuringStore());
        }
        if (params.getConfigurationCacheIgnoreUnsupportedBuildEventsListeners() != null) {
            sp.setConfigurationCacheIgnoreUnsupportedBuildEventsListeners(params.getConfigurationCacheIgnoreUnsupportedBuildEventsListeners());
        }
        if (params.getConfigurationCacheMaxProblems() != null) {
            sp.setConfigurationCacheMaxProblems(params.getConfigurationCacheMaxProblems());
        }
        if (params.getConfigurationCacheIgnoredFileSystemCheckInputs() != null) {
            sp.setConfigurationCacheIgnoredFileSystemCheckInputs(params.getConfigurationCacheIgnoredFileSystemCheckInputs());
        }
        if (params.getConfigurationCacheDebug() != null) {
            sp.setConfigurationCacheDebug(params.getConfigurationCacheDebug());
        }
        if (params.getConfigurationCacheRecreateCache() != null) {
            sp.setConfigurationCacheRecreateCache(params.getConfigurationCacheRecreateCache());
        }
        if (params.getConfigurationCacheParallel() != null) {
            sp.setConfigurationCacheParallel(params.getConfigurationCacheParallel());
        }
        if (params.getConfigurationCacheReadOnly() != null) {
            sp.setConfigurationCacheReadOnly(params.getConfigurationCacheReadOnly());
        }
        if (params.getConfigurationCacheQuiet() != null) {
            sp.setConfigurationCacheQuiet(params.getConfigurationCacheQuiet());
        }
        if (params.getConfigurationCacheIntegrityCheckEnabled() != null) {
            sp.setConfigurationCacheIntegrityCheckEnabled(params.getConfigurationCacheIntegrityCheckEnabled());
        }
        if (params.getConfigurationCacheEntriesPerKey() != null) {
            sp.setConfigurationCacheEntriesPerKey(params.getConfigurationCacheEntriesPerKey());
        }
        if (params.getConfigurationCacheHeapDumpDir() != null) {
            sp.setConfigurationCacheHeapDumpDir(params.getConfigurationCacheHeapDumpDir());
        }
        if (params.getConfigurationCacheFineGrainedPropertyTracking() != null) {
            sp.setConfigurationCacheFineGrainedPropertyTracking(params.getConfigurationCacheFineGrainedPropertyTracking());
        }
        if (params.getConfigureOnDemand() != null) {
            sp.setConfigureOnDemand(params.getConfigureOnDemand());
        }
        if (params.getContinuous() != null) {
            sp.setContinuous(params.getContinuous());
        }
        if (params.getContinuousBuildQuietPeriod() != null) {
            sp.setContinuousBuildQuietPeriod(params.getContinuousBuildQuietPeriod());
        }
        if (params.getBuildScan() != null) {
            if (params.getBuildScan()) {
                sp.setBuildScan(true);
            } else {
                sp.setNoBuildScan(true);
            }
        }
        if (params.getWriteDependencyLocks() != null) {
            sp.setWriteDependencyLocks(params.getWriteDependencyLocks());
        }
        if (params.getWriteDependencyVerifications() != null) {
            sp.setWriteDependencyVerifications(params.getWriteDependencyVerifications());
        }
        if (params.getDependencyVerificationMode() != null) {
            sp.setDependencyVerificationMode(params.getDependencyVerificationMode());
        }
        if (params.getLockedDependenciesToUpdate() != null) {
            sp.setLockedDependenciesToUpdate(params.getLockedDependenciesToUpdate());
        }
        if (params.getRefreshKeys() != null) {
            sp.setRefreshKeys(params.getRefreshKeys());
        }
        if (params.getExportKeys() != null) {
            sp.setExportKeys(params.getExportKeys());
        }
        if (params.getPropertyUpgradeReportEnabled() != null) {
            sp.setPropertyUpgradeReportEnabled(params.getPropertyUpgradeReportEnabled());
        }
        if (params.getProblemReportGenerationEnabled() != null) {
            sp.enableProblemReportGeneration(params.getProblemReportGenerationEnabled());
        }
        if (params.getTaskGraph() != null) {
            sp.setTaskGraph(params.getTaskGraph());
        }
        if (params.getParallelToolingModelBuilding() != null) {
            sp.setParallelToolingModelBuilding(Option.Value.value(params.getParallelToolingModelBuilding()));
        }
        if (params.getDevelocityUrl() != null) {
            sp.setDevelocityUrl(params.getDevelocityUrl());
        }
        if (params.getDevelocityPluginVersion() != null) {
            sp.setDevelocityPluginVersion(params.getDevelocityPluginVersion());
        }

        return sp;
    }

    @Override
    public StartParameterInternal newInstance() {
        return (StartParameterInternal) prepareNewInstance(new StartParameterInternal());
    }

    @Override
    public StartParameterInternal newBuild() {
        return prepareNewBuild(new StartParameterInternal());
    }

    @Override
    protected StartParameterInternal prepareNewBuild(StartParameter startParameter) {
        StartParameterInternal p = (StartParameterInternal) super.prepareNewBuild(startParameter);
        p.watchFileSystemMode = watchFileSystemMode;
        p.vfsVerboseLogging = vfsVerboseLogging;
        p.configurationCache = configurationCache;
        p.isolatedProjects = isolatedProjects;
        p.configurationCacheProblems = configurationCacheProblems;
        p.configurationCacheMaxProblems = configurationCacheMaxProblems;
        p.configurationCacheIgnoredFileSystemCheckInputs = configurationCacheIgnoredFileSystemCheckInputs;
        p.configurationCacheIgnoreUnsupportedBuildEventsListeners = configurationCacheIgnoreUnsupportedBuildEventsListeners;
        p.configurationCacheDebug = configurationCacheDebug;
        p.configurationCacheParallel = configurationCacheParallel;
        p.configurationCacheReadOnly = configurationCacheReadOnly;
        p.configurationCacheRecreateCache = configurationCacheRecreateCache;
        p.configurationCacheQuiet = configurationCacheQuiet;
        p.configurationCacheEntriesPerKey = configurationCacheEntriesPerKey;
        p.configurationCacheIntegrityCheckEnabled = configurationCacheIntegrityCheckEnabled;
        p.configurationCacheHeapDumpDir = configurationCacheHeapDumpDir;
        p.configurationCacheFineGrainedPropertyTracking = configurationCacheFineGrainedPropertyTracking;
        p.searchUpwards = searchUpwards;
        p.useEmptySettings = useEmptySettings;
        p.enableProblemReportGeneration = enableProblemReportGeneration;
        p.parallelToolingModelBuilding = parallelToolingModelBuilding;
        return p;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Map<String, String> getProjectProperties() {
        // We avoid using the more usual `Instrumented` directly because a class dependency on it bloats up the Shaded TAPI Jar
        InstrumentedInputs.listener().startParameterProjectPropertiesObserved();
        return super.getProjectProperties();
    }

    /**
     * Returns the properties without making their snapshot a build input for Configuration Caching purposes.
     * <p>
     * This should be used with care because failing to track properties can lead to false-positive cache hits.
     */
    public Map<String, String> getProjectPropertiesUntracked() {
        return super.getProjectProperties();
    }

    public File getGradleHomeDir() {
        return gradleHomeDir;
    }

    public void setGradleHomeDir(File gradleHomeDir) {
        this.gradleHomeDir = gradleHomeDir;
    }

    public boolean isSearchUpwards() {
        return searchUpwards;
    }

    public void doNotSearchUpwards() {
        this.searchUpwards = false;
    }

    public boolean isUseEmptySettings() {
        return useEmptySettings;
    }

    public void useEmptySettings() {
        this.useEmptySettings = true;
    }

    public WatchMode getWatchFileSystemMode() {
        return watchFileSystemMode;
    }

    public void setWatchFileSystemMode(WatchMode watchFileSystemMode) {
        this.watchFileSystemMode = watchFileSystemMode;
    }

    public boolean isVfsVerboseLogging() {
        return vfsVerboseLogging;
    }

    public void setVfsVerboseLogging(boolean vfsVerboseLogging) {
        this.vfsVerboseLogging = vfsVerboseLogging;
    }

    /**
     * Is the configuration cache requested? Note: depending on the build action, this may not be the final value for this option.
     *
     * Consider querying {@link BuildModelParameters} instead.
     */
    public Option.Value<Boolean> getConfigurationCache() {
        return configurationCache;
    }

    public void setConfigurationCache(Option.Value<Boolean> configurationCache) {
        this.configurationCache = configurationCache;
    }

    public Option.Value<Boolean> getIsolatedProjects() {
        return isolatedProjects;
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isConfigurationCacheRequested() {
        StartParameterDeprecations.nagOnIsConfigurationCacheRequested();
        return configurationCache.get();
    }

    public void setIsolatedProjects(Option.Value<Boolean> isolatedProjects) {
        this.isolatedProjects = isolatedProjects;
    }

    public ConfigurationCacheProblemsMode getConfigurationCacheProblems() {
        return configurationCacheProblems;
    }

    public void setConfigurationCacheProblems(ConfigurationCacheProblemsMode configurationCacheProblems) {
        this.configurationCacheProblems = configurationCacheProblems;
    }

    public boolean isConfigurationCacheDebug() {
        return configurationCacheDebug;
    }

    public void setConfigurationCacheDebug(boolean configurationCacheDebug) {
        this.configurationCacheDebug = configurationCacheDebug;
    }

    public boolean isConfigurationCacheIgnoreInputsDuringStore() {
        return configurationCacheIgnoreInputsDuringStore;
    }

    public void setConfigurationCacheIgnoreInputsDuringStore(boolean ignoreInputsDuringStore) {
        configurationCacheIgnoreInputsDuringStore = ignoreInputsDuringStore;
    }

    public void setConfigurationCacheIgnoreUnsupportedBuildEventsListeners(boolean configurationCacheIgnoreUnsupportedBuildEventsListeners) {
        this.configurationCacheIgnoreUnsupportedBuildEventsListeners = configurationCacheIgnoreUnsupportedBuildEventsListeners;
    }

    public boolean isConfigurationCacheIgnoreUnsupportedBuildEventsListeners() {
        return configurationCacheIgnoreUnsupportedBuildEventsListeners;
    }

    public boolean isConfigurationCacheParallel() {
        return configurationCacheParallel;
    }

    public void setConfigurationCacheParallel(boolean parallel) {
        this.configurationCacheParallel = parallel;
    }

    public boolean isConfigurationCacheReadOnly() {
        return configurationCacheReadOnly;
    }

    public void setConfigurationCacheReadOnly(boolean readOnly) {
        this.configurationCacheReadOnly = readOnly;
    }

    public int getConfigurationCacheEntriesPerKey() {
        return configurationCacheEntriesPerKey;
    }

    public void setConfigurationCacheEntriesPerKey(int configurationCacheEntriesPerKey) {
        this.configurationCacheEntriesPerKey = configurationCacheEntriesPerKey;
    }

    public int getConfigurationCacheMaxProblems() {
        return configurationCacheMaxProblems;
    }

    public void setConfigurationCacheMaxProblems(int configurationCacheMaxProblems) {
        this.configurationCacheMaxProblems = configurationCacheMaxProblems;
    }

    @Nullable
    public String getConfigurationCacheIgnoredFileSystemCheckInputs() {
        return configurationCacheIgnoredFileSystemCheckInputs;
    }

    public void setConfigurationCacheIgnoredFileSystemCheckInputs(@Nullable String configurationCacheIgnoredFileSystemCheckInputs) {
        this.configurationCacheIgnoredFileSystemCheckInputs = configurationCacheIgnoredFileSystemCheckInputs;
    }

    public boolean isConfigurationCacheRecreateCache() {
        return configurationCacheRecreateCache;
    }

    public void setConfigurationCacheRecreateCache(boolean configurationCacheRecreateCache) {
        this.configurationCacheRecreateCache = configurationCacheRecreateCache;
    }

    public boolean isConfigurationCacheQuiet() {
        return configurationCacheQuiet;
    }

    public void setConfigurationCacheQuiet(boolean configurationCacheQuiet) {
        this.configurationCacheQuiet = configurationCacheQuiet;
    }

    public void setConfigurationCacheIntegrityCheckEnabled(boolean configurationCacheIntegrityCheck) {
        this.configurationCacheIntegrityCheckEnabled = configurationCacheIntegrityCheck;
    }

    public boolean isConfigurationCacheIntegrityCheckEnabled() {
        return configurationCacheIntegrityCheckEnabled;
    }

    public void setConfigurationCacheHeapDumpDir(@Nullable String configurationCacheHeapDumpDir) {
        this.configurationCacheHeapDumpDir = configurationCacheHeapDumpDir;
    }

    public @Nullable String getConfigurationCacheHeapDumpDir() {
        return configurationCacheHeapDumpDir;
    }

    public void setConfigurationCacheFineGrainedPropertyTracking(boolean configurationCacheFineGrainedPropertyTracking) {
        this.configurationCacheFineGrainedPropertyTracking = configurationCacheFineGrainedPropertyTracking;
    }

    public boolean isConfigurationCacheFineGrainedPropertyTracking() {
        return configurationCacheFineGrainedPropertyTracking;
    }

    public void setContinuousBuildQuietPeriod(Duration continuousBuildQuietPeriod) {
        this.continuousBuildQuietPeriod = continuousBuildQuietPeriod;
    }

    public Duration getContinuousBuildQuietPeriod() {
        return continuousBuildQuietPeriod;
    }

    public boolean isPropertyUpgradeReportEnabled() {
        return propertyUpgradeReportEnabled;
    }

    public void setPropertyUpgradeReportEnabled(boolean propertyUpgradeReportEnabled) {
        this.propertyUpgradeReportEnabled = propertyUpgradeReportEnabled;
    }

    public void enableProblemReportGeneration(boolean enableProblemReportGeneration) {
        this.enableProblemReportGeneration = enableProblemReportGeneration;
    }

    public boolean isProblemReportGenerationEnabled() {
        return this.enableProblemReportGeneration;
    }

    public Option.Value<Boolean> getParallelToolingModelBuilding() {
        return parallelToolingModelBuilding;
    }

    public void setParallelToolingModelBuilding(Option.Value<Boolean> parallelToolingModelBuilding) {
        this.parallelToolingModelBuilding = parallelToolingModelBuilding;
    }

    @Nullable
    public String getDevelocityUrl() {
        return develocityUrl;
    }

    public void setDevelocityUrl(@Nullable String develocityUrl) {
        this.develocityUrl = develocityUrl;
    }

    @Nullable
    public String getDevelocityPluginVersion() {
        return develocityPluginVersion;
    }

    public void setDevelocityPluginVersion(@Nullable String develocityPluginVersion) {
        this.develocityPluginVersion = develocityPluginVersion;
    }

    public BuildLayoutConfiguration toBuildLayoutConfiguration() {
        return new BuildLayoutConfiguration(getCurrentDir(), isSearchUpwards(), isUseEmptySettings());
    }
}
