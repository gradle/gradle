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

import org.gradle.internal.scripts.ScriptFileResolver;

import javax.annotation.Nullable;
import java.io.File;

import static org.gradle.initialization.DefaultProjectDescriptor.BUILD_SCRIPT_BASENAME;

public class BuildLocation {

    private final File buildDefinitionDirectory;
    @Nullable
    private final File settingsFile;
    private final ScriptFileResolver scriptFileResolver;

    /**
     * TODO
     *
     * Note: `null` for `settingsFile` means explicitly no settings
     *   A non null value can be a non existent file, which is semantically equivalent to an empty file
     */
     public BuildLocation(File buildDefinitionDirectory, @Nullable File settingsFile, ScriptFileResolver scriptFileResolver) {
        this.settingsFile = settingsFile;
        this.buildDefinitionDirectory = buildDefinitionDirectory;
        this.scriptFileResolver = scriptFileResolver;
    }

    /**
     * Was a build definition found?
     */
    public boolean isBuildDefinitionMissing() {
        return settingsFile != null && !settingsFile.exists() && scriptFileResolver.resolveScriptFile(buildDefinitionDirectory, BUILD_SCRIPT_BASENAME) == null;
    }

    /**
     * TODO
     */
    public File getBuildDefinitionDirectory() {
        return buildDefinitionDirectory;
    }

    /**
     * Returns the settings file. May be null, which mean "no settings file" rather than "use default settings file location".
     */
    @Nullable
    public File getSettingsFile() {
        return settingsFile;
    }
}
