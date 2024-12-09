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

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.internal.isolated.models.IsolatedModelKey;
import org.gradle.api.provider.Provider;
import org.gradle.internal.isolated.models.IsolatedModelAccessKey;
import org.gradle.internal.isolated.models.IsolatedModelScope;
import org.gradle.internal.model.CalculatedValueContainerFactory;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.gradle.internal.Cast.uncheckedCast;

public class BuildIsolatedModelStore implements IsolatedModelStore {

    private final GradleInternal gradle;
    private final IsolatedProviderFactory isolatedProviderFactory;

    private final Map<IsolatedModelKey<?>, BuildIsolatedModel<?>> store = new ConcurrentHashMap<>();

    private final Map<IsolatedModelAccessKey<?>, IsolatedModelProducingProvider<?>> currentValues = new ConcurrentHashMap<>();
    private final CalculatedValueContainerFactory calculatedValueContainerFactory;

    public BuildIsolatedModelStore(
        GradleInternal gradle,
        IsolatedProviderFactory isolatedProviderFactory,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        this.gradle = gradle;
        this.isolatedProviderFactory = isolatedProviderFactory;
        this.calculatedValueContainerFactory = calculatedValueContainerFactory;
    }

    public <T> void registerModel(IsolatedModelKey<T> modelKey, Provider<T> provider) {
        BuildIsolatedModel<T> modelProvider = new BuildIsolatedModel<>(isolatedProviderFactory, provider, gradle);
        store.put(modelKey, modelProvider);
    }

    @Nullable
    public <T> BuildIsolatedModel<T> findModel(IsolatedModelKey<T> modelKey) {
        return uncheckedCast(store.get(modelKey));
    }

    @Override
    public <T> ProviderInternal<T> getModel(IsolatedModelScope consumer, IsolatedModelKey<T> key, IsolatedModelScope producer) {
        checkSameBuild(consumer, producer);
        if (producer.getProjectPath() != null) {
            throw new UnsupportedOperationException("Only build-scope isolated models can be requested");
        }

        IsolatedModelAccessKey<T> accessKey = new IsolatedModelAccessKey<>(producer, key, consumer);
        IsolatedModelProducingProvider<?> producingProvider = currentValues.computeIfAbsent(accessKey, this::instantiateProducer);

        return uncheckedCast(producingProvider);
    }

    private <T> IsolatedModelProducingProvider<T> instantiateProducer(IsolatedModelAccessKey<T> isolatedModelAccessKey) {
        // TODO: force the evaluation of the producer to ensure it registers all its models
        BuildIsolatedModel<T> buildIsolatedModel = findModel(isolatedModelAccessKey.getModelKey());
        if (buildIsolatedModel == null) {
            throw new IllegalStateException("Model not found for key: " + isolatedModelAccessKey);
        }
        return new IsolatedModelProducingProvider<>(isolatedModelAccessKey, calculatedValueContainerFactory, buildIsolatedModel);
    }

    private static void checkSameBuild(IsolatedModelScope consumer, IsolatedModelScope producer) {
        if (!consumer.getBuildPath().equals(producer.getBuildPath())) {
            // TODO: include participants into the message
            throw new IllegalArgumentException("Requesting isolated models from other builds is not allowed.");
        }
    }
}
