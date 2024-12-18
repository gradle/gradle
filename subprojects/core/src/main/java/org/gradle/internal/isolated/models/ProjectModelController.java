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

import org.gradle.api.internal.provider.ValueSupplier;
import org.gradle.api.provider.Provider;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ServiceScope(Scope.Build.class)
public class ProjectModelController {

    // TODO: what states are there for the controller?

    private final Map<ProducerKey<?>, IsolatedModelProducer<?>> workByKey = new HashMap<>();

    public <T> void register(ProjectModelScopeIdentifier producerScope, IsolatedModelKey<T> key, IsolatedModelProducer<T> work) {
        ProducerKey<T> workKey = new ProducerKey<>(producerScope, key);
        workByKey.put(workKey, work);
    }

    public <T> Provider<List<T>> request(ProjectScopeModelBatchRequest<T> request) {
        return new ConsumerProjectScopeModelBatchProvider<>(this, request);
    }

    public <T> boolean calculateBatchPresence(ProjectScopeModelBatchRequest<T> request) {
        return !calculateBatchValue(request).isMissing();
    }

    public <T> ValueSupplier.Value<? extends List<T>> calculateBatchValue(ProjectScopeModelBatchRequest<T> request) {
        ArrayList<T> batchValues = new ArrayList<>();
        for (ProjectModelScopeIdentifier producerId : request.getProducers()) {
            ProducerKey<T> producerKey = new ProducerKey<>(producerId, request.getModelKey());
            IsolatedModelProducer<?> producer = workByKey.get(producerKey);
            if (producer == null && !request.isLenient()) {
                return ValueSupplier.Value.missing();
            }

            if (!producer.getModelType().equals(request.getModelKey().getType())) {
                throw new IllegalStateException("Producer " + producerKey + " does not match model type " + request.getModelKey().getType());
            }

            @SuppressWarnings("unchecked")
            IsolatedModelProducer<T> typedProducer = (IsolatedModelProducer<T>) producer;

            T value = typedProducer.prepare().get();
            batchValues.add(value);
        }

        return ValueSupplier.Value.of(batchValues);
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
