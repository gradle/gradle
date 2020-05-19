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
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption;
import org.gradle.internal.deprecation.Deprecatable;
import org.gradle.internal.deprecation.LoggingDeprecatable;

import java.io.File;
import java.util.Set;

public class StartParameterInternal extends StartParameter implements Deprecatable {

    private final Deprecatable deprecationHandler = new LoggingDeprecatable();

    private boolean watchFileSystem;

    private ConfigurationCacheOption.Value configurationCache = ConfigurationCacheOption.Value.OFF;
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
        p.configurationCache = configurationCache;
        p.configurationCacheMaxProblems = configurationCacheMaxProblems;
        p.configurationCacheRecreateCache = configurationCacheRecreateCache;
        p.configurationCacheQuiet = configurationCacheQuiet;
        return p;
    }

    @Override
    public void addDeprecation(String deprecation) {
        deprecationHandler.addDeprecation(deprecation);
    }

    @Override
    public Set<String> getDeprecations() {
        return deprecationHandler.getDeprecations();
    }

    @Override
    public void checkDeprecation() {
        deprecationHandler.checkDeprecation();
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

    public ConfigurationCacheOption.Value getConfigurationCache() {
        return configurationCache;
    }

    public void setConfigurationCache(ConfigurationCacheOption.Value configurationCache) {
        this.configurationCache = configurationCache;
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
