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
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.resources.MissingResourceException;
import org.gradle.internal.FileUtils;
import org.gradle.internal.scripts.DefaultScriptFileResolver;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.io.File;

@ServiceScope(Scope.Global.class)
public class BuildLayoutFactory {

    private static final String DEFAULT_SETTINGS_FILE_BASENAME = "settings";
    private final DefaultScriptFileResolver scriptFileResolver = new DefaultScriptFileResolver();

    /**
     * Determines the layout of the build, given a current directory and some other configuration.
     */
    public BuildLayout getLayoutFor(File currentDir, boolean shouldSearchUpwards) {
        boolean searchUpwards = shouldSearchUpwards && !isBuildSrc(currentDir);
        return getLayoutFor(currentDir, searchUpwards ? null : currentDir.getParentFile());
    }

    private boolean isBuildSrc(File currentDir) {
        return currentDir.getName().equals(SettingsInternal.BUILD_SRC);
    }

    /**
     * Determines the layout of the build, given a current directory and some other configuration.
     */
    public BuildLayout getLayoutFor(BuildLayoutConfiguration configuration) {
        if (configuration.isUseEmptySettings()) {
            return buildLayoutFrom(configuration, null);
        }
        File explicitSettingsFile = configuration.getSettingsFile();
        if (explicitSettingsFile != null) {
            if (!explicitSettingsFile.isFile()) {
                throw new MissingResourceException(explicitSettingsFile.toURI(), String.format("Could not read settings file '%s' as it does not exist.", explicitSettingsFile.getAbsolutePath()));
            }
            return buildLayoutFrom(configuration, explicitSettingsFile);
        }

        return getLayoutFor(configuration.getCurrentDir(), configuration.isSearchUpwards());
    }

    private BuildLayout buildLayoutFrom(BuildLayoutConfiguration configuration, File settingsFile) {
        return new BuildLayout(configuration.getCurrentDir(), configuration.getCurrentDir(), settingsFile, scriptFileResolver);
    }

    @Nullable
    public File findExistingSettingsFileIn(File directory) {
        return scriptFileResolver.resolveScriptFile(directory, DEFAULT_SETTINGS_FILE_BASENAME);
    }

    BuildLayout getLayoutFor(File currentDir, File stopAt) {
        File settingsFile = findExistingSettingsFileIn(currentDir);
        if (settingsFile != null) {
            return layout(currentDir, settingsFile);
        }
        for (File candidate = currentDir.getParentFile(); candidate != null && !candidate.equals(stopAt); candidate = candidate.getParentFile()) {
            settingsFile = findExistingSettingsFileIn(candidate);
            if (settingsFile != null) {
                return layout(candidate, settingsFile);
            }
        }
        return layout(currentDir, new File(currentDir, Settings.DEFAULT_SETTINGS_FILE));
    }

    private BuildLayout layout(File rootDir, File settingsFile) {
        return new BuildLayout(rootDir, settingsFile.getParentFile(), FileUtils.canonicalize(settingsFile), scriptFileResolver);
    }
}
