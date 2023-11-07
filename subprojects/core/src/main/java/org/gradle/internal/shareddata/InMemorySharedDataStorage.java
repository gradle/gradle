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
import org.gradle.api.provider.Provider;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@NonNullApi
public class InMemorySharedDataStorage implements SharedDataStorage, HoldsProjectState {

    private final ReadWriteLock modificationLock = new ReentrantReadWriteLock();

    private final Map<Path, Map<DataKey, Provider<?>>> dataByProjectPathAndKey = new LinkedHashMap<>();

    @Override
    public ProjectProducedSharedData getProjectDataResolver(Path consumerProjectIdentityPath, Path sourceProjectIdentitityPath) {
        Lock readLock = modificationLock.readLock();
        return new ProjectProducedSharedData() {
            @Nullable
            @Override
            public Provider<?> get(DataKey dataKey) {
                readLock.lock();
                try {
                    @Nullable Map<DataKey, Provider<?>> mapFromStorage = dataByProjectPathAndKey.get(sourceProjectIdentitityPath);
                    return mapFromStorage != null ? mapFromStorage.get(dataKey) : null;
                } finally {
                    readLock.unlock();
                }
            }

            @Override
            public Map<DataKey, Provider<?>> getAllData() {
                readLock.lock();
                try {
                    @Nullable Map<DataKey, Provider<?>> mapFromStorage = dataByProjectPathAndKey.get(sourceProjectIdentitityPath);
                    return mapFromStorage != null ? new LinkedHashMap<>(mapFromStorage) : Collections.emptyMap();
                } finally {
                    readLock.unlock();
                }
            }
        };
    }

    public <T> void put(Path sourceProjectIdentityPath, Class<T> type, @Nullable String identifier, Provider<T> dataProvider) {
        Lock writeLock = modificationLock.writeLock();
        writeLock.lock();
        try {
            dataByProjectPathAndKey.computeIfAbsent(sourceProjectIdentityPath, key -> new ConcurrentHashMap<>()).compute(
                new DataKey(type, identifier),
                (key, oldValue) -> {
                    if (oldValue != null) {
                        throw new IllegalStateException(
                            "Shared data for type " + type + (identifier != null ? " (identifier '" + identifier + "')" : "") + " has already been registered in " + key
                        );
                    }
                    return dataProvider;
                }
            );
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void discardAll() {
        Lock writeLock = modificationLock.writeLock();
        writeLock.lock();
        try {
            dataByProjectPathAndKey.clear();
        } finally {
            writeLock.unlock();
        }
    }
}
