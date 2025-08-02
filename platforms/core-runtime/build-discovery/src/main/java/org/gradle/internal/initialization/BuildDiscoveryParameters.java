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
package org.gradle.internal.initialization;

import org.gradle.initialization.BuildLayoutParameters;

import java.io.File;

/**
 * Target directory and other parameters to determine the location of the build.
 *
 * @see BuildLocator
 */
public class BuildDiscoveryParameters {

    private final File targetDirectory;
    private final boolean searchUpwards;
    private final boolean useEmptySettings;

    public BuildDiscoveryParameters(File targetDirectory, boolean searchUpwards, boolean useEmptySettings) {
        this.targetDirectory = targetDirectory;
        this.searchUpwards = searchUpwards;
        this.useEmptySettings = useEmptySettings;
    }

    public BuildDiscoveryParameters(BuildLayoutParameters parameters) {
        this.targetDirectory = parameters.getCurrentDir();
        this.searchUpwards = true;
        this.useEmptySettings = false;
    }

    public File getTargetDirectory() {
        return targetDirectory;
    }

    public boolean isSearchUpwards() {
        return searchUpwards;
    }

    public boolean isUseEmptySettings() {
        return useEmptySettings;
    }
}
