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
package org.gradle.internal.initialization.layout;

import org.gradle.internal.initialization.BuildLogicFiles;
import org.gradle.internal.scripts.ScriptFileResolver;
import org.gradle.internal.scripts.ScriptResolutionResult;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Optional;

/// Location of a build represented as its [root directory][#getBuildRootDirectory()].
@ServiceScope(Scope.Build.class)
public class BuildLocations {

    private final File buildRootDirectory;
    @Nullable
    private final ScriptResolutionResult settingsFileResolution;
    private final ScriptFileResolver scriptFileResolver;

    /// Creates locations for a build rooted at the given directory.
    ///
    /// A `null` settings file resolution means explicitly no settings.
    /// A non-null value can point to a non-existent file, which is semantically equivalent to an empty file.
    public BuildLocations(File buildRootDirectory, @Nullable ScriptResolutionResult settingsFileResolution, ScriptFileResolver scriptFileResolver) {
        this.buildRootDirectory = buildRootDirectory;
        this.settingsFileResolution = settingsFileResolution;
        this.scriptFileResolver = scriptFileResolver;
    }

    /// Root directory of the build.
    ///
    /// It is the same as the settings directory.
    ///
    /// Note that this directory can technically differ from the `Project.rootDir`, because the latter is mutable during settings
    /// via the `ProjectDescriptor` of the root project.
    public File getBuildRootDirectory() {
        return buildRootDirectory;
    }

    /// Resolution result for the settings script, or `null` if using "empty settings".
    @Nullable
    public ScriptResolutionResult getSettingsFileResolution() {
        return settingsFileResolution;
    }

    /// Settings script file or `null` if using "empty settings".
    ///
    /// When not `null`, the file might not exist on disk, in which case it is treated as a file with empty contents.
    ///
    /// Note that this file might still not be the effective settings file, because under certain conditions
    /// the build can switch to empty settings despite a valid settings file being present.
    /// See the `DefaultSettingsPreparer` logic for details.
    @Nullable
    public File getSettingsFile() {
        return Optional
            .ofNullable(settingsFileResolution)
            .map(ScriptResolutionResult::getSelectedCandidate)
            .orElse(null);
    }

    /// Was a build definition found?
    public boolean isBuildDefinitionMissing() {
        File settingsFile = getSettingsFile();
        return settingsFile != null &&
            !settingsFile.exists() &&
            !scriptFileResolver.resolveScriptFile(buildRootDirectory, BuildLogicFiles.BUILD_FILE_BASENAME).isScriptFound();
    }
}
