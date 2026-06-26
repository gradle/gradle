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
import org.gradle.internal.initialization.BuildLocation;
import org.gradle.internal.initialization.BuildLogicFiles;
import org.gradle.internal.scripts.DefaultScriptFileResolver;
import org.gradle.internal.scripts.ScriptFileResolver;
import org.gradle.internal.scripts.ScriptResolutionResult;
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
     * Determines the location of the build, given a current directory and some other configuration.
     */
    public BuildLocation locationFor(File currentDir, boolean shouldSearchUpwards) {
        boolean searchUpwards = shouldSearchUpwards && !isBuildSrc(currentDir);
        BuildLocation location = searchUpwards ? findLocationRecursively(currentDir) : findLocation(currentDir);
        return location != null ? location : getLocationWithDefaultSettingsFile(currentDir);
    }

    /**
     * Determines the location of the build, given a current directory and some other configuration.
     */
    public BuildLocation locationFor(BuildLayoutConfiguration configuration) {
        if (configuration.isUseEmptySettings()) {
            return location(configuration.getCurrentDir(), null);
        }
        return locationFor(configuration.getCurrentDir(), configuration.isSearchUpwards());
    }

    /**
     * Determines the layout of the build, given a current directory and some other configuration.
     *
     * @deprecated Use {@link #locationFor(File, boolean)} instead. {@link BuildLayout} is a deprecated service.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public BuildLayout getLayoutFor(File currentDir, boolean shouldSearchUpwards) {
        return new BuildLayout(locationFor(currentDir, shouldSearchUpwards));
    }

    /**
     * Determines the layout of the build, given a current directory and some other configuration.
     *
     * @deprecated Use {@link #locationFor(BuildLayoutConfiguration)} instead. {@link BuildLayout} is a deprecated service.
     */
    @Deprecated
    @SuppressWarnings("deprecation")
    public BuildLayout getLayoutFor(BuildLayoutConfiguration configuration) {
        return new BuildLayout(locationFor(configuration));
    }

    @Nullable
    private BuildLocation findLocationRecursively(File dir) {
        while (dir != null) {
            BuildLocation location = findLocation(dir);
            if (location != null) {
                return location;
            }
            dir = dir.getParentFile();
        }
        return null;
    }

    @Nullable
    private BuildLocation findLocation(File dir) {
        ScriptResolutionResult resolutionResult = scriptFileResolver.resolveScriptFile(dir, BuildLogicFiles.SETTINGS_FILE_BASENAME);
        if (resolutionResult.isScriptFound()) {
            return location(dir, resolutionResult);
        } else {
            return null;
        }
    }

    private BuildLocation getLocationWithDefaultSettingsFile(File dir) {
        ScriptResolutionResult resolution = ScriptResolutionResult.fromSingleFile(
            BuildLogicFiles.SETTINGS_FILE_BASENAME,
            FileUtils.canonicalize(new File(dir, BuildLogicFiles.DEFAULT_SETTINGS_FILE))
        );
        return location(dir, resolution);
    }

    private BuildLocation location(File rootDir, @Nullable ScriptResolutionResult settingsFileResolution) {
        return new BuildLocation(rootDir, settingsFileResolution, scriptFileResolver);
    }

    private static boolean isBuildSrc(File dir) {
        return dir.getName().equals(BuildLogicFiles.BUILD_SOURCE_DIRECTORY);
    }
}
