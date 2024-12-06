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

import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.HashMap;
import java.util.Map;

@ServiceScope(Scope.BuildTree.class)
public class IsolatedModelController {

    private final Map<Key<?>, IsolatedModelWork<?>> workByKey = new HashMap<>();

    @SuppressWarnings({"unused"})
    public <T> void register(IsolatedModelScope producerScope, IsolatedModelKey<T> key, IsolatedModelWork<T> work) {
        Key<T> workKey = new Key<>(producerScope, key);
        workByKey.put(workKey, work);
    }

    @SuppressWarnings("unused")
    public <T> Provider<T> obtain(IsolatedModelScope consumerScope, IsolatedModelKey<T> key, IsolatedModelScope producerScope) {
        Key<T> workKey = new Key<>(producerScope, key);
        return Providers.of(workKey).flatMap(this::doObtain);
    }

    @SuppressWarnings("unchecked")
    private <T> Provider<T> doObtain(Key<T> workKey) {
        IsolatedModelWork<?> work = workByKey.get(workKey);
        Provider<?> provider = work == null ? Providers.notDefined() : work.prepare();
        return (Provider<T>) provider;
    }

    private static final class Key<T> {

        private final IsolatedModelScope producerScope;
        private final IsolatedModelKey<T> key;

        private Key(IsolatedModelScope producerScope, IsolatedModelKey<T> key) {
            this.producerScope = producerScope;
            this.key = key;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Key)) {
                return false;
            }

            Key<?> key1 = (Key<?>) o;
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
