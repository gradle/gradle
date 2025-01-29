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

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.lambdas.SerializableLambdas;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ServiceScope(Scope.BuildTree.class)
public class ProjectModelController {

    // TODO: what states are there for the controller?

    private final Map<ProducerKey<?>, IsolatedModelProducer<?>> workByKey = new HashMap<>();

    private final ObjectFactory objectFactory;

    public ProjectModelController(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
        System.out.println("*** Instantiating ProjectModelController");
    }

    public <T> void register(ProjectModelScopeIdentifier producerScope, IsolatedModelKey<T> key, IsolatedModelProducer<T> work) {
        ProducerKey<T> workKey = new ProducerKey<>(producerScope, key);
        workByKey.put(workKey, work);
    }

    public <T> Provider<List<T>> request(ProjectScopeModelBatchRequest<T> request) {
        return new ConsumerProjectScopeModelBatchProvider<>(this, request);
    }

    public <T> ProviderInternal<? extends List<T>> calculateBatchValueProvider(ProjectScopeModelBatchRequest<T> request) {
        IsolatedModelKey<T> modelKey = request.getModelKey();
        ListProperty<T> batchValuesProperty = objectFactory.listProperty(modelKey.getType());

        Collection<ProducerKey<?>> producerKeys = request.isAll() ? workByKey.keySet() : getProducerKeys(modelKey, request.getProducers());

        for (ProducerKey producerKey : producerKeys) {
            IsolatedModelProducer<?> producer = workByKey.get(producerKey);

            Provider<T> producerSideProvider;
            if (producer == null) {
                // the producer has not been registered at all
                producerSideProvider = Providers.notDefined();
            } else {

                if (!producer.getModelType().equals(modelKey.getType())) {
                    throw new IllegalStateException("Producer " + producerKey + " with type " + producer.getModelType() + " (from " + producer.getModelType().getClassLoader()  + ") does not match model type " + modelKey.getType() + " (from " + modelKey.getType().getClassLoader()  + ")");
                }

                @SuppressWarnings("unchecked")
                IsolatedModelProducer<T> typedProducer = (IsolatedModelProducer<T>) producer;
                producerSideProvider = typedProducer.prepare();
            }

            // Simulating aggregation leniency via list providers
            Provider<List<T>> listWrappedValueProvider = producerSideProvider.map(SerializableLambdas.transformer(ImmutableList::of));
            if (request.isLenient()) {
                listWrappedValueProvider = listWrappedValueProvider.orElse(ImmutableList.of());
            }
            batchValuesProperty.addAll(listWrappedValueProvider);
        }

        return (ProviderInternal<? extends List<T>>) batchValuesProperty;
    }

    private <T> Collection<ProducerKey<?>> getProducerKeys(IsolatedModelKey<T> modelKey, List<ProjectModelScopeIdentifier> producers) {
        return producers.stream().map(it -> new ProducerKey<>(it, modelKey)).collect(Collectors.toList());
    }

    private static final class ProducerKey<T> {

        private final ProjectModelScopeIdentifier producerScope;
        private final IsolatedModelKey<T> key;

        private ProducerKey(ProjectModelScopeIdentifier producerScope, IsolatedModelKey<T> key) {
            this.producerScope = producerScope;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ProducerKey)) {
                return false;
            }

            ProducerKey<?> key1 = (ProducerKey<?>) o;
            return producerScope.equals(key1.producerScope) && key.equals(key1.key);
        }

        @Override
        public int hashCode() {
            int result = producerScope.hashCode();
            result = 31 * result + key.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "Key{" +
                "producerScope=" + producerScope +
                ", key=" + key +
                '}';
        }
    }
}
