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
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption;
import org.gradle.initialization.layout.BuildLayoutConfiguration;
import org.gradle.internal.buildoption.Option;
import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.configuration.inputs.InstrumentedInputs;
import org.gradle.internal.deprecation.StartParameterDeprecations;
import org.gradle.internal.watch.registry.WatchMode;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

public class StartParameterInternal extends StartParameter {
    private WatchMode watchFileSystemMode = WatchMode.DEFAULT;
    private boolean vfsVerboseLogging;

    private Option.Value<Boolean> configurationCache = Option.Value.defaultValue(false);
    private Option.Value<Boolean> isolatedProjects = Option.Value.defaultValue(false);
    private ConfigurationCacheProblemsOption.Value configurationCacheProblems = ConfigurationCacheProblemsOption.Value.FAIL;
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
    private boolean isolatedProjectsDiagnostics = false;
    private boolean isolatedProjectsDangerouslyIgnoreProblems = false;
    private boolean searchUpwards = true;
    private boolean useEmptySettings = false;
    private Duration continuousBuildQuietPeriod = Duration.ofMillis(250);
    private boolean propertyUpgradeReportEnabled;
    private boolean enableProblemReportGeneration = true;
    private boolean daemonJvmCriteriaConfigured = false;
    private Option.Value<Boolean> parallelToolingModelBuilding = Option.Value.defaultValue(false);
    private @Nullable String develocityUrl;
    private @Nullable String develocityPluginVersion;
    // Runtime-only wiring, deliberately transient: a StartParameter captured in task state must be
    // serializable to the configuration cache without dragging the listener (and its services) along.
    private transient @Nullable Consumer<String> mutationListener;

    public StartParameterInternal() {
    }

    protected StartParameterInternal(BuildLayoutParameters layoutParameters) {
        super(layoutParameters);
    }

    /**
     * Arms the listener invoked on every subsequent mutating call (see {@link #onMutableCall}). The
     * build tree installs it once settings have been evaluated, so that later mutations of the running
     * build's start parameter can be reported as Isolated Projects violations. Throws if a listener is
     * already set, to catch a stale one surviving from an earlier build into a reused start parameter.
     */
    public void setMutationListener(Consumer<String> mutationListener) {
        if (this.mutationListener != null) {
            throw new IllegalStateException("Mutation listener already set on StartParameterInternal");
        }
        this.mutationListener = mutationListener;
    }

    /**
     * Disarms the listener, so the start parameter is mutable again without reporting. Used when a start
     * parameter outlives the build that armed it, e.g. across iterations of a continuous build.
     */
    public void clearMutationListener() {
        this.mutationListener = null;
    }

    @Override
    protected void onMutableCall(String methodSignature) {
        Consumer<String> listener = this.mutationListener;
        if (listener != null) {
            listener.accept(methodSignature);
        }
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
        p.isolatedProjectsDiagnostics = isolatedProjectsDiagnostics;
        p.isolatedProjectsDangerouslyIgnoreProblems = isolatedProjectsDangerouslyIgnoreProblems;
        p.searchUpwards = searchUpwards;
        p.useEmptySettings = useEmptySettings;
        p.enableProblemReportGeneration = enableProblemReportGeneration;
        p.daemonJvmCriteriaConfigured = daemonJvmCriteriaConfigured;
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
        onMutableCall("setGradleHomeDir(File)");
        this.gradleHomeDir = gradleHomeDir;
    }

    public boolean isSearchUpwards() {
        return searchUpwards;
    }

    public void doNotSearchUpwards() {
        onMutableCall("doNotSearchUpwards()");
        this.searchUpwards = false;
    }

    public boolean isUseEmptySettings() {
        return useEmptySettings;
    }

    public void useEmptySettings() {
        onMutableCall("useEmptySettings()");
        this.useEmptySettings = true;
    }

    public WatchMode getWatchFileSystemMode() {
        return watchFileSystemMode;
    }

    public void setWatchFileSystemMode(WatchMode watchFileSystemMode) {
        onMutableCall("setWatchFileSystemMode(WatchMode)");
        this.watchFileSystemMode = watchFileSystemMode;
    }

    public boolean isVfsVerboseLogging() {
        return vfsVerboseLogging;
    }

    public void setVfsVerboseLogging(boolean vfsVerboseLogging) {
        onMutableCall("setVfsVerboseLogging(boolean)");
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
        onMutableCall("setConfigurationCache(Option.Value)");
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
        onMutableCall("setIsolatedProjects(Option.Value)");
        this.isolatedProjects = isolatedProjects;
    }

