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
package org.gradle.internal.initialization.layout;

import org.gradle.internal.FileUtils;
import org.gradle.internal.initialization.BuildLogicFiles;
import org.gradle.internal.scripts.DefaultScriptFileResolver;
import org.gradle.internal.scripts.ScriptFileResolver;
import org.gradle.internal.scripts.ScriptResolutionResult;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;

/// Finds the location of a build on the file system.
///
/// The root of the build is normally identified as the directory housing the settings script.
///
/// @see BuildLocations
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

    /// Resolves the root directory of the build, checking parent directories if necessary.
    public File findBuildRootDirectory(BuildDiscoveryParameters parameters) {
        return findBuild(parameters).getBuildRootDirectory();
    }

    /// Resolves the location of the build, checking parent directories if necessary.
    public BuildLocations findBuild(BuildDiscoveryParameters parameters) {
        if (parameters.isUseEmptySettings()) {
            return locations(parameters.getTargetDirectory(), null);
        }
        return findBuild(parameters.getTargetDirectory(), parameters.isSearchUpwards());
    }

    /// Resolves the location of the build, checking parent directories if necessary.
    public BuildLocations findBuild(File targetDirectory, boolean shouldSearchUpwards) {
        boolean searchUpwards = shouldSearchUpwards && !isBuildSrc(targetDirectory);
        BuildLocations locations = searchUpwards ? findLocationsRecursively(targetDirectory) : findLocations(targetDirectory);
        return locations != null ? locations : getLocationsWithDefaultSettingsFile(targetDirectory);
    }

    @Nullable
    private BuildLocations findLocationsRecursively(File dir) {
        while (dir != null) {
            BuildLocations locations = findLocations(dir);
            if (locations != null) {
                return locations;
            }
            dir = dir.getParentFile();
        }
        return null;
    }

    @Nullable
    private BuildLocations findLocations(File dir) {
        ScriptResolutionResult resolutionResult = scriptFileResolver.resolveScriptFile(dir, BuildLogicFiles.SETTINGS_FILE_BASENAME);
        if (resolutionResult.isScriptFound()) {
            return locations(dir, resolutionResult);
        } else {
            return null;
        }
    }

    private BuildLocations getLocationsWithDefaultSettingsFile(File dir) {
        ScriptResolutionResult resolution = ScriptResolutionResult.fromSingleFile(
            BuildLogicFiles.SETTINGS_FILE_BASENAME,
            FileUtils.canonicalize(new File(dir, BuildLogicFiles.DEFAULT_SETTINGS_FILE))
        );
        return locations(dir, resolution);
    }

    private BuildLocations locations(File rootDir, @Nullable ScriptResolutionResult settingsFileResolution) {
        return new BuildLocations(rootDir, settingsFileResolution, scriptFileResolver);
    }

    private static boolean isBuildSrc(File dir) {
        return dir.getName().equals(BuildLogicFiles.BUILD_SOURCE_DIRECTORY);
    }
}
