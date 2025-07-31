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

import org.gradle.internal.FileUtils;
import org.gradle.internal.initialization.BuildLogicFiles;
import org.gradle.internal.scripts.DefaultScriptFileResolver;
import org.gradle.internal.scripts.ScriptFileResolver;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;

@ServiceScope(Scope.Global.class)
public class BuildLayoutFactory {

    private final ScriptFileResolver scriptFileResolver;

    @Inject
    public BuildLayoutFactory(ScriptFileResolver scriptFileResolver) {
        this.scriptFileResolver = scriptFileResolver;
    }

    public BuildLayoutFactory() {
        this(new DefaultScriptFileResolver());
    }

    /**
     * Determines the layout of the build, given a current directory and some other configuration.
     */
    public BuildLayout getLayoutFor(File currentDir, boolean shouldSearchUpwards) {
        boolean searchUpwards = shouldSearchUpwards && !isBuildSrc(currentDir);
        BuildLayout layout = searchUpwards ? findLayoutRecursively(currentDir) : findLayout(currentDir);
        return layout != null ? layout : getLayoutWithDefaultSettingsFile(currentDir);
    }

    /**
     * Determines the layout of the build, given a current directory and some other configuration.
     */
    public BuildLayout getLayoutFor(BuildLayoutConfiguration configuration) {
        if (configuration.isUseEmptySettings()) {
            return layout(configuration.getCurrentDir(), null);
        }
        return getLayoutFor(configuration.getCurrentDir(), configuration.isSearchUpwards());
    }

    @Nullable
    public File findExistingSettingsFileIn(File directory) {
        return scriptFileResolver.resolveScriptFile(directory, BuildLogicFiles.SETTINGS_FILE_BASENAME);
    }

    @Nullable
    private BuildLayout findLayoutRecursively(File dir) {
        while (dir != null) {
            BuildLayout layout = findLayout(dir);
            if (layout != null) {
                return layout;
            }
            dir = dir.getParentFile();
        }
        return null;
    }

    @Nullable
    private BuildLayout findLayout(File dir) {
        File settingsFile = findExistingSettingsFileIn(dir);
        return settingsFile != null ? layout(dir, settingsFile) : null;
    }

    private BuildLayout getLayoutWithDefaultSettingsFile(File dir) {
        return layout(dir, new File(dir, BuildLogicFiles.DEFAULT_SETTINGS_FILE));
    }

    private BuildLayout layout(File rootDir, @Nullable File settingsFile) {
        File canonicalSettingsFile = settingsFile != null ? FileUtils.canonicalize(settingsFile) : null;
        return new BuildLayout(rootDir, canonicalSettingsFile, scriptFileResolver);
    }

    private static boolean isBuildSrc(File dir) {
        return dir.getName().equals(BuildLogicFiles.BUILD_SOURCE_DIRECTORY);
    }
}