    public ConfigurationCacheProblemsOption.Value getConfigurationCacheProblems() {
        return configurationCacheProblems;
    }

    public void setConfigurationCacheProblems(ConfigurationCacheProblemsOption.Value configurationCacheProblems) {
        onMutableCall("setConfigurationCacheProblems(ConfigurationCacheProblemsOption.Value)");
        this.configurationCacheProblems = configurationCacheProblems;
    }

    public boolean isConfigurationCacheDebug() {
        return configurationCacheDebug;
    }

    public void setConfigurationCacheDebug(boolean configurationCacheDebug) {
        onMutableCall("setConfigurationCacheDebug(boolean)");
        this.configurationCacheDebug = configurationCacheDebug;
    }

    public boolean isConfigurationCacheIgnoreInputsDuringStore() {
        return configurationCacheIgnoreInputsDuringStore;
    }

    public void setConfigurationCacheIgnoreInputsDuringStore(boolean ignoreInputsDuringStore) {
        onMutableCall("setConfigurationCacheIgnoreInputsDuringStore(boolean)");
        configurationCacheIgnoreInputsDuringStore = ignoreInputsDuringStore;
    }

    public void setConfigurationCacheIgnoreUnsupportedBuildEventsListeners(boolean configurationCacheIgnoreUnsupportedBuildEventsListeners) {
        onMutableCall("setConfigurationCacheIgnoreUnsupportedBuildEventsListeners(boolean)");
        this.configurationCacheIgnoreUnsupportedBuildEventsListeners = configurationCacheIgnoreUnsupportedBuildEventsListeners;
    }

    public boolean isConfigurationCacheIgnoreUnsupportedBuildEventsListeners() {
        return configurationCacheIgnoreUnsupportedBuildEventsListeners;
    }

    public boolean isConfigurationCacheParallel() {
        return configurationCacheParallel;
    }

    public void setConfigurationCacheParallel(boolean parallel) {
        onMutableCall("setConfigurationCacheParallel(boolean)");
        this.configurationCacheParallel = parallel;
    }

    public boolean isConfigurationCacheReadOnly() {
        return configurationCacheReadOnly;
    }

    public void setConfigurationCacheReadOnly(boolean readOnly) {
        onMutableCall("setConfigurationCacheReadOnly(boolean)");
        this.configurationCacheReadOnly = readOnly;
    }

    public int getConfigurationCacheEntriesPerKey() {
        return configurationCacheEntriesPerKey;
    }

    public void setConfigurationCacheEntriesPerKey(int configurationCacheEntriesPerKey) {
        onMutableCall("setConfigurationCacheEntriesPerKey(int)");
        this.configurationCacheEntriesPerKey = configurationCacheEntriesPerKey;
    }

    public int getConfigurationCacheMaxProblems() {
        return configurationCacheMaxProblems;
    }

    public void setConfigurationCacheMaxProblems(int configurationCacheMaxProblems) {
        onMutableCall("setConfigurationCacheMaxProblems(int)");
        this.configurationCacheMaxProblems = configurationCacheMaxProblems;
    }

    @Nullable
    public String getConfigurationCacheIgnoredFileSystemCheckInputs() {
        return configurationCacheIgnoredFileSystemCheckInputs;
    }

    public void setConfigurationCacheIgnoredFileSystemCheckInputs(@Nullable String configurationCacheIgnoredFileSystemCheckInputs) {
        onMutableCall("setConfigurationCacheIgnoredFileSystemCheckInputs(String)");
        this.configurationCacheIgnoredFileSystemCheckInputs = configurationCacheIgnoredFileSystemCheckInputs;
    }

    public boolean isConfigurationCacheRecreateCache() {
        return configurationCacheRecreateCache;
    }

    public void setConfigurationCacheRecreateCache(boolean configurationCacheRecreateCache) {
        onMutableCall("setConfigurationCacheRecreateCache(boolean)");
        this.configurationCacheRecreateCache = configurationCacheRecreateCache;
    }

    public boolean isConfigurationCacheQuiet() {
        return configurationCacheQuiet;
    }

    public void setConfigurationCacheQuiet(boolean configurationCacheQuiet) {
        onMutableCall("setConfigurationCacheQuiet(boolean)");
        this.configurationCacheQuiet = configurationCacheQuiet;
    }

    public void setConfigurationCacheIntegrityCheckEnabled(boolean configurationCacheIntegrityCheck) {
        onMutableCall("setConfigurationCacheIntegrityCheckEnabled(boolean)");
        this.configurationCacheIntegrityCheckEnabled = configurationCacheIntegrityCheck;
    }

