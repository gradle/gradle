/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.cache.internal;

import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentStore;
import org.gradle.internal.Factory;
import org.gradle.internal.serialize.Serializer;

public class InMemoryNonExclusiveStore implements PersistentStore {

    @Override
    public <K, V> PersistentIndexedCache<K, V> createCache(String name, Class<K> keyType, Serializer<V> valueSerializer) {
        return new InMemoryNotSerializingCache<K, V>();
    }

    @Override
    public <T> T useCache(String operationDisplayName, Factory<? extends T> action) {
        return action.create();
    }

    @Override
    public void useCache(String operationDisplayName, Runnable action) {
        action.run();
    }

    @Override
    public <T> T longRunningOperation(String operationDisplayName, Factory<? extends T> action) {
        return action.create();
    }

    @Override
    public void longRunningOperation(String operationDisplayName, Runnable action) {
        action.run();
    }
}
