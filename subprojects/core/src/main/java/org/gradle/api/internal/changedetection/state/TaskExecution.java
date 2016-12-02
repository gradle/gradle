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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.internal.BuildCacheKeyBuilder;
import org.gradle.caching.internal.DefaultBuildCacheKeyBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The state for a single task execution.
 */
public abstract class TaskExecution {
    private String taskClass;
    private HashCode taskClassLoaderHash;
    private HashCode taskActionsClassLoaderHash;
    private Map<String, Object> inputProperties;
    private Iterable<String> outputPropertyNamesForCacheKey;
    private ImmutableSet<String> declaredOutputFilePaths;

    /**
     * Returns the names of all cacheable output property names that have a value set.
     * The collection includes names of properties declared via mapped plural outputs,
     * and excludes optional properties that don't have a value set. If the task is not
     * cacheable, it returns an empty collection.
     */
    public ImmutableSet<String> getOutputPropertyNamesForCacheKey() {
        return ImmutableSet.copyOf(outputPropertyNamesForCacheKey);
    }

    public void setOutputPropertyNamesForCacheKey(Iterable<String> outputPropertyNames) {
        this.outputPropertyNamesForCacheKey = outputPropertyNames;
    }

    /**
     * Returns the absolute path of every declared output file and directory.
     * The returned set includes potentially missing files as well, and does
     * not include the resolved contents of directories.
     */
    public ImmutableSet<String> getDeclaredOutputFilePaths() {
        return declaredOutputFilePaths;
    }

    public void setDeclaredOutputFilePaths(ImmutableSet<String> declaredOutputFilePaths) {
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

    public BuildCacheKey calculateCacheKey() {
        if (taskClassLoaderHash == null || taskActionsClassLoaderHash == null) {
            return null;
        }

        BuildCacheKeyBuilder builder = new DefaultBuildCacheKeyBuilder();
        builder.putString(taskClass);
        builder.putBytes(taskClassLoaderHash.asBytes());
        builder.putBytes(taskActionsClassLoaderHash.asBytes());

        // TODO:LPTR Use sorted maps instead of explicitly sorting entries here

        for (Map.Entry<String, Object> entry : sortEntries(inputProperties.entrySet())) {
            builder.putString(entry.getKey());
            Object value = entry.getValue();
            builder.appendToCacheKey(value);
        }

        for (Map.Entry<String, FileCollectionSnapshot> entry : sortEntries(getInputFilesSnapshot().entrySet())) {
            builder.putString(entry.getKey());
            FileCollectionSnapshot snapshot = entry.getValue();
            snapshot.appendToCacheKey(builder);
        }

        for (String cacheableOutputPropertyName : sortStrings(getOutputPropertyNamesForCacheKey())) {
            builder.putString(cacheableOutputPropertyName);
        }

        return builder.build();
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
}
