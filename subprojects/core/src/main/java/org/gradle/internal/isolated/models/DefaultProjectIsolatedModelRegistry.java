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

package org.gradle.internal.isolated.models;

import org.gradle.api.internal.project.ProjectIdentity;
import org.gradle.api.isolated.models.ProjectIsolatedModelRegistry;
import org.gradle.api.provider.Provider;
import org.gradle.util.Path;

public class DefaultProjectIsolatedModelRegistry implements ProjectIsolatedModelRegistry {

    private final IsolatedModelScope producerScope;

    public DefaultProjectIsolatedModelRegistry(ProjectIdentity projectIdentity) {
        // TODO: the build path should be available directly
        Path buildPath = Path.path(projectIdentity.getBuildIdentifier().getBuildPath());
        producerScope = new IsolatedModelScope(buildPath, projectIdentity.getProjectPath());
    }

    @Override
    public <T> void registerModel(String key, Class<T> type, Provider<T> provider) {
        IsolatedModelKey<T> modelKey = new IsolatedModelKey<>(key, type);

    }
}
