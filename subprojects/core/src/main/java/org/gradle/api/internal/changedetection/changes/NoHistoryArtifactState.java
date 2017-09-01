/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.changedetection.changes;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.caching.internal.tasks.BuildCacheKeyInputs;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.internal.id.UniqueId;
import org.gradle.util.Path;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

class NoHistoryArtifactState implements TaskArtifactState, TaskExecutionHistory {

    public static final TaskArtifactState INSTANCE = new NoHistoryArtifactState();

    private static final BuildCacheKeyInputs NO_CACHE_KEY_INPUTS = new BuildCacheKeyInputs(
        null,
        null,
        null,
        null,
        null,
        null
    );

    private static final TaskOutputCachingBuildCacheKey NO_CACHE_KEY = new TaskOutputCachingBuildCacheKey() {
        @Override
        public boolean isValid() {
            return false;
        }

        @Override
        public String toString() {
            return "INVALID";
        }

        @Override
        public Path getTaskPath() {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getHashCode() {
            return null;
        }

        @Override
        public BuildCacheKeyInputs getInputs() {
            return NO_CACHE_KEY_INPUTS;
        }

        @Override
        public String getDisplayName() {
            return toString();
        }
    };

    private NoHistoryArtifactState() {
    }

    @Override
    public boolean isUpToDate(Collection<String> messages) {
        messages.add("Task has not declared any outputs.");
        return false;
    }

    @Override
    public IncrementalTaskInputs getInputChanges() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAllowedToUseCachedResults() {
        return false;
    }

    @Override
    public TaskOutputCachingBuildCacheKey calculateCacheKey() {
        return NO_CACHE_KEY;
    }

    @Override
    public TaskExecutionHistory getExecutionHistory() {
        return this;
    }

    @Override
    public UniqueId getOriginBuildInvocationId() {
        return null;
    }

    @Override
    public void ensureSnapshotBeforeTask() {
    }

    @Override
    public void afterOutputsRemovedBeforeTask() {
    }

    @Override
    public void snapshotAfterTaskExecution(Throwable failure) {
    }

    @Override
    public void snapshotAfterLoadedFromCache(ImmutableSortedMap<String, FileCollectionSnapshot> newOutputSnapshot) {
    }

    @Override
    public Map<String, Map<String, FileContentSnapshot>> getOutputContentSnapshots() {
        return Collections.emptyMap();
    }

    @Override
    public Set<File> getOutputFiles() {
        return Collections.emptySet();
    }

    @Override
    public OverlappingOutputs getOverlappingOutputs() {
        return null;
    }

}
