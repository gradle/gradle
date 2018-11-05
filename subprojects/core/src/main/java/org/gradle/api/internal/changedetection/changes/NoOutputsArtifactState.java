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
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.execution.TaskProperties;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.caching.internal.tasks.BuildCacheKeyInputs;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.internal.change.Change;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.util.Path;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

class NoOutputsArtifactState implements TaskArtifactState {

    public static final TaskArtifactState WITHOUT_ACTIONS = new NoOutputsArtifactState("Task has not declared any outputs nor actions.");
    public static final TaskArtifactState WITH_ACTIONS = new NoOutputsArtifactState("Task has not declared any outputs despite executing actions.");

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
        public byte[] getHashCodeBytes() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return toString();
        }
    };

    private final Change change;

    private NoOutputsArtifactState(final String message) {
        this.change = new Change() {
            @Override
            public String getMessage() {
                return message;
            }
        };
    }

    @Override
    public Optional<ExecutionStateChanges> getExecutionStateChanges() {
        return Optional.<ExecutionStateChanges>of(new ExecutionStateChanges() {
            @Override
            public void visitAllChanges(ChangeVisitor visitor) {
                visitor.visitChange(change);
            }

            @Override
            public boolean isRebuildRequired() {
                return true;
            }

            @Override
            public Iterable<Change> getInputFilesChanges() {
                throw new UnsupportedOperationException();
            }

            @Override
            public AfterPreviousExecutionState getPreviousExecution() {
                throw new UnsupportedOperationException();
            }
        });
    }

    @Override
    public IncrementalTaskInputs getInputChanges() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Iterable<? extends FileCollectionFingerprint> getCurrentInputFileFingerprints() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAllowedToUseCachedResults() {
        return false;
    }

    @Override
    public TaskOutputCachingBuildCacheKey calculateCacheKey(TaskProperties taskProperties) {
        return NO_CACHE_KEY;
    }

    @Override
    public void ensureSnapshotBeforeTask() {
    }

    @Override
    public void afterOutputsRemovedBeforeTask() {
    }

    @Override
    public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotAfterTaskExecution(TaskExecutionContext taskExecutionContext) {
        return ImmutableSortedMap.of();
    }

    @Override
    public void persistNewOutputs(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> newOutputFingerprints, boolean successful, OriginMetadata originMetadata) {
    }

    @Override
    public Map<String, CurrentFileCollectionFingerprint> getOutputFingerprints() {
        return Collections.emptyMap();
    }

    @Override
    public OverlappingOutputs getOverlappingOutputs() {
        return null;
    }

}
