/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.internal.cache.StringInterner;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.internal.fingerprint.HistoricalFileCollectionFingerprint;
import org.gradle.internal.fingerprint.impl.DefaultHistoricalFileCollectionFingerprint;
import org.gradle.internal.fingerprint.impl.EmptyHistoricalFileCollectionFingerprint;
import org.gradle.internal.serialize.DefaultSerializerRegistry;
import org.gradle.internal.serialize.SerializerRegistry;
import org.gradle.internal.serialize.Serializers;

import javax.annotation.Nullable;

public class TaskHistoryCache {
    private final PersistentIndexedCache<String, HistoricalTaskExecution> cache;

    public TaskHistoryCache(TaskHistoryStore cacheAccess, StringInterner stringInterner) {
        SerializerRegistry serializerRegistry = new DefaultSerializerRegistry();
        serializerRegistry.register(DefaultHistoricalFileCollectionFingerprint.class, new DefaultHistoricalFileCollectionFingerprint.SerializerImpl(stringInterner));
        serializerRegistry.register(EmptyHistoricalFileCollectionFingerprint.class, Serializers.constant(EmptyHistoricalFileCollectionFingerprint.INSTANCE));
        TaskExecutionFingerprintSerializer serializer = new TaskExecutionFingerprintSerializer(serializerRegistry.build(HistoricalFileCollectionFingerprint.class));
        this.cache = cacheAccess.createCache(
            "taskHistory",
            String.class,
            serializer,
            10000,
            false
        );
    }

    @Nullable
    public HistoricalTaskExecution get(String key) {
        return cache.get(key);
    }

    public void put(String key, HistoricalTaskExecution value) {
        cache.put(key, value);
    }
}
