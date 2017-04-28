/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.initialization.Settings;
import org.gradle.api.resources.MissingResourceException;
import org.gradle.internal.scripts.ScriptFileResolver;

import java.io.File;

public class BuildLayoutFactory {

    private final ScriptFileResolver scriptFileResolver;

    public BuildLayoutFactory(ScriptFileResolver scriptFileResolver) {
        this.scriptFileResolver = scriptFileResolver;
    }

    /**
     * Determines the layout of the build, given a current directory and some other configuration.
     */
    public BuildLayout getLayoutFor(File currentDir, boolean searchUpwards) {
        return getLayoutFor(currentDir, searchUpwards ? null : currentDir.getParentFile());
    }

    /**
     * Determines the layout of the build, given a current directory and some other configuration.
     */
    public BuildLayout getLayoutFor(BuildLayoutConfiguration configuration) {
        if (configuration.isUseEmptySettings()) {
            return new BuildLayout(configuration.getCurrentDir(), configuration.getCurrentDir(), null);
        }
        File explicitSettingsFile = configuration.getSettingsFile();
        if (explicitSettingsFile != null) {
            if (!explicitSettingsFile.isFile()) {
                throw new MissingResourceException(explicitSettingsFile.toURI(), String.format("Could not read settings file '%s' as it does not exist.", explicitSettingsFile.getAbsolutePath()));
            }
            return new BuildLayout(configuration.getCurrentDir(), configuration.getCurrentDir(), explicitSettingsFile);
        }

        File currentDir = configuration.getCurrentDir();
        boolean searchUpwards = configuration.isSearchUpwards();
        return getLayoutFor(currentDir, searchUpwards ? null : currentDir.getParentFile());
    }

    BuildLayout getLayoutFor(File currentDir, File stopAt) {
        File settingsFile = findExistingSettingsFileIn(currentDir);
        if (settingsFile != null) {
            return layout(currentDir, currentDir, settingsFile);
        }
        for (File candidate = currentDir.getParentFile(); candidate != null && !candidate.equals(stopAt); candidate = candidate.getParentFile()) {
            settingsFile = findExistingSettingsFileIn(candidate);
            if (settingsFile == null) {
                settingsFile = findExistingSettingsFileIn(new File(candidate, "master"));
            }
            if (settingsFile != null) {
                return layout(candidate, settingsFile.getParentFile(), settingsFile);
            }
        }
        return layout(currentDir, currentDir, new File(currentDir, Settings.DEFAULT_SETTINGS_FILE));
    }

    private BuildLayout layout(File rootDir, File settingsDir, File settingsFile) {
        return new BuildLayout(rootDir, settingsDir, settingsFile);
    }

    private File findExistingSettingsFileIn(File directory) {
        File defaultSettingsFile = new File(directory, Settings.DEFAULT_SETTINGS_FILE);
        if (defaultSettingsFile.isFile()) {
            return defaultSettingsFile;
        }
        return scriptFileResolver.resolveScriptFile(directory, Settings.DEFAULT_SETTINGS_FILE_BASENAME);
    }
}
