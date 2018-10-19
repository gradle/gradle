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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.rules.DefaultTaskUpToDateState;
import org.gradle.api.internal.changedetection.rules.NoHistoryTaskUpToDateState;
import org.gradle.api.internal.changedetection.rules.TaskUpToDateState;
import org.gradle.api.internal.changedetection.state.CurrentTaskExecution;
import org.gradle.api.internal.changedetection.state.HistoricalTaskExecution;
import org.gradle.api.internal.changedetection.state.TaskHistoryRepository;
import org.gradle.api.internal.changedetection.state.TaskOutputFilesRepository;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.execution.TaskProperties;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.caching.internal.origin.OriginMetadata;
import org.gradle.caching.internal.tasks.TaskCacheKeyCalculator;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.change.Change;
import org.gradle.internal.change.ChangeVisitor;
import org.gradle.internal.change.LimitingChangeVisitor;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.reflect.Instantiator;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.gradle.api.internal.changedetection.rules.TaskUpToDateState.MAX_OUT_OF_DATE_MESSAGES;

@NonNullApi
public class DefaultTaskArtifactStateRepository implements TaskArtifactStateRepository {
    private final FileCollectionFingerprinterRegistry fingerprinterRegistry;
    private final TaskHistoryRepository taskHistoryRepository;
    private final Instantiator instantiator;
    private final TaskOutputFilesRepository taskOutputFilesRepository;
    private final TaskCacheKeyCalculator taskCacheKeyCalculator;

    public DefaultTaskArtifactStateRepository(FileCollectionFingerprinterRegistry fingerprinterRegistry, TaskHistoryRepository taskHistoryRepository, Instantiator instantiator,
                                              TaskOutputFilesRepository taskOutputFilesRepository, TaskCacheKeyCalculator taskCacheKeyCalculator) {
        this.fingerprinterRegistry = fingerprinterRegistry;
        this.taskHistoryRepository = taskHistoryRepository;
        this.instantiator = instantiator;
        this.taskOutputFilesRepository = taskOutputFilesRepository;
        this.taskCacheKeyCalculator = taskCacheKeyCalculator;
    }

    public TaskArtifactState getStateFor(final TaskInternal task, TaskProperties taskProperties) {
        return new TaskArtifactStateImpl(task, taskHistoryRepository.getHistory(task, taskProperties));
    }

    private class TaskArtifactStateImpl implements TaskArtifactState, TaskExecutionHistory {
        private final TaskInternal task;
        private final TaskHistoryRepository.History history;
        private boolean upToDate;
        private boolean outputsRemoved;
        private TaskUpToDateState states;

        public TaskArtifactStateImpl(TaskInternal task, TaskHistoryRepository.History history) {
            this.task = task;
            this.history = history;
        }

        @Override
        public boolean isUpToDate(final Collection<String> messages) {
            MessageCollectingChangeVisitor visitor = new MessageCollectingChangeVisitor(messages);
            getStates().visitAllChanges(new LimitingChangeVisitor(MAX_OUT_OF_DATE_MESSAGES, visitor));
            this.upToDate = !visitor.hasAnyChanges();
            return upToDate;
        }

        @Override
        public IncrementalTaskInputs getInputChanges() {
            assert !upToDate : "Should not be here if the task is up-to-date";

            IncrementalTaskInputs taskInputs;
            if (outputsRemoved || getStates().isRebuildRequired()) {
                taskInputs = instantiator.newInstance(RebuildIncrementalTaskInputs.class, task, getCurrentInputFileFingerprints());
            } else {
                taskInputs = instantiator.newInstance(ChangesOnlyIncrementalTaskInputs.class, getStates().getInputFilesChanges());
            }
            return taskInputs;
        }

        @Override
        public Iterable<? extends FileCollectionFingerprint> getCurrentInputFileFingerprints() {
            return history.getCurrentExecution().getInputFingerprints().values();
        }

        @Override
        public boolean isAllowedToUseCachedResults() {
            return true;
        }

        @Nullable
        @Override
        public OverlappingOutputs getOverlappingOutputs() {
            return history.getCurrentExecution().getDetectedOverlappingOutputs();
        }

        @Override
        public TaskOutputCachingBuildCacheKey calculateCacheKey() {
            return taskCacheKeyCalculator.calculate(task, history.getCurrentExecution());
        }

        @Override
        public Set<File> getOutputFiles() {
            HistoricalTaskExecution previousExecution = history.getPreviousExecution();
            if (previousExecution == null) {
                return Collections.emptySet();
            }
            ImmutableCollection<FileCollectionFingerprint> outputFingerprints = previousExecution.getOutputFingerprints().values();
            Set<File> outputs = new HashSet<File>();
            for (FileCollectionFingerprint fileCollectionFingerprint : outputFingerprints) {
                for (String absolutePath : fileCollectionFingerprint.getFingerprints().keySet()) {
                    outputs.add(new File(absolutePath));
                }
            }
            return outputs;
        }

        @Override
        public Map<String, CurrentFileCollectionFingerprint> getOutputFingerprints() {
            return history.getCurrentExecution().getOutputFingerprints();
        }

