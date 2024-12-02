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
import org.gradle.internal.buildoption.Option;
import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.watch.registry.WatchMode;

import javax.annotation.Nullable;
import java.io.File;
import java.time.Duration;

public class StartParameterInternal extends StartParameter {
    private WatchMode watchFileSystemMode = WatchMode.DEFAULT;
    private boolean vfsVerboseLogging;

    private Option.Value<Boolean> configurationCache = Option.Value.defaultValue(false);
    private Option.Value<Boolean> isolatedProjects = Option.Value.defaultValue(false);
    private ConfigurationCacheProblemsOption.Value configurationCacheProblems = ConfigurationCacheProblemsOption.Value.FAIL;
    private boolean configurationCacheDebug;
    private boolean configurationCacheIgnoreInputsDuringStore = false;
    private int configurationCacheMaxProblems = 512;
    private @Nullable String configurationCacheIgnoredFileSystemCheckInputs = null;
    private boolean configurationCacheParallel;
    private boolean configurationCacheRecreateCache;
    private boolean configurationCacheQuiet;
    private boolean searchUpwards = true;
    private boolean useEmptySettings = false;
    private Duration continuousBuildQuietPeriod = Duration.ofMillis(250);
    private boolean propertyUpgradeReportEnabled;
    private boolean enableProblemReportGeneration = true;

    public StartParameterInternal() {
    }

    protected StartParameterInternal(BuildLayoutParameters layoutParameters) {
        super(layoutParameters);
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
        p.configurationCacheDebug = configurationCacheDebug;
        p.configurationCacheParallel = configurationCacheParallel;
        p.configurationCacheRecreateCache = configurationCacheRecreateCache;
        p.configurationCacheQuiet = configurationCacheQuiet;
        p.searchUpwards = searchUpwards;
        p.useEmptySettings = useEmptySettings;
        p.enableProblemReportGeneration = enableProblemReportGeneration;
        return p;
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
     * Used by the Kotlin plugin, via reflection.
     */
    @Deprecated
    public boolean isConfigurationCache() {
        return getConfigurationCache().get();
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
        return configurationCache.get();
    }

    public void setIsolatedProjects(Option.Value<Boolean> isolatedProjects) {
        this.isolatedProjects = isolatedProjects;
    }

    public ConfigurationCacheProblemsOption.Value getConfigurationCacheProblems() {
        return configurationCacheProblems;
    }

    public void setConfigurationCacheProblems(ConfigurationCacheProblemsOption.Value configurationCacheProblems) {
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

    public boolean isConfigurationCacheParallel() {
        return configurationCacheParallel;
    }

    public void setConfigurationCacheParallel(boolean parallel) {
        this.configurationCacheParallel = parallel;
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
}