    public boolean isConfigurationCacheIntegrityCheckEnabled() {
        return configurationCacheIntegrityCheckEnabled;
    }

    public void setConfigurationCacheHeapDumpDir(@Nullable String configurationCacheHeapDumpDir) {
        onMutableCall("setConfigurationCacheHeapDumpDir(String)");
        this.configurationCacheHeapDumpDir = configurationCacheHeapDumpDir;
    }

    public @Nullable String getConfigurationCacheHeapDumpDir() {
        return configurationCacheHeapDumpDir;
    }

    public void setConfigurationCacheFineGrainedPropertyTracking(boolean configurationCacheFineGrainedPropertyTracking) {
        onMutableCall("setConfigurationCacheFineGrainedPropertyTracking(boolean)");
        this.configurationCacheFineGrainedPropertyTracking = configurationCacheFineGrainedPropertyTracking;
    }

    public boolean isConfigurationCacheFineGrainedPropertyTracking() {
        return configurationCacheFineGrainedPropertyTracking;
    }

    public boolean isIsolatedProjectsDiagnostics() {
        return isolatedProjectsDiagnostics;
    }

    public void setIsolatedProjectsDiagnostics(boolean isolatedProjectsDiagnostics) {
        onMutableCall("setIsolatedProjectsDiagnostics(boolean)");
        this.isolatedProjectsDiagnostics = isolatedProjectsDiagnostics;
    }

    public boolean isIsolatedProjectsDangerouslyIgnoreProblems() {
        return isolatedProjectsDangerouslyIgnoreProblems;
    }

    public void setIsolatedProjectsDangerouslyIgnoreProblems(boolean isolatedProjectsDangerouslyIgnoreProblems) {
        onMutableCall("setIsolatedProjectsDangerouslyIgnoreProblems(boolean)");
        this.isolatedProjectsDangerouslyIgnoreProblems = isolatedProjectsDangerouslyIgnoreProblems;
    }

    public void setContinuousBuildQuietPeriod(Duration continuousBuildQuietPeriod) {
        onMutableCall("setContinuousBuildQuietPeriod(Duration)");
        this.continuousBuildQuietPeriod = continuousBuildQuietPeriod;
    }

    public Duration getContinuousBuildQuietPeriod() {
        return continuousBuildQuietPeriod;
    }

    public boolean isPropertyUpgradeReportEnabled() {
        return propertyUpgradeReportEnabled;
    }

    public void setPropertyUpgradeReportEnabled(boolean propertyUpgradeReportEnabled) {
        onMutableCall("setPropertyUpgradeReportEnabled(boolean)");
        this.propertyUpgradeReportEnabled = propertyUpgradeReportEnabled;
    }

    public void enableProblemReportGeneration(boolean enableProblemReportGeneration) {
        onMutableCall("enableProblemReportGeneration(boolean)");
        this.enableProblemReportGeneration = enableProblemReportGeneration;
    }

    public boolean isProblemReportGenerationEnabled() {
        return this.enableProblemReportGeneration;
    }

    public boolean isDaemonJvmCriteriaConfigured() {
        return daemonJvmCriteriaConfigured;
    }

    public void setDaemonJvmCriteriaConfigured(boolean daemonJvmCriteriaConfigured) {
        onMutableCall("setDaemonJvmCriteriaConfigured(boolean)");
        this.daemonJvmCriteriaConfigured = daemonJvmCriteriaConfigured;
    }

    public Option.Value<Boolean> getParallelToolingModelBuilding() {
        return parallelToolingModelBuilding;
    }

    public void setParallelToolingModelBuilding(Option.Value<Boolean> parallelToolingModelBuilding) {
        onMutableCall("setParallelToolingModelBuilding(Option.Value)");
        this.parallelToolingModelBuilding = parallelToolingModelBuilding;
    }

    @Nullable
    public String getDevelocityUrl() {
        return develocityUrl;
    }

    public void setDevelocityUrl(@Nullable String develocityUrl) {
        onMutableCall("setDevelocityUrl(String)");
        this.develocityUrl = develocityUrl;
    }

    @Nullable
    public String getDevelocityPluginVersion() {
        return develocityPluginVersion;
    }

    public void setDevelocityPluginVersion(@Nullable String develocityPluginVersion) {
        onMutableCall("setDevelocityPluginVersion(String)");
        this.develocityPluginVersion = develocityPluginVersion;
    }

    public BuildLayoutConfiguration toBuildLayoutConfiguration() {
        return new BuildLayoutConfiguration(getCurrentDir(), isSearchUpwards(), isUseEmptySettings());
    }
}