        @Override
        public TaskExecutionHistory getExecutionHistory() {
            return this;
        }

        @Override
        public OriginMetadata getOriginExecutionMetadata() {
            HistoricalTaskExecution previousExecution = history.getPreviousExecution();
            return previousExecution == null ? null : previousExecution.getOriginExecutionMetadata();
        }

        @Override
        public void ensureSnapshotBeforeTask() {
            history.getCurrentExecution();
        }

        @Override
        public void afterOutputsRemovedBeforeTask() {
            outputsRemoved = true;
        }

        @Override
        public void snapshotAfterTaskExecution(Throwable failure, UniqueId buildInvocationId, TaskExecutionContext taskExecutionContext) {
            final CurrentTaskExecution currentExecution = history.getCurrentExecution();
            final HistoricalTaskExecution previousExecution = history.getPreviousExecution();
            final ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFilesAfter = Util.fingerprintTaskFiles(task, taskExecutionContext.getTaskProperties().getOutputFileProperties(), fingerprinterRegistry);

            ImmutableSortedMap<String, CurrentFileCollectionFingerprint> newOutputFingerprints;
            if (currentExecution.getDetectedOverlappingOutputs() == null) {
                newOutputFingerprints = outputFilesAfter;
            } else {
                newOutputFingerprints = ImmutableSortedMap.copyOfSorted(Maps.transformEntries(currentExecution.getOutputFingerprints(), new Maps.EntryTransformer<String, CurrentFileCollectionFingerprint, CurrentFileCollectionFingerprint>() {
                    @Override
                    @SuppressWarnings("NullableProblems")
                    public CurrentFileCollectionFingerprint transformEntry(String propertyName, CurrentFileCollectionFingerprint beforeExecution) {
                        CurrentFileCollectionFingerprint afterExecution = outputFilesAfter.get(propertyName);
                        FileCollectionFingerprint afterPreviousExecution = Util.getFingerprintAfterPreviousExecution(previousExecution, propertyName);
                        return Util.filterOutputFingerprint(afterPreviousExecution, beforeExecution, afterExecution);
                    }
                }));
            }

            snapshotAfterOutputsWereGenerated(newOutputFingerprints, failure, new OriginMetadata(
                    buildInvocationId,
                    taskExecutionContext.markExecutionTime()
            ));
        }

        @Override
        public void snapshotAfterLoadedFromCache(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> newOutputFingerprints, OriginMetadata originMetadata) {
            snapshotAfterOutputsWereGenerated(newOutputFingerprints, null, originMetadata);
        }

        private void snapshotAfterOutputsWereGenerated(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> newOutputFingerprints, @Nullable Throwable failure, OriginMetadata originMetadata) {
            // Only persist history if there was no failure, or some output files have been changed
            if (failure == null || hasOutputChanges(history.getCurrentExecution().getOutputFingerprints(), newOutputFingerprints)) {
                history.updateCurrentExecutionWithOutputs(newOutputFingerprints, failure == null, originMetadata);
                history.persist();
                taskOutputFilesRepository.recordOutputs(newOutputFingerprints.values());
            }
        }

        // TODO This can probably be made a lot better/faster
        private boolean hasOutputChanges(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> before, ImmutableSortedMap<String, CurrentFileCollectionFingerprint> after) {
            if (!before.keySet().equals(after.keySet())) {
                return true;
            }
            for (Map.Entry<String, CurrentFileCollectionFingerprint> entry : before.entrySet()) {
                String property = entry.getKey();
                CurrentFileCollectionFingerprint beforeCollection = entry.getValue();
                CurrentFileCollectionFingerprint afterCollection = after.get(property);
                final MutableBoolean changed = new MutableBoolean();
                afterCollection.visitChangesSince(beforeCollection, "", true, new ChangeVisitor() {
                    @Override
                    public boolean visitChange(Change change) {
                        changed.set(true);
                        return false;
                    }
                });
                if (changed.get()) {
                    return true;
                }
            }
            return false;
        }

        private TaskUpToDateState getStates() {
            if (states == null) {
                HistoricalTaskExecution previousExecution = history.getPreviousExecution();
                // Calculate initial state - note this is potentially expensive
                // We need to evaluate this even if we have no history, since every input property should be evaluated before the task executes
                CurrentTaskExecution currentExecution = history.getCurrentExecution();
                if (previousExecution == null) {
                    states = NoHistoryTaskUpToDateState.INSTANCE;
                } else {
                    states = new DefaultTaskUpToDateState(previousExecution, currentExecution, task);
                }
            }
            return states;
        }
    }

    private static class MessageCollectingChangeVisitor implements ChangeVisitor {
        private final Collection<String> messages;
        private boolean anyChanges;

        public MessageCollectingChangeVisitor(Collection<String> messages) {
            this.messages = messages;
        }

        @Override
        public boolean visitChange(Change change) {
            messages.add(change.getMessage());
            anyChanges = true;
            return true;
        }

        public boolean hasAnyChanges() {
            return anyChanges;
        }
    }
}
