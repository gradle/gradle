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
import org.gradle.api.Describable;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.state.TaskHistoryRepository;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.execution.TaskProperties;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.caching.internal.tasks.TaskCacheKeyCalculator;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.internal.change.Change;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.execution.history.OutputFilesRepository;
import org.gradle.internal.execution.history.changes.DefaultExecutionStateChanges;
import org.gradle.internal.execution.history.changes.ExecutionStateChanges;
import org.gradle.internal.execution.history.changes.OutputFileChanges;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.reflect.Instantiator;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

@NonNullApi
public class DefaultTaskArtifactStateRepository implements TaskArtifactStateRepository {
    private final FileCollectionFingerprinterRegistry fingerprinterRegistry;
    private final TaskHistoryRepository taskHistoryRepository;
    private final Instantiator instantiator;
    private final OutputFilesRepository outputFilesRepository;
    private final TaskCacheKeyCalculator taskCacheKeyCalculator;

    public DefaultTaskArtifactStateRepository(FileCollectionFingerprinterRegistry fingerprinterRegistry, TaskHistoryRepository taskHistoryRepository, Instantiator instantiator,
                                              OutputFilesRepository outputFilesRepository, TaskCacheKeyCalculator taskCacheKeyCalculator) {
        this.fingerprinterRegistry = fingerprinterRegistry;
        this.taskHistoryRepository = taskHistoryRepository;
        this.instantiator = instantiator;
        this.outputFilesRepository = outputFilesRepository;
        this.taskCacheKeyCalculator = taskCacheKeyCalculator;
    }

    public TaskArtifactState getStateFor(final TaskInternal task, TaskProperties taskProperties) {
        return new TaskArtifactStateImpl(task, taskHistoryRepository.getHistory(task, taskProperties));
    }

    private class TaskArtifactStateImpl implements TaskArtifactState {
        private final TaskInternal task;
        private final TaskHistoryRepository.History history;
        private boolean outputsRemoved;
        private boolean statesCalculated;
        private ExecutionStateChanges states;

        public TaskArtifactStateImpl(TaskInternal task, TaskHistoryRepository.History history) {
            this.task = task;
            this.history = history;
        }

        @Override
        public IncrementalTaskInputs getInputChanges() {
            return getExecutionStateChanges()
                .map(new Function<ExecutionStateChanges, StatefulIncrementalTaskInputs>() {
                     @Override
                     public StatefulIncrementalTaskInputs apply(ExecutionStateChanges changes) {
                         return changes.isRebuildRequired()
                             ? createRebuildInputs()
                             : createIncrementalInputs(changes.getInputFilesChanges());
                     }
                }).orElseGet(new Supplier<StatefulIncrementalTaskInputs>() {
                    @Override
                    public StatefulIncrementalTaskInputs get() {
                        return createRebuildInputs();
                    }
                });
        }

        private ChangesOnlyIncrementalTaskInputs createIncrementalInputs(Iterable<Change> inputFilesChanges) {
            return instantiator.newInstance(ChangesOnlyIncrementalTaskInputs.class, inputFilesChanges);
        }

        private RebuildIncrementalTaskInputs createRebuildInputs() {
            return instantiator.newInstance(RebuildIncrementalTaskInputs.class, task, getCurrentInputFileFingerprints());
        }

        @Override
        public Iterable<? extends FileCollectionFingerprint> getCurrentInputFileFingerprints() {
            return history.getBeforeExecutionState().getInputFileProperties().values();
        }

        @Override
        public boolean isAllowedToUseCachedResults() {
            return true;
        }

        @Nullable
        @Override
        public OverlappingOutputs getOverlappingOutputs() {
            BeforeExecutionState beforeExecutionState = history.getBeforeExecutionState();
            AfterPreviousExecutionState afterPreviousExecutionState = history.getAfterPreviousExecutionState();
            return OverlappingOutputs.detect(
                afterPreviousExecutionState == null
                    ? null
                    : afterPreviousExecutionState.getOutputFileProperties(),
                beforeExecutionState.getOutputFileProperties()
            );
        }

        @Override
        public TaskOutputCachingBuildCacheKey calculateCacheKey(TaskProperties taskProperties) {
            return taskCacheKeyCalculator.calculate(task, history.getBeforeExecutionState(), taskProperties);
        }

        @Override
        public Map<String, CurrentFileCollectionFingerprint> getOutputFingerprints() {
            return history.getBeforeExecutionState().getOutputFileProperties();
        }

        @Override
        public void ensureSnapshotBeforeTask() {
            history.getBeforeExecutionState();
        }

        @Override
        public void afterOutputsRemovedBeforeTask() {
            outputsRemoved = true;
        }

        @Override
        public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> snapshotAfterTaskExecution(TaskExecutionContext taskExecutionContext) {
            AfterPreviousExecutionState afterPreviousExecutionState = history.getAfterPreviousExecutionState();
            BeforeExecutionState beforeExecutionState = history.getBeforeExecutionState();
            return TaskFingerprintUtil.fingerprintAfterOutputsGenerated(
                afterPreviousExecutionState == null ? null : afterPreviousExecutionState.getOutputFileProperties(),
                beforeExecutionState.getOutputFileProperties(),
                taskExecutionContext.getTaskProperties().getOutputFileProperties(),
                getOverlappingOutputs() != null,
                task,
                fingerprinterRegistry
            );
        }

        @Override
        public void persistNewOutputs(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> newOutputFingerprints, boolean successful, OriginMetadata originMetadata) {
            AfterPreviousExecutionState afterPreviousExecutionState = history.getAfterPreviousExecutionState();
            // Only persist history if there was no failure, or some output files have been changed
            if (successful || afterPreviousExecutionState == null || hasAnyOutputFileChanges(afterPreviousExecutionState.getOutputFileProperties(), newOutputFingerprints)) {
                history.persist(newOutputFingerprints, successful, originMetadata);
                outputFilesRepository.recordOutputs(newOutputFingerprints.values());
            }
        }

        private boolean hasAnyOutputFileChanges(ImmutableSortedMap<String, FileCollectionFingerprint> previous, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> current) {
            return !previous.keySet().equals(current.keySet())
                || new OutputFileChanges(previous, current).hasAnyChanges();
        }

        @Override
        public Optional<ExecutionStateChanges> getExecutionStateChanges() {
            if (!statesCalculated) {
                statesCalculated = true;
                AfterPreviousExecutionState afterPreviousExecutionState = history.getAfterPreviousExecutionState();
                // Calculate initial state - note this is potentially expensive
                // We need to evaluate this even if we have no history, since every input property should be evaluated before the task executes
                BeforeExecutionState beforeExecutionState = history.getBeforeExecutionState();
                if (afterPreviousExecutionState == null || outputsRemoved) {
                    states = null;
                } else {
                    // TODO We need a nicer describable wrapper around task here
                    states = new DefaultExecutionStateChanges(afterPreviousExecutionState, beforeExecutionState, new Describable() {
                        @Override
                        public String getDisplayName() {
                            // The value is cached, so we should be okay to call this many times
                            return task.toString();
                        }
                    });
                }
            }
            return Optional.ofNullable(states);
        }
    }
}
