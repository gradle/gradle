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

import org.gradle.internal.FileUtils;
import org.gradle.internal.scripts.DefaultScriptFileResolver;
import org.gradle.internal.scripts.ScriptFileResolver;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;

/**
 * Finds the location of a build on the file system.
 * <p>
 * The root of the build is normally identified as the directory housing the settings script.
 *
 * @see BuildLocations
 */
@ServiceScope(Scope.Global.class)
public class BuildLocator {

    private final ScriptFileResolver scriptFileResolver;

    @Inject
    public BuildLocator(ScriptFileResolver scriptFileResolver) {
        this.scriptFileResolver = scriptFileResolver;
    }

    public BuildLocator() {
        this(new DefaultScriptFileResolver());
    }

    /**
     * Resolves the location of the build, checking parent directories if necessary.
     *
     * @return root directory of the build
     */
    public File findBuildRootDirectory(BuildDiscoveryParameters parameters) {
        return findBuild(parameters).getBuildRootDirectory();
    }

    /**
     * Resolves the location of the build, checking parent directories if necessary.
     */
    public BuildLocations findBuild(BuildDiscoveryParameters parameters) {
        if (parameters.isUseEmptySettings()) {
            return layout(parameters.getTargetDirectory(), null);
        }
        return findBuild(parameters.getTargetDirectory(), parameters.isSearchUpwards());
    }

    /**
     * Resolves the location of the build, checking parent directories if necessary.
     */
    public BuildLocations findBuild(File targetDirectory, boolean shouldSearchUpwards) {
        boolean searchUpwards = shouldSearchUpwards && !isBuildSrc(targetDirectory);
        BuildLocations layout = searchUpwards ? findLayoutRecursively(targetDirectory) : findLayout(targetDirectory);
        return layout != null ? layout : getLayoutWithDefaultSettingsFile(targetDirectory);
    }

    @Nullable
    public File findExistingSettingsFileIn(File directory) {
        return scriptFileResolver.resolveScriptFile(directory, BuildLogicFiles.SETTINGS_FILE_BASENAME);
    }

    @Nullable
    private BuildLocations findLayoutRecursively(File dir) {
        while (dir != null) {
            BuildLocations layout = findLayout(dir);
            if (layout != null) {
                return layout;
            }
            dir = dir.getParentFile();
        }
        return null;
    }

    @Nullable
    private BuildLocations findLayout(File dir) {
        File settingsFile = findExistingSettingsFileIn(dir);
        return settingsFile != null ? layout(dir, settingsFile) : null;
    }

    private BuildLocations getLayoutWithDefaultSettingsFile(File dir) {
        return layout(dir, new File(dir, BuildLogicFiles.DEFAULT_SETTINGS_FILE));
    }

    private BuildLocations layout(File rootDir, @Nullable File settingsFile) {
        File canonicalSettingsFile = settingsFile != null ? FileUtils.canonicalize(settingsFile) : null;
        return new BuildLocations(rootDir, canonicalSettingsFile, scriptFileResolver);
    }

    private static boolean isBuildSrc(File dir) {
        return dir.getName().equals(BuildLogicFiles.BUILD_SOURCE_DIRECTORY);
    }
}
