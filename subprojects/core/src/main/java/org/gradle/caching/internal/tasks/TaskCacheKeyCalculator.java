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

import com.google.common.hash.HashCode;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.api.internal.changedetection.state.ValueSnapshot;
import org.gradle.caching.internal.DefaultBuildCacheHasher;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;

public class TaskCacheKeyCalculator {

    public TaskOutputCachingBuildCacheKey calculate(TaskExecution execution) {
        DefaultTaskOutputCachingBuildCacheKeyBuilder builder = new DefaultTaskOutputCachingBuildCacheKeyBuilder();
        HashCode taskClassLoaderHash = execution.getTaskClassLoaderHash();
        List<HashCode> taskActionsClassLoaderHashes = execution.getTaskActionsClassLoaderHashes();

        builder.appendTaskClass(execution.getTaskClass());
        builder.appendClassloaderHash(taskClassLoaderHash);
        builder.appendActionsClassloaderHashes(taskActionsClassLoaderHashes);

        SortedMap<String, ValueSnapshot> inputProperties = execution.getInputProperties();
        for (Map.Entry<String, ValueSnapshot> entry : inputProperties.entrySet()) {
            DefaultBuildCacheHasher newHasher = new DefaultBuildCacheHasher();
            entry.getValue().appendToHasher(newHasher);
            HashCode hash = newHasher.hash();
            builder.appendInputPropertyHash(entry.getKey(), hash);
        }

        SortedMap<String, FileCollectionSnapshot> inputFilesSnapshots = execution.getInputFilesSnapshot();
        for (Map.Entry<String, FileCollectionSnapshot> entry : inputFilesSnapshots.entrySet()) {
            FileCollectionSnapshot snapshot = entry.getValue();
            builder.appendInputPropertyHash(entry.getKey(), snapshot.getHash());
        }

        SortedSet<String> outputPropertyNamesForCacheKey = execution.getOutputPropertyNamesForCacheKey();
        for (String cacheableOutputPropertyName : outputPropertyNamesForCacheKey) {
            builder.appendOutputPropertyName(cacheableOutputPropertyName);
        }

        return builder.build();
    }
}
