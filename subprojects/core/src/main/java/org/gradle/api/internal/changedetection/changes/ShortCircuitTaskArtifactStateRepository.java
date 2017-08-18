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
package org.gradle.api.internal.changedetection.changes;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.StartParameter;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.reflect.Instantiator;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;

public class ShortCircuitTaskArtifactStateRepository implements TaskArtifactStateRepository {

    private final StartParameter startParameter;
    private final TaskArtifactStateRepository repository;
    private final Instantiator instantiator;

    public ShortCircuitTaskArtifactStateRepository(StartParameter startParameter, Instantiator instantiator, TaskArtifactStateRepository repository) {
        this.startParameter = startParameter;
        this.instantiator = instantiator;
        this.repository = repository;
    }

    public TaskArtifactState getStateFor(final TaskInternal task) {

        // Only false if no declared outputs AND no Task.upToDateWhen spec. We force to true for incremental tasks.
        if (!task.getOutputs().getHasOutput()) {
            return NoHistoryArtifactState.INSTANCE;
        }

        TaskArtifactState state = repository.getStateFor(task);

        if (startParameter.isRerunTasks()) {
            return new RerunTaskArtifactState(state, task, "Executed with '--rerun-tasks'.");
        }

        if (!task.getOutputs().getUpToDateSpec().isSatisfiedBy(task)) {
            return new RerunTaskArtifactState(state, task, "Task.upToDateWhen is false.");
        }

        return state;
    }

    private class RerunTaskArtifactState implements TaskArtifactState {
        private final TaskArtifactState delegate;
        private final TaskInternal task;
        private final String reason;

        private RerunTaskArtifactState(TaskArtifactState delegate, TaskInternal task, String reason) {
            this.delegate = delegate;
            this.task = task;
            this.reason = reason;
        }

        @Override
        public boolean isUpToDate(Collection<String> messages) {
            // Ensure that we snapshot the task's inputs
            delegate.ensureSnapshotBeforeTask();
            messages.add(reason);
            return false;
        }

        @Override
        public IncrementalTaskInputs getInputChanges() {
            return instantiator.newInstance(RebuildIncrementalTaskInputs.class, task);
        }

        @Override
        public boolean isAllowedToUseCachedResults() {
            return false;
        }

        @Override
        public TaskOutputCachingBuildCacheKey calculateCacheKey() {
            return delegate.calculateCacheKey();
        }

        @Override
        public TaskExecutionHistory getExecutionHistory() {
            return delegate.getExecutionHistory();
        }

        @Override
        public Map<String, Map<String, FileContentSnapshot>> getOutputContentSnapshots() {
            return delegate.getOutputContentSnapshots();
        }

        @Nullable
        @Override
        public UniqueId getOriginBuildInvocationId() {
            return null;
        }

        @Override
        public void ensureSnapshotBeforeTask() {
            delegate.ensureSnapshotBeforeTask();
        }

        @Override
        public void afterOutputsRemovedBeforeTask() {
            delegate.afterOutputsRemovedBeforeTask();
        }

        @Override
        public void snapshotAfterTaskExecution(Throwable failure) {
            delegate.snapshotAfterTaskExecution(failure);
        }

        @Override
        public void snapshotAfterLoadedFromCache(ImmutableSortedMap<String, FileCollectionSnapshot> newOutputSnapshot) {
            delegate.snapshotAfterLoadedFromCache(newOutputSnapshot);
        }
    }
}
