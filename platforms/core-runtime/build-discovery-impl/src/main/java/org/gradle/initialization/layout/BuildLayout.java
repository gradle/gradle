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

import org.gradle.initialization.SettingsLocation;
import org.gradle.internal.initialization.BuildLogicFiles;
import org.gradle.internal.scripts.ScriptFileResolver;
import org.gradle.internal.scripts.ScriptResolutionResult;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.io.File;

@ServiceScope(Scope.Build.class)
public class BuildLayout extends SettingsLocation {
    private final ScriptFileResolver scriptFileResolver;

    /**
     * Creates a new build layout.
     *
     * @param rootDirectory the root directory of the build
     * @param settingsFileResolution the settings file resolution result. {@code null} means explicitly no settings.
     * A non-null value can be a non-existent file, which is semantically equivalent to an empty file.
     * @param scriptFileResolver the script file resolver
     */
    public BuildLayout(File rootDirectory, @Nullable ScriptResolutionResult settingsFileResolution, ScriptFileResolver scriptFileResolver) {
        super(rootDirectory, settingsFileResolution);
        this.scriptFileResolver = scriptFileResolver;
    }

    /**
     * Was a build definition found?
     */
    public boolean isBuildDefinitionMissing() {
        return getSettingsFile() != null &&
            !getSettingsFile().exists() &&
            !scriptFileResolver.resolveScriptFile(getRootDirectory(), BuildLogicFiles.BUILD_FILE_BASENAME).isScriptFound();
    }

    /**
     * Returns the root directory of the build, is never null.
     */
    public File getRootDirectory() {
        return getSettingsDir();
    }
}
