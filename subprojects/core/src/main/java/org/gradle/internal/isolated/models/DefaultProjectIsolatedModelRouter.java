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

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.isolated.models.IsolatedModelKey;
import org.gradle.api.isolated.models.IsolatedModelRouter;
import org.gradle.api.isolated.models.IsolatedModelWork;
import org.gradle.api.provider.Provider;

import java.util.Collection;
import java.util.Map;

public class DefaultProjectIsolatedModelRouter implements IsolatedModelRouter {

    private final IsolatedModelScope projectScope;
    private final IsolatedModelController modelController;

    public DefaultProjectIsolatedModelRouter(
        ProjectInternal project,
        IsolatedModelController modelController
    ) {
        this.projectScope = new IsolatedModelScope(project.getBuildPath(), project.getProjectPath());
        this.modelController = modelController;
    }

    @Override
    public <T> IsolatedModelKey<T> key(String name, Class<T> type) {
        return new DefaultIsolatedModelKey<>(name, type);
    }

    @Override
    public <T> IsolatedModelWork<T> work(Provider<T> provider) {
        return new DefaultIsolatedModelWork<>(provider);
    }

    @Override
    public <T> Map<String, Provider<T>> getProjectModels(IsolatedModelKey<T> key, Collection<String> projectPaths) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> Provider<T> getBuildModel(IsolatedModelKey<T> key) {
        return modelController.obtain(projectScope.getBuildScope(), key);
    }

    @Override
    public <T> void postModel(IsolatedModelKey<T> key, IsolatedModelWork<T> work) {
        throw new UnsupportedOperationException();
    }
}
