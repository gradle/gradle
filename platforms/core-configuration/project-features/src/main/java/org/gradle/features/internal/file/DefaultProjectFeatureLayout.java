/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.features.internal.file;

import org.gradle.features.file.ProjectFeatureLayout;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

import java.io.File;

/**
 * Default implementation of {@link ProjectFeatureLayout} which delegates to {@link ProjectLayout}.
 */
public class DefaultProjectFeatureLayout implements ProjectFeatureLayout {
    private final ProjectLayout projectLayout;

    public DefaultProjectFeatureLayout(ProjectLayout projectLayout) {
        this.projectLayout = projectLayout;
    }

    @Override
    public Directory getProjectDirectory() {
        return projectLayout.getProjectDirectory();
    }

    @Override
    public Directory getSettingsDirectory() {
        return projectLayout.getSettingsDirectory();
    }

    @Override
    public Provider<Directory> getContextBuildDirectory() {
        // TODO implement context-specific build directory
        return projectLayout.getBuildDirectory().map(directory -> directory);
    }

    @Override
    public Provider<RegularFile> file(Provider<File> file) {
        return projectLayout.file(file);
    }

    @Override
    public Provider<Directory> dir(Provider<File> file) {
        return projectLayout.dir(file);
    }
}
