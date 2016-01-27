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

package org.gradle.api.internal.changedetection.rules;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.*;

/**
 * Represents the complete changes in a tasks state
 */
public class TaskUpToDateState {
    private static final int MAX_OUT_OF_DATE_MESSAGES = 3;
    private final FilesSnapshotSet inputFilesSnapshot;

    private TaskStateChanges inputFilesState;
    private DiscoveredTaskStateChanges discoveredInputFilesState;
    private SummaryTaskStateChanges allTaskChanges;
    private SummaryTaskStateChanges rebuildChanges;

    public TaskUpToDateState(TaskInternal task, TaskHistoryRepository.History history, FileCollectionSnapshotter outputFilesSnapshotter, FileCollectionSnapshotter inputFilesSnapshotter, DiscoveredInputFilesStateChangeRule discoveredInputFilesStateChangeRule) {
        TaskExecution thisExecution = history.getCurrentExecution();
        TaskExecution lastExecution = history.getPreviousExecution();

        TaskStateChanges noHistoryState = NoHistoryStateChangeRule.create(task, lastExecution);
        TaskStateChanges taskTypeState = TaskTypeStateChangeRule.create(task, lastExecution, thisExecution);
        TaskStateChanges inputPropertiesState = InputPropertiesStateChangeRule.create(task, lastExecution, thisExecution);

        // Capture outputs state
        TaskStateChanges outputFilesState;
        try {
            outputFilesState = caching(OutputFilesStateChangeRule.create(task, lastExecution, thisExecution, outputFilesSnapshotter));
        } catch (UncheckedIOException e) {
            throw new UncheckedIOException(String.format("Failed to capture snapshot of output files for task '%s' during up-to-date check.", task.getName()), e);
        }

        // Capture inputs state
        try {
            FileCollectionSnapshot inputFilesSnapshot = inputFilesSnapshotter.snapshot(task.getInputs().getFiles());
            this.inputFilesSnapshot = inputFilesSnapshot.getSnapshot();
            inputFilesState = caching(InputFilesStateChangeRule.create(lastExecution, thisExecution, inputFilesSnapshot));
        } catch (UncheckedIOException e) {
            throw new UncheckedIOException(String.format("Failed to capture snapshot of input files for task '%s' during up-to-date check.", task.getName()), e);
        }

        // Capture discovered inputs state from previous execution
        try {
            discoveredInputFilesState = discoveredInputFilesStateChangeRule.create(lastExecution, thisExecution);
        } catch (UncheckedIOException e) {
            throw new UncheckedIOException(String.format("Failed to capture snapshot of input files for task '%s' during up-to-date check.", task.getName()), e);
        }

        allTaskChanges = new SummaryTaskStateChanges(MAX_OUT_OF_DATE_MESSAGES, noHistoryState, taskTypeState, inputPropertiesState, outputFilesState, inputFilesState, caching(discoveredInputFilesState));
        rebuildChanges = new SummaryTaskStateChanges(1, noHistoryState, taskTypeState, inputPropertiesState, outputFilesState);
    }

    private TaskStateChanges caching(TaskStateChanges wrapped) {
        return new CachingTaskStateChanges(MAX_OUT_OF_DATE_MESSAGES, wrapped);
    }

    public TaskStateChanges getInputFilesChanges() {
        return inputFilesState;
    }

    public TaskStateChanges getAllTaskChanges() {
        return allTaskChanges;
    }

    public TaskStateChanges getRebuildChanges() {
        return rebuildChanges;
    }

    public FilesSnapshotSet getInputFilesSnapshot() {
        return inputFilesSnapshot;
    }

    public DiscoveredTaskStateChanges getDiscoveredInputFilesChanges() {
        return discoveredInputFilesState;
    }
}
