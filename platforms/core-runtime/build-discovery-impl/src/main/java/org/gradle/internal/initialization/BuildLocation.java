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

import org.gradle.internal.scripts.ScriptFileResolver;
import org.gradle.internal.scripts.ScriptResolutionResult;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Optional;

/**
 * Location of a build on the file system, represented by its {@link #getBuildRootDirectory() root directory}.
 * <p>
 * This is the internal companion to the build-tree-scoped {@code BuildTreeLocations} service.
 * It is the non-deprecated replacement for the {@code org.gradle.initialization.layout.BuildLayout}
 * and {@code org.gradle.initialization.SettingsLocation} services, which are deprecated because they
 * are easily mistaken for the public {@link org.gradle.api.file.BuildLayout} service.
 *
 * @see org.gradle.internal.initialization.layout.BuildTreeLocations
 */
@ServiceScope(Scope.Build.class)
public class BuildLocation {

    private final File buildRootDirectory;
    @Nullable
    private final ScriptResolutionResult settingsFileResolution;
    private final ScriptFileResolver scriptFileResolver;

    /**
     * Creates a new build location.
     *
     * @param buildRootDirectory the root directory of the build
     * @param settingsFileResolution the settings file resolution result. {@code null} means explicitly no settings.
     * A non-null value can resolve to a non-existent file, which is semantically equivalent to an empty file.
     * @param scriptFileResolver the script file resolver
     */
    public BuildLocation(File buildRootDirectory, @Nullable ScriptResolutionResult settingsFileResolution, ScriptFileResolver scriptFileResolver) {
        this.buildRootDirectory = buildRootDirectory;
        this.settingsFileResolution = settingsFileResolution;
        this.scriptFileResolver = scriptFileResolver;
    }

    /**
     * Root directory of the build.
     * <p>
     * It is the same as the settings directory.
     * <p>
     * Note that this directory can technically differ from the {@code Project.rootDir}, because the latter is mutable
     * during settings via the {@code ProjectDescriptor} of the root project.
     */
    public File getBuildRootDirectory() {
        return buildRootDirectory;
    }

    /**
     * Returns the resolution result for the settings file, or {@code null} when using "empty settings".
     */
    @Nullable
    public ScriptResolutionResult getSettingsFileResolution() {
        return settingsFileResolution;
    }

    /**
     * Settings script file or {@code null} when using "empty settings".
     * <p>
     * When not null, the file might not exist on disk, in which case it is treated as a file with empty contents.
     */
    @Nullable
    public File getSettingsFile() {
        return Optional
            .ofNullable(settingsFileResolution)
            .map(ScriptResolutionResult::getSelectedCandidate)
            .orElse(null);
    }

    /**
     * Was a build definition found?
     */
    public boolean isBuildDefinitionMissing() {
        File settingsFile = getSettingsFile();
        return settingsFile != null &&
            !settingsFile.exists() &&
            !scriptFileResolver.resolveScriptFile(buildRootDirectory, BuildLogicFiles.BUILD_FILE_BASENAME).isScriptFound();
    }
}
