/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.initialization.layout;

import org.gradle.StartParameter;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.annotation.Nullable;
import java.io.File;

/**
 * Configuration which affects the (static) layout of a build.
 */
public class BuildLayoutConfiguration {
    private final File currentDir;
    private final boolean searchUpwards;
    private final File settingsFile;
    private final File buildFile;
    private final boolean useEmptySettings;

    public BuildLayoutConfiguration(StartParameter startParameter) {
        currentDir = startParameter.getCurrentDir();
        searchUpwards = ((StartParameterInternal)startParameter).isSearchUpwards();
        @SuppressWarnings("deprecation")
        File customSettingsFile = DeprecationLogger.whileDisabled(startParameter::getSettingsFile);
        this.settingsFile = customSettingsFile;
        @SuppressWarnings("deprecation")
        File customBuildFile = DeprecationLogger.whileDisabled(startParameter::getBuildFile);
        this.buildFile = customBuildFile;
        useEmptySettings = ((StartParameterInternal)startParameter).isUseEmptySettings();
    }

    public BuildLayoutConfiguration(BuildLayoutParameters parameters) {
        this.currentDir = parameters.getCurrentDir();
        this.searchUpwards = true;
        this.settingsFile = parameters.getSettingsFile();
        this.buildFile = parameters.getBuildFile();
        this.useEmptySettings = false;
    }

    public File getCurrentDir() {
        return currentDir;
    }

    public boolean isSearchUpwards() {
        return searchUpwards;
    }

    /**
     * When null, use the default. When not null, use the given value.
     */
    @Nullable
    public File getSettingsFile() {
        return settingsFile;
    }

    @Nullable
    public File getBuildFile() {
        return buildFile;
    }

    public boolean isUseEmptySettings() {
        return useEmptySettings;
    }
}
