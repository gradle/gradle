/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.shareddata;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.provider.AbstractMinimalProvider;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.api.shareddata.ProjectSharedDataRegistry;
import org.gradle.util.Path;

import javax.annotation.Nullable;

public class DefaultSharedDataRegistry implements SharedDataRegistry, HoldsProjectState {

    private SharedDataStorage storage = new SharedDataStorage();

    @Override
    public <T> void registerSharedDataProducer(ProjectInternal providerProject, Class<T> dataType, @Nullable String dataIdentifier, Provider<T> dataProvider) {
        storage.put(providerProject, dataType, dataIdentifier, dataProvider);
    }

    @Override
    public <T> Provider<T> obtainData(ProjectInternal consumerProject, Class<T> dataType, @Nullable String dataIdentifier, ProjectSharedDataRegistry.SingleSourceIdentifier dataSourceIdentifier) {
        Path sourceProjectIdentitiyPath = dataSourceIdentifier.getSourceProjectIdentitiyPath();
        return new ProjectSharedDataProvider<>(sourceProjectIdentitiyPath, dataType, dataIdentifier);
    }

    @Override
    public void discardAll() {
        storage = new SharedDataStorage();
    }

    @NonNullApi
    private class ProjectSharedDataProvider<T> extends AbstractMinimalProvider<T> {

        private final Path sourceProjectIdentityPath;
        private final Class<T> dataType;
        private final String dataIdentifier;

        ProjectSharedDataProvider(
            Path sourceProjectIdentityPath,
            Class<T> dataType,
            @Nullable String dataIdentifier
        ) {
            this.sourceProjectIdentityPath = sourceProjectIdentityPath;
            this.dataType = dataType;
            this.dataIdentifier = dataIdentifier;
        }

        @Override
        public ValueProducer getProducer() {
            @Nullable Provider<T> providerOrNull = findProviderInStorage();
            return providerOrNull != null
                ? Providers.internal(providerOrNull).getProducer()
                : super.getProducer();
        }

        @Override
        protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
            @Nullable Provider<T> providerOrNull = findProviderInStorage();
            return providerOrNull != null
                ? Providers.internal(providerOrNull).calculateValue(consumer)
                : Value.missing();
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return dataType;
        }

        // TODO: cache the result?
        @Nullable
        private Provider<T> findProviderInStorage() {
            return storage.get(sourceProjectIdentityPath, dataType, dataIdentifier);
        }
    }
}
