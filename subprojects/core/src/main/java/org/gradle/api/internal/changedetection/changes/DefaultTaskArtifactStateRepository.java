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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.api.internal.changedetection.rules.TaskStateChanges;
import org.gradle.api.internal.changedetection.rules.TaskUpToDateState;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotterRegistry;
import org.gradle.api.internal.changedetection.state.OutputFilesSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.api.internal.changedetection.state.TaskHistoryRepository;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.caching.BuildCacheKey;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.Collection;

public class DefaultTaskArtifactStateRepository implements TaskArtifactStateRepository {

    private final TaskHistoryRepository taskHistoryRepository;
    private final OutputFilesSnapshotter outputFilesSnapshotter;
    private final FileCollectionSnapshotterRegistry fileCollectionSnapshotterRegistry;
    private final Instantiator instantiator;
    private final FileCollectionFactory fileCollectionFactory;
    private final ClassLoaderHierarchyHasher classLoaderHierarchyHasher;

    public DefaultTaskArtifactStateRepository(TaskHistoryRepository taskHistoryRepository, Instantiator instantiator,
                                              OutputFilesSnapshotter outputFilesSnapshotter, FileCollectionSnapshotterRegistry fileCollectionSnapshotterRegistry,
                                              FileCollectionFactory fileCollectionFactory, ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
        this.taskHistoryRepository = taskHistoryRepository;
        this.instantiator = instantiator;
        this.outputFilesSnapshotter = outputFilesSnapshotter;
        this.fileCollectionSnapshotterRegistry = fileCollectionSnapshotterRegistry;
        this.fileCollectionFactory = fileCollectionFactory;
        this.classLoaderHierarchyHasher = classLoaderHierarchyHasher;
    }

    public TaskArtifactState getStateFor(final TaskInternal task) {
        return new TaskArtifactStateImpl(task, taskHistoryRepository.getHistory(task));
    }

    private class TaskArtifactStateImpl implements TaskArtifactState, TaskExecutionHistory {
        private final TaskInternal task;
        private final TaskHistoryRepository.History history;
        private boolean upToDate;
        private TaskUpToDateState states;
        private IncrementalTaskInputsInternal taskInputs;

        public TaskArtifactStateImpl(TaskInternal task, TaskHistoryRepository.History history) {
            this.task = task;
            this.history = history;
        }

        public boolean isUpToDate(Collection<String> messages) {
            if (collectChangedMessages(messages, getStates().getAllTaskChanges())) {
                upToDate = true;
                return true;
            }
            return false;
        }

        private boolean collectChangedMessages(Collection<String> messages, TaskStateChanges stateChanges) {
            boolean up2date = true;
            for (TaskStateChange stateChange : stateChanges) {
                if (messages != null) {
                    messages.add(stateChange.getMessage());
                    up2date = false;
                } else {
                    return false;
                }
            }
            return up2date;
        }

        public IncrementalTaskInputs getInputChanges() {
            assert !upToDate : "Should not be here if the task is up-to-date";

            if (canPerformIncrementalBuild()) {
                taskInputs = instantiator.newInstance(ChangesOnlyIncrementalTaskInputs.class, getStates().getInputFilesChanges());
            } else {
                taskInputs = instantiator.newInstance(RebuildIncrementalTaskInputs.class, task);
            }
            return taskInputs;
        }

        private boolean canPerformIncrementalBuild() {
            return collectChangedMessages(null, getStates().getRebuildChanges());
        }

        @Override
        public boolean isAllowedToUseCachedResults() {
            return true;
        }

        @Override
        public BuildCacheKey calculateCacheKey() {
            // Ensure that states are created
            getStates();
            return history.getCurrentExecution().calculateCacheKey();
        }

        public FileCollection getOutputFiles() {
            TaskExecution lastExecution = history.getPreviousExecution();
            if (lastExecution != null && lastExecution.getOutputFilesSnapshot() != null) {
                ImmutableSet.Builder<File> builder = ImmutableSet.builder();
                for (FileCollectionSnapshot snapshot : lastExecution.getOutputFilesSnapshot().values()) {
                    builder.addAll(snapshot.getFiles());
                }
                return fileCollectionFactory.fixed("Task " + task.getPath() + " outputs", builder.build());
            } else {
                return fileCollectionFactory.empty("Task " + task.getPath() + " outputs");
            }
        }

        public TaskExecutionHistory getExecutionHistory() {
            return this;
        }

        public void beforeTask() {
        }

        public void afterTask() {
            if (upToDate) {
                return;
            }

            if (taskInputs != null) {
                getStates().newInputs(taskInputs.getDiscoveredInputs());
            }
            getStates().getAllTaskChanges().snapshotAfterTask();
            history.update();
        }

        public void finished() {
        }

        private TaskUpToDateState getStates() {
            if (states == null) {
                // Calculate initial state - note this is potentially expensive
                states = new TaskUpToDateState(task, history, outputFilesSnapshotter, fileCollectionSnapshotterRegistry, fileCollectionFactory, classLoaderHierarchyHasher);
            }
            return states;
        }
    }

}
