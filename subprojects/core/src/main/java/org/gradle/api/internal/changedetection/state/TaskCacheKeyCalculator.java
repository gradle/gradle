/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.execution.TaskCachingHashesListener;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.BuildCacheKeyBuilder;
import org.gradle.caching.internal.DefaultBuildCacheKeyBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TaskCacheKeyCalculator {
    private final TaskCachingHashesListener hashesListener;

    public TaskCacheKeyCalculator() {
        this(null);
    }

    public TaskCacheKeyCalculator(TaskCachingHashesListener hashesListener) {
        this.hashesListener = hashesListener;
    }

    public BuildCacheKey calculate(TaskExecution execution, Task task) {
        if (execution == null || execution.getTaskActionsClassLoaderHash() == null || execution.getTaskActionsClassLoaderHash() == null) {
            hashesListener.inputsCollected(task, null, new TaskCachingHashesListener.TaskCachingInputs(
                null,
                null,
                ImmutableMap.<String, HashCode>of(),
                ImmutableSet.<String>of()));
            return null;
        }

        HashCode taskClassLoaderHash = execution.getTaskClassLoaderHash();
        HashCode taskActionsClassLoaderHash = execution.getTaskActionsClassLoaderHash();
        Map<String, HashCode> inputHashes = new HashMap<String, HashCode>();
        BuildCacheKeyBuilder builder = new DefaultBuildCacheKeyBuilder();

        builder.putString(execution.getTaskClass());
        builder.putBytes(taskClassLoaderHash.asBytes());
        builder.putBytes(taskActionsClassLoaderHash.asBytes());

        // TODO:LPTR Use sorted maps instead of explicitly sorting entries here

        for (Map.Entry<String, Object> entry : sortEntries(execution.getInputProperties().entrySet())) {
            Object value = entry.getValue();
            HashCode hash = hashForObject(value);
            addInputProperty(entry.getKey(), hash, builder, inputHashes);
        }

        for (Map.Entry<String, FileCollectionSnapshot> entry : sortEntries(execution.getInputFilesSnapshot().entrySet())) {
            FileCollectionSnapshot snapshot = entry.getValue();
            DefaultBuildCacheKeyBuilder newBuilder = new DefaultBuildCacheKeyBuilder();
            snapshot.appendToCacheKey(newBuilder);
            HashCode hash = newBuilder.buildHashCode();
            addInputProperty(entry.getKey(), hash, builder, inputHashes);
        }

        Set<String> outputPropertyNames = new HashSet<String>();
        for (String cacheableOutputPropertyName : sortStrings(execution.getOutputPropertyNamesForCacheKey())) {
            outputPropertyNames.add(cacheableOutputPropertyName);
            builder.putString(cacheableOutputPropertyName);
        }

        BuildCacheKey cacheKey = builder.build();
        if (hashesListener != null) {
            hashesListener.inputsCollected(task, cacheKey, new TaskCachingHashesListener.TaskCachingInputs(
                taskClassLoaderHash,
                taskActionsClassLoaderHash,
                inputHashes,
                outputPropertyNames
            ));
        }
        return cacheKey;
    }

    private static void addInputProperty(String name, HashCode hash, BuildCacheKeyBuilder builder, Map<String, HashCode> inputHashes) {
        builder.putString(name);
        builder.putBytes(hash.asBytes());
        inputHashes.put(name, hash);
    }

    private static <T> List<Map.Entry<String, T>> sortEntries(Set<Map.Entry<String, T>> entries) {
        List<Map.Entry<String, T>> sortedEntries = Lists.newArrayList(entries);
        Collections.sort(sortedEntries, new Comparator<Map.Entry<String, T>>() {
            @Override
            public int compare(Map.Entry<String, T> o1, Map.Entry<String, T> o2) {
                return o1.getKey().compareTo(o2.getKey());
            }
        });
        return sortedEntries;
    }

    private static List<String> sortStrings(Collection<String> entries) {
        List<String> sortedEntries = Lists.newArrayList(entries);
        Collections.sort(sortedEntries);
        return sortedEntries;
    }

    private static HashCode hashForObject(Object object) {
        return ((DefaultBuildCacheKeyBuilder) new DefaultBuildCacheKeyBuilder().appendToCacheKey(object)).buildHashCode();
    }
}
