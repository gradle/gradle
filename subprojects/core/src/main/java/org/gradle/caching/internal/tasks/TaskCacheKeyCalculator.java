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

package org.gradle.caching.internal.tasks;

import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import org.gradle.api.GradleException;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.caching.internal.DefaultBuildCacheHasher;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TaskCacheKeyCalculator {

    public TaskOutputCachingBuildCacheKey calculate(TaskExecution execution) {
        DefaultTaskOutputCachingBuildCacheKeyBuilder builder = new DefaultTaskOutputCachingBuildCacheKeyBuilder();
        HashCode taskClassLoaderHash = execution.getTaskClassLoaderHash();
        HashCode taskActionsClassLoaderHash = execution.getTaskActionsClassLoaderHash();

        builder.appendTaskClass(execution.getTaskClass());
        builder.appendClassloaderHash(taskClassLoaderHash);
        builder.appendActionsClassloaderHash(taskActionsClassLoaderHash);

        // TODO:LPTR Use sorted maps instead of explicitly sorting entries here

        for (Map.Entry<String, Object> entry : sortEntries(execution.getInputProperties().entrySet())) {
            Object value = entry.getValue();
            try {
                DefaultBuildCacheHasher newHasher = new DefaultBuildCacheHasher();
                newHasher.putObject(value);
                HashCode hash = newHasher.hash();
                builder.appendInputPropertyHash(entry.getKey(), hash);
            } catch (RuntimeException e) {
                throw new GradleException(
                    String.format("Unable to hash task input properties. Property '%s' with value '%s' cannot be serialized.",
                        entry.getKey(), value), e);

            }
        }

        // InputFilesSnapshot is already sorted
        for (Map.Entry<String, FileCollectionSnapshot> entry : execution.getInputFilesSnapshot().entrySet()) {
            FileCollectionSnapshot snapshot = entry.getValue();
            DefaultBuildCacheHasher newHasher = new DefaultBuildCacheHasher();
            snapshot.appendToHasher(newHasher);
            HashCode hash = newHasher.hash();
            builder.appendInputPropertyHash(entry.getKey(), hash);
        }

        for (String cacheableOutputPropertyName : sortStrings(execution.getOutputPropertyNamesForCacheKey())) {
            builder.appendOutputPropertyName(cacheableOutputPropertyName);
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
