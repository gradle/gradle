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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.execution.TaskProperties;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.ValueSnapshot;

import java.util.Map;
import java.util.SortedMap;

public class TaskCacheKeyCalculator {

    private final boolean buildCacheDebugLogging;

    public TaskCacheKeyCalculator(boolean buildCacheDebugLogging) {
        this.buildCacheDebugLogging = buildCacheDebugLogging;
    }

    public TaskOutputCachingBuildCacheKey calculate(TaskInternal task, BeforeExecutionState execution, TaskProperties taskProperties) {
        TaskOutputCachingBuildCacheKeyBuilder builder = new DefaultTaskOutputCachingBuildCacheKeyBuilder(task.getIdentityPath());
        if (buildCacheDebugLogging) {
            builder = new DebuggingTaskOutputCachingBuildCacheKeyBuilder(builder);
        }
        builder.appendTaskImplementation(execution.getImplementation());
        builder.appendTaskActionImplementations(execution.getAdditionalImplementations());

        SortedMap<String, ValueSnapshot> inputProperties = execution.getInputProperties();
        for (Map.Entry<String, ValueSnapshot> entry : inputProperties.entrySet()) {
            Hasher newHasher = Hashing.newHasher();
            entry.getValue().appendToHasher(newHasher);
            if (newHasher.isValid()) {
                HashCode hash = newHasher.hash();
                builder.appendInputValuePropertyHash(entry.getKey(), hash);
            } else {
                builder.inputPropertyNotCacheable(entry.getKey(), newHasher.getInvalidReason());
            }
        }

        SortedMap<String, CurrentFileCollectionFingerprint> inputFingerprints = execution.getInputFileProperties();
        for (Map.Entry<String, CurrentFileCollectionFingerprint> entry : inputFingerprints.entrySet()) {
            builder.appendInputFilesProperty(entry.getKey(), entry.getValue());
        }

        for (TaskOutputFilePropertySpec propertySpec : taskProperties.getOutputFileProperties()) {
            if (!(propertySpec instanceof CacheableTaskOutputFilePropertySpec)) {
                continue;
            }
            if (((CacheableTaskOutputFilePropertySpec) propertySpec).getOutputFile() != null) {
                builder.appendOutputPropertyName(propertySpec.getPropertyName());
            }
        }

        return builder.build();
    }
}
