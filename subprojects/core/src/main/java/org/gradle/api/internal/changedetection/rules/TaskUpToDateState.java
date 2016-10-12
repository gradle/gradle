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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotterRegistry;
import org.gradle.api.internal.changedetection.state.OutputFilesSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.api.internal.changedetection.state.TaskHistoryRepository;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;

import java.io.File;
import java.util.Set;

/**
 * Represents the complete changes in a tasks state
 */
public class TaskUpToDateState {
    private static final int MAX_OUT_OF_DATE_MESSAGES = 3;

    private TaskStateChanges inputFileChanges;
    private DiscoveredInputsListener discoveredInputsListener;
    private SummaryTaskStateChanges allTaskChanges;
    private SummaryTaskStateChanges rebuildChanges;

    public TaskUpToDateState(TaskInternal task, TaskHistoryRepository.History history,
                             OutputFilesSnapshotter outputFilesSnapshotter, FileCollectionSnapshotterRegistry fileCollectionSnapshotterRegistry,
                             FileCollectionFactory fileCollectionFactory, ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
        TaskExecution thisExecution = history.getCurrentExecution();
        TaskExecution lastExecution = history.getPreviousExecution();

        TaskStateChanges noHistoryState = new NoHistoryTaskStateChanges(lastExecution);
        TaskStateChanges taskTypeState = new TaskTypeTaskStateChanges(lastExecution, thisExecution, task.getPath(), task.getClass(), task.getActionClassLoaders(), classLoaderHierarchyHasher);
        TaskStateChanges inputPropertiesState = new InputPropertiesTaskStateChanges(lastExecution, thisExecution, task);

        // Capture outputs state
        TaskStateChanges outputFileChanges = caching(new OutputFilesTaskStateChanges(lastExecution, thisExecution, task, fileCollectionSnapshotterRegistry, outputFilesSnapshotter));

        // Capture inputs state
        InputFilesTaskStateChanges directInputFileChanges = new InputFilesTaskStateChanges(lastExecution, thisExecution, task, fileCollectionSnapshotterRegistry);
        this.inputFileChanges = caching(directInputFileChanges);

        // Capture discovered inputs state from previous execution
        DiscoveredInputsTaskStateChanges discoveredChanges = new DiscoveredInputsTaskStateChanges(lastExecution, thisExecution, fileCollectionSnapshotterRegistry, fileCollectionFactory, task);
        this.discoveredInputsListener = discoveredChanges;
        TaskStateChanges discoveredInputFilesChanges = caching(discoveredChanges);

        allTaskChanges = new SummaryTaskStateChanges(MAX_OUT_OF_DATE_MESSAGES, noHistoryState, taskTypeState, inputPropertiesState, outputFileChanges, inputFileChanges, discoveredInputFilesChanges);
        rebuildChanges = new SummaryTaskStateChanges(1, noHistoryState, taskTypeState, inputPropertiesState, outputFileChanges);
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

    public void newInputs(Set<File> discoveredInputs) {
        discoveredInputsListener.newInputs(discoveredInputs);
    }
}
