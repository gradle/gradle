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
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption;

import java.io.File;

public class StartParameterInternal extends StartParameter {
    private boolean watchFileSystem;
    private boolean watchFileSystemDebugLogging;
    private boolean watchFileSystemUsingDeprecatedOption;
    private boolean vfsVerboseLogging;

    private boolean configurationCache;
    private ConfigurationCacheProblemsOption.Value configurationCacheProblems = ConfigurationCacheProblemsOption.Value.FAIL;
    private int configurationCacheMaxProblems = 512;
    private boolean configurationCacheRecreateCache;
    private boolean configurationCacheQuiet;

    @Override
    public StartParameter newInstance() {
        return prepareNewInstance(new StartParameterInternal());
    }

    @Override
    public StartParameter newBuild() {
        return prepareNewBuild(new StartParameterInternal());
    }

    @Override
    protected StartParameter prepareNewBuild(StartParameter startParameter) {
        StartParameterInternal p = (StartParameterInternal) super.prepareNewBuild(startParameter);
        p.watchFileSystem = watchFileSystem;
        p.watchFileSystemDebugLogging = watchFileSystemDebugLogging;
        p.watchFileSystemUsingDeprecatedOption = watchFileSystemUsingDeprecatedOption;
        p.vfsVerboseLogging = vfsVerboseLogging;
        p.configurationCache = configurationCache;
        p.configurationCacheProblems = configurationCacheProblems;
        p.configurationCacheMaxProblems = configurationCacheMaxProblems;
        p.configurationCacheRecreateCache = configurationCacheRecreateCache;
        p.configurationCacheQuiet = configurationCacheQuiet;
        return p;
    }

    public File getGradleHomeDir() {
        return gradleHomeDir;
    }

    public void setGradleHomeDir(File gradleHomeDir) {
        this.gradleHomeDir = gradleHomeDir;
    }

    public void useEmptySettingsWithoutDeprecationWarning() {
        doUseEmptySettings();
    }

    public boolean isUseEmptySettingsWithoutDeprecationWarning() {
        return super.useEmptySettings;
    }

    public boolean isSearchUpwardsWithoutDeprecationWarning() {
        return super.searchUpwards;
    }

    public void setSearchUpwardsWithoutDeprecationWarning(boolean searchUpwards) {
        super.searchUpwards = searchUpwards;
    }

    public boolean isWatchFileSystem() {
        return watchFileSystem;
    }

    public void setWatchFileSystem(boolean watchFileSystem) {
        this.watchFileSystem = watchFileSystem;
    }

    public boolean isWatchFileSystemDebugLogging() {
        return watchFileSystemDebugLogging;
    }

    public void setWatchFileSystemDebugLogging(boolean watchFileSystemDebugLogging) {
        this.watchFileSystemDebugLogging = watchFileSystemDebugLogging;
    }

    public boolean isWatchFileSystemUsingDeprecatedOption() {
        return watchFileSystemUsingDeprecatedOption;
    }

    public void setWatchFileSystemUsingDeprecatedOption(boolean watchFileSystemUsingDeprecatedOption) {
        this.watchFileSystemUsingDeprecatedOption = watchFileSystemUsingDeprecatedOption;
    }

    public boolean isVfsVerboseLogging() {
        return vfsVerboseLogging;
    }

    public void setVfsVerboseLogging(boolean vfsVerboseLogging) {
        this.vfsVerboseLogging = vfsVerboseLogging;
    }

    public boolean isConfigurationCache() {
        return configurationCache;
    }

    public void setConfigurationCache(boolean configurationCache) {
        this.configurationCache = configurationCache;
    }

    public ConfigurationCacheProblemsOption.Value getConfigurationCacheProblems() {
        return configurationCacheProblems;
    }

    public void setConfigurationCacheProblems(ConfigurationCacheProblemsOption.Value configurationCacheProblems) {
        this.configurationCacheProblems = configurationCacheProblems;
    }

    public int getConfigurationCacheMaxProblems() {
        return configurationCacheMaxProblems;
    }

    public void setConfigurationCacheMaxProblems(int configurationCacheMaxProblems) {
        this.configurationCacheMaxProblems = configurationCacheMaxProblems;
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
}
