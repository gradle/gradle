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

package org.gradle.internal.isolated.models.legacy;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.provider.Provider;
import org.gradle.internal.isolated.models.DefaultIsolatedModelKey;
import org.gradle.internal.isolated.models.IsolatedModelScope;

public class BuildIsolatedModelProjectLookup implements BuildIsolatedModelLookup {

    private final IsolatedModelScope scope;
    private final IsolatedModelScope buildScope;

    private final BuildIsolatedModelStore store;

    public BuildIsolatedModelProjectLookup(ProjectInternal project, BuildIsolatedModelStore store) {
        this.scope = new IsolatedModelScope(project.getBuildPath(), project.getProjectPath());
        this.buildScope = new IsolatedModelScope(project.getBuildPath());
        this.store = store;
    }

    @Override
    public <T> Provider<T> getModel(String key, Class<T> type) {
        DefaultIsolatedModelKey<T> modelKey = new DefaultIsolatedModelKey<>(key, type);
        return store.getModel(scope, modelKey, buildScope);
    }
}
