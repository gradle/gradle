/*
 * Copyright 2011 the original author or authors.
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

import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import org.apache.commons.lang.SerializationUtils;
import org.gradle.api.internal.tasks.cache.DefaultTaskCacheKeyBuilder;
import org.gradle.api.internal.tasks.cache.TaskCacheKey;
import org.gradle.api.internal.tasks.cache.TaskCacheKeyBuilder;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The persistent state for a single task execution.
 */
public abstract class TaskExecution {
    private String taskClass;
    private HashCode taskClassLoaderHash;
    private HashCode taskActionsClassLoaderHash;
    private Map<String, Object> inputProperties;
    private Set<String> declaredOutputFilePaths;

    /**
     * Returns the absolute path of every declared output file and directory.
     * The returned set includes potentially missing files as well, and does
     * not include the resolved contents of directories.
     */
    public Set<String> getDeclaredOutputFilePaths() {
        return declaredOutputFilePaths;
    }

    public void setDeclaredOutputFilePaths(Set<String> declaredOutputFilePaths) {
        this.declaredOutputFilePaths = declaredOutputFilePaths;
    }

    public String getTaskClass() {
        return taskClass;
    }

    public void setTaskClass(String taskClass) {
        this.taskClass = taskClass;
    }

    public HashCode getTaskClassLoaderHash() {
        return taskClassLoaderHash;
    }

    public void setTaskClassLoaderHash(HashCode taskClassLoaderHash) {
        this.taskClassLoaderHash = taskClassLoaderHash;
    }

    public HashCode getTaskActionsClassLoaderHash() {
        return taskActionsClassLoaderHash;
    }

    public void setTaskActionsClassLoaderHash(HashCode taskActionsClassLoaderHash) {
        this.taskActionsClassLoaderHash = taskActionsClassLoaderHash;
    }

    public Map<String, Object> getInputProperties() {
        return inputProperties;
    }

    public void setInputProperties(Map<String, Object> inputProperties) {
        this.inputProperties = inputProperties;
    }

    /**
     * @return May return null.
     */
    public abstract Map<String, FileCollectionSnapshot> getOutputFilesSnapshot();

    public abstract void setOutputFilesSnapshot(Map<String, FileCollectionSnapshot> outputFilesSnapshot);

    public abstract Map<String, FileCollectionSnapshot> getInputFilesSnapshot();

    public abstract void setInputFilesSnapshot(Map<String, FileCollectionSnapshot> inputFilesSnapshot);

    public abstract FileCollectionSnapshot getDiscoveredInputFilesSnapshot();

    public abstract void setDiscoveredInputFilesSnapshot(FileCollectionSnapshot inputFilesSnapshot);

    public TaskCacheKey calculateCacheKey() {
        TaskCacheKeyBuilder builder = new DefaultTaskCacheKeyBuilder();
        builder.putString(taskClass);
        builder.putBytes(taskClassLoaderHash.asBytes());
        builder.putBytes(taskActionsClassLoaderHash.asBytes());

        // TODO:LPTR Use sorted maps instead of explicitly sorting entries here

        for (Map.Entry<String, Object> entry : sortEntries(inputProperties.entrySet())) {
            builder.putString(entry.getKey());
            Object value = entry.getValue();
            appendToCacheKey(builder, value);
        }

        for (Map.Entry<String, FileCollectionSnapshot> entry : sortEntries(getInputFilesSnapshot().entrySet())) {
            builder.putString(entry.getKey());
            FileCollectionSnapshot snapshot = entry.getValue();
            snapshot.appendToCacheKey(builder);
        }
        return builder.build();
    }

    private static <T> List<Map.Entry<String, T>> sortEntries(Set<Map.Entry<String, T>> entries) {
        ArrayList<Map.Entry<String, T>> sortedEntries = Lists.newArrayList(entries);
        Collections.sort(sortedEntries, new Comparator<Map.Entry<String, T>>() {
            @Override
            public int compare(Map.Entry<String, T> o1, Map.Entry<String, T> o2) {
                return o1.getKey().compareTo(o2.getKey());
            }
        });
        return sortedEntries;
    }

    private static void appendToCacheKey(TaskCacheKeyBuilder builder, Object value) {
        if (value == null) {
            builder.putString("$NULL");
            return;
        }

        if (value.getClass().isArray()) {
            builder.putString("Array");
            for (int idx = 0, len = Array.getLength(value); idx < len; idx++) {
                builder.putInt(idx);
                appendToCacheKey(builder, Array.get(value, idx));
            }
            return;
        }

        if (value instanceof Iterable) {
            builder.putString("Iterable");
            int idx = 0;
            for (Object elem : (Iterable<?>) value) {
                builder.putInt(idx);
                appendToCacheKey(builder, elem);
                idx++;
            }
            return;
        }

        if (value instanceof Map) {
            builder.putString("Map");
            int idx = 0;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                builder.putInt(idx);
                appendToCacheKey(builder, entry.getKey());
                appendToCacheKey(builder, entry.getValue());
                idx++;
            }
            return;
        }

        if (value instanceof Boolean) {
            builder.putBoolean((Boolean) value);
        } else if (value instanceof Integer) {
            builder.putInt((Integer) value);
        } else if (value instanceof Short) {
            builder.putInt((Short) value);
        } else if (value instanceof Byte) {
            builder.putInt((Byte) value);
        } else if (value instanceof Double) {
            builder.putDouble((Double) value);
        } else if (value instanceof Float) {
            builder.putDouble((Float) value);
        } else if (value instanceof BigInteger) {
            builder.putBytes(((BigInteger) value).toByteArray());
        } else if (value instanceof CharSequence) {
            builder.putString((CharSequence) value);
        } else if (value instanceof Enum) {
            builder.putString(value.getClass().getName());
            builder.putString(((Enum) value).name());
        } else {
            byte[] bytes = SerializationUtils.serialize((Serializable) value);
            builder.putBytes(bytes);
        }
    }
}
