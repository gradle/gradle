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
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.io.File;

/**
 * Location of a build represented as its {@link #getBuildRootDirectory() root directory}.
 */
@ServiceScope(Scope.Build.class)
public class BuildLocations {

    private final File buildRootDirectory;
    @Nullable
    private final File settingsFile;
    private final ScriptFileResolver scriptFileResolver;

    public BuildLocations(File buildRootDirectory, @Nullable File settingsFile, ScriptFileResolver scriptFileResolver) {
        this.buildRootDirectory = buildRootDirectory;
        this.settingsFile = settingsFile;
        this.scriptFileResolver = scriptFileResolver;
    }

    /**
     * Root directory of the build.
     * <p>
     * It is the same as the settings directory.
     * <p>
     * Note that this directory can technically differ from the <code>Project.rootDir</code>, because the latter is mutable during settings
     * via the <code>ProjectDescriptor</code> of the root project.
     */
    public File getBuildRootDirectory() {
        return buildRootDirectory;
    }

    /**
     * Settings script file or null if using "empty settings".
     * <p>
     * When not null, the file might not exist on disk, in which case it is treated as a file with empty contents.
     */
    @Nullable
    public File getSettingsFile() {
        return settingsFile;
    }

    /**
     * Was a build definition found?
     */
    public boolean isBuildDefinitionMissing() {
        return settingsFile != null && !settingsFile.exists() && scriptFileResolver.resolveScriptFile(buildRootDirectory, BuildLogicFiles.BUILD_FILE_BASENAME) == null;
    }
}
