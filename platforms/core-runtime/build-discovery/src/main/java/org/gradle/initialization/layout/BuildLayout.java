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

import org.gradle.internal.initialization.BuildLocations;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;

/**
 * @implNote Despite not being part of the public API, this service is known to have been used by users.
 * So we treat its removal as a breaking change.
 * @deprecated Instead, use {@link org.gradle.api.file.BuildLayout#getSettingsDirectory()} for settings or {@link org.gradle.api.file.ProjectLayout#getSettingsDirectory()} for project.
 */
@Deprecated
@ServiceScope(Scope.Build.class)
@SuppressWarnings("deprecation")
public class BuildLayout extends org.gradle.initialization.SettingsLocation {

    public BuildLayout(BuildLocations buildLocations) {
        super(buildLocations);
    }

    /**
     * Was a build definition found?
     */
    public boolean isBuildDefinitionMissing() {
        return buildLocations.isBuildDefinitionMissing();
    }

    /**
     * Returns the root directory of the build, is never null.
     */
    public File getRootDirectory() {
        return buildLocations.getBuildRootDirectory();
    }
}
