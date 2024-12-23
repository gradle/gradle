/*
 * Copyright 2024 the original author or authors.
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
package org.gradle.initialization.location;

import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.internal.FileUtils;
import org.gradle.internal.scripts.DefaultScriptFileResolver;
import org.gradle.internal.scripts.ScriptFileResolver;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;

@ServiceScope(Scope.Global.class)
public class BuildLocationFactory {

    private static final String DEFAULT_SETTINGS_FILE_BASENAME = "settings";
    private final ScriptFileResolver scriptFileResolver;

    @Inject
    public BuildLocationFactory(ScriptFileResolver scriptFileResolver) {
        this.scriptFileResolver = scriptFileResolver;
    }

    public BuildLocationFactory() {
        this(new DefaultScriptFileResolver());
    }

    /**
     * Determines the location of the build, given a current directory and some other configuration.
     */
    public BuildLocation getLocationFor(File currentDir, boolean shouldSearchUpwards) {
        boolean searchUpwards = shouldSearchUpwards && !isBuildSrc(currentDir);
        return getLocationFor(currentDir, searchUpwards ? null : currentDir.getParentFile());
    }

    private boolean isBuildSrc(File currentDir) {
        return currentDir.getName().equals(SettingsInternal.BUILD_SRC);
    }

    /**
     * Determines the layout of the build, given a current directory and some other configuration.
     */
    public BuildLocation getLocationFor(BuildLocationConfiguration configuration) {
        if (configuration.isUseEmptySettings()) {
            return buildLocationFrom(configuration, null);
        }
        File explicitSettingsFile = configuration.getSettingsFile();
        if (explicitSettingsFile != null) {
            return buildLocationFrom(configuration, explicitSettingsFile);
        }

        return getLocationFor(configuration.getCurrentDir(), configuration.isSearchUpwards());
    }

    private BuildLocation buildLocationFrom(BuildLocationConfiguration configuration, File settingsFile) {
        return new BuildLocation(configuration.getCurrentDir(), settingsFile, scriptFileResolver);
    }

    @Nullable
    public File findExistingSettingsFileIn(File directory) {
        return scriptFileResolver.resolveScriptFile(directory, DEFAULT_SETTINGS_FILE_BASENAME);
    }

    BuildLocation getLocationFor(File currentDir, File stopAt) {
        File settingsFile = findExistingSettingsFileIn(currentDir);
        if (settingsFile != null) {
            return buildLocation(settingsFile);
        }
        for (File candidate = currentDir.getParentFile(); candidate != null && !candidate.equals(stopAt); candidate = candidate.getParentFile()) {
            settingsFile = findExistingSettingsFileIn(candidate);
            if (settingsFile != null) {
                return buildLocation(settingsFile);
            }
        }
        return buildLocation(new File(currentDir, Settings.DEFAULT_SETTINGS_FILE));
    }

    private BuildLocation buildLocation(File settingsFile) {
        return new BuildLocation(settingsFile.getParentFile(), FileUtils.canonicalize(settingsFile), scriptFileResolver);
    }
}
