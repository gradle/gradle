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

import org.gradle.api.isolated.models.BuildIsolatedModelLookup;
import org.gradle.api.provider.Provider;

public class DefaultBuildIsolatedModelRegistry implements BuildIsolatedModelRegistryInternal, BuildIsolatedModelLookup {

    private final BuildIsolatedModelStore store;

    public DefaultBuildIsolatedModelRegistry(BuildIsolatedModelStore store) {
        this.store = store;
    }

    @Override
    public <T> void registerModel(String key, Class<T> type, Provider<T> provider) {
        IsolatedModelKey<T> modelKey = new IsolatedModelKey<>(key, type);
        store.registerModel(modelKey, provider);
    }

    @Override
    public <T> Provider<T> getModel(String key, Class<T> type) {
        IsolatedModelKey<T> modelKey = new IsolatedModelKey<>(key, type);
        BuildIsolatedModel<T> model = store.findModel(modelKey);
        if (model == null) {
            throw new IllegalArgumentException("No provider for " + modelKey);
        }
        return model.instantiate();
    }

    @Override
    public void isolateAllModelProviders() {

    }
}
