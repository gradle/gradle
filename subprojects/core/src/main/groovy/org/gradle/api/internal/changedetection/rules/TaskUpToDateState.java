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
import org.gradle.api.internal.file.FileCollectionFactory;

import java.io.File;
import java.util.Set;

/**
 * Represents the complete changes in a tasks state
 */
public class TaskUpToDateState {
    private static final int MAX_OUT_OF_DATE_MESSAGES = 3;
    private final FilesSnapshotSet inputFilesSnapshot;

    private TaskStateChanges inputFileChanges;
    private DiscoveredInputsListener discoveredInputsListener;
    private SummaryTaskStateChanges allTaskChanges;
    private SummaryTaskStateChanges rebuildChanges;

    public TaskUpToDateState(TaskInternal task, TaskHistoryRepository.History history,
                             FileCollectionSnapshotter outputFilesSnapshotter, FileCollectionSnapshotter inputFilesSnapshotter,
                             FileCollectionSnapshotter discoveredInputsSnapshotter, FileCollectionFactory fileCollectionFactory) {
        TaskExecution thisExecution = history.getCurrentExecution();
        TaskExecution lastExecution = history.getPreviousExecution();

        TaskStateChanges noHistoryState = NoHistoryStateChangeRule.create(task, lastExecution);
        TaskStateChanges taskTypeState = TaskTypeStateChangeRule.create(task, lastExecution, thisExecution);
        TaskStateChanges inputPropertiesState = InputPropertiesStateChangeRule.create(task, lastExecution, thisExecution);

        // Capture outputs state
        TaskStateChanges outputFileChanges;
        try {
            SnapshotAccess outputFileSnapshotAccess = new OutputFilesSnapshotAccess(lastExecution, thisExecution, task, outputFilesSnapshotter);
            outputFileChanges = caching(FileSnapshotStateChangeRule.create(outputFileSnapshotAccess, "Output"));
        } catch (UncheckedIOException e) {
            throw new UncheckedIOException(String.format("Failed to capture snapshot of output files for task '%s' during up-to-date check.", task.getName()), e);
        }

        // Capture inputs state
        TaskStateChanges inputFileChanges;
        try {
            FileCollectionSnapshot inputFilesSnapshot = inputFilesSnapshotter.snapshot(task.getInputs().getFiles());
            this.inputFilesSnapshot = inputFilesSnapshot.getSnapshot();
            inputFileChanges = caching(FileSnapshotStateChangeRule.create(new InputFilesSnapshotAccess(lastExecution, thisExecution, inputFilesSnapshot), "Input"));
        } catch (UncheckedIOException e) {
            throw new UncheckedIOException(String.format("Failed to capture snapshot of input files for task '%s' during up-to-date check.", task.getName()), e);
        }

        // Capture discovered inputs state from previous execution
        TaskStateChanges discoveredInputFilesChanges;
        try {
            DiscoveredInputFilesSnapshotAccess discoveredInputFilesSnapshotAccess = new DiscoveredInputFilesSnapshotAccess(discoveredInputsSnapshotter, fileCollectionFactory, lastExecution, thisExecution);
            this.discoveredInputsListener = discoveredInputFilesSnapshotAccess;
            discoveredInputFilesChanges = FileSnapshotStateChangeRule.create(discoveredInputFilesSnapshotAccess, "Discovered input");
        } catch (UncheckedIOException e) {
            throw new UncheckedIOException(String.format("Failed to capture snapshot of input files for task '%s' during up-to-date check.", task.getName()), e);
        }

        allTaskChanges = new SummaryTaskStateChanges(MAX_OUT_OF_DATE_MESSAGES, noHistoryState, taskTypeState, inputPropertiesState, outputFileChanges, inputFileChanges, discoveredInputFilesChanges);
        rebuildChanges = new SummaryTaskStateChanges(1, noHistoryState, taskTypeState, inputPropertiesState, outputFileChanges);
        this.inputFileChanges = inputFileChanges;
    }

    private TaskStateChanges caching(TaskStateChanges wrapped) {
        return new CachingTaskStateChanges(MAX_OUT_OF_DATE_MESSAGES, wrapped);
    }

    public TaskStateChanges getInputFilesChanges() {
        return inputFileChanges;
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

    public void newInputs(Set<File> discoveredInputs) {
        discoveredInputsListener.newInputs(discoveredInputs);
    }
}
