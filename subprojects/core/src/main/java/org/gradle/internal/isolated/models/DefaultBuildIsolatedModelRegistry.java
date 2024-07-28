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

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.isolated.models.BuildIsolatedModelLookup;
import org.gradle.api.provider.Provider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.gradle.internal.Cast.uncheckedCast;

public class DefaultBuildIsolatedModelRegistry implements BuildIsolatedModelRegistryInternal, BuildIsolatedModelLookup {

    private final GradleInternal gradle;
    private final IsolatedProviderFactory isolatedProviderFactory;

    private final Map<IsolatedModelKey<?>, BuildIsolatedModel<?>> providersByKey = new ConcurrentHashMap<>();

    public DefaultBuildIsolatedModelRegistry(GradleInternal gradle, IsolatedProviderFactory isolatedProviderFactory) {
        this.gradle = gradle;
        this.isolatedProviderFactory = isolatedProviderFactory;
    }

    @Override
    public <T> void registerModel(String key, Class<T> type, Provider<T> provider) {
        IsolatedModelKey<T> modelKey = new IsolatedModelKey<>(key, type);
        BuildIsolatedModel<T> modelProvider = new BuildIsolatedModel<>(isolatedProviderFactory, provider, gradle);
        providersByKey.put(modelKey, modelProvider);
    }

    @Override
    public <T> Provider<T> getModel(String key, Class<T> type) {
        IsolatedModelKey<T> modelKey = new IsolatedModelKey<>(key, type);
        BuildIsolatedModel<T> model = uncheckedCast(providersByKey.get(modelKey));
        if (model == null) {
            throw new IllegalArgumentException("No provider for " + modelKey);
        }
        return model.instantiate();
    }

    @Override
    public void isolateAllModelProviders() {

    }


}
