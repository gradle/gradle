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

import javax.annotation.Nullable;
import org.gradle.api.initialization.Settings;
import org.gradle.api.resources.MissingResourceException;
import org.gradle.internal.FileUtils;
import org.gradle.internal.scan.UsedByScanPlugin;
import org.gradle.internal.scripts.DefaultScriptFileResolver;
import org.gradle.internal.scripts.ScriptFileResolver;

import java.io.File;

@UsedByScanPlugin
public class BuildLayoutFactory {

    /**
     * Constructs a {@code BuildLayoutFactory} that searches for script files
     * matching all available scripting languages.
     *
     * @see ScriptFileResolver
     */
    public static BuildLayoutFactory forDefaultScriptingLanguages() {
        return new BuildLayoutFactory();
    }

    private static final String DEFAULT_SETTINGS_FILE_BASENAME = "settings";

    private final ScriptFileResolver scriptFileResolver;

    public BuildLayoutFactory(ScriptFileResolver scriptFileResolver) {
        this.scriptFileResolver = scriptFileResolver;
    }

    /**
     * This constructor should not be used in Gradle.
     * Its sole purpose is backwards compatibility with the build scan plugin.
     *
     * {@code BuildLayoutFactory} should be either consumed as a service or instantiated via
     * {@link #forDefaultScriptingLanguages()}.
     */
    @Deprecated
    public BuildLayoutFactory() {
        this(DefaultScriptFileResolver.forDefaultScriptingLanguages());
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
            return buildLayoutFrom(configuration, null);
        }
        File explicitSettingsFile = configuration.getSettingsFile();
        if (explicitSettingsFile != null) {
            if (!explicitSettingsFile.isFile()) {
                throw new MissingResourceException(explicitSettingsFile.toURI(), String.format("Could not read settings file '%s' as it does not exist.", explicitSettingsFile.getAbsolutePath()));
            }
            return buildLayoutFrom(configuration, explicitSettingsFile);
        }

        File currentDir = configuration.getCurrentDir();
        boolean searchUpwards = configuration.isSearchUpwards();
        return getLayoutFor(currentDir, searchUpwards ? null : currentDir.getParentFile());
    }

    private BuildLayout buildLayoutFrom(BuildLayoutConfiguration configuration, File settingsFile) {
        return new BuildLayout(configuration.getCurrentDir(), configuration.getCurrentDir(), settingsFile);
    }

    @Nullable
    public File findExistingSettingsFileIn(File directory) {
        File defaultSettingsFile = new File(directory, Settings.DEFAULT_SETTINGS_FILE);
        return defaultSettingsFile.isFile()
            ? defaultSettingsFile
            : scriptFileResolver.resolveScriptFile(directory, DEFAULT_SETTINGS_FILE_BASENAME);
    }

    BuildLayout getLayoutFor(File currentDir, File stopAt) {
        File settingsFile = findExistingSettingsFileIn(currentDir);
        if (settingsFile != null) {
            return layout(currentDir, settingsFile);
        }
        for (File candidate = currentDir.getParentFile(); candidate != null && !candidate.equals(stopAt); candidate = candidate.getParentFile()) {
            settingsFile = findExistingSettingsFileIn(candidate);
            if (settingsFile == null) {
                settingsFile = findExistingSettingsFileIn(new File(candidate, "master"));
            }
            if (settingsFile != null) {
                return layout(candidate, settingsFile);
            }
        }
        return layout(currentDir, new File(currentDir, Settings.DEFAULT_SETTINGS_FILE));
    }

    private BuildLayout layout(File rootDir, File settingsFile) {
        return new BuildLayout(rootDir, settingsFile.getParentFile(), FileUtils.canonicalize(settingsFile));
    }
}
