/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.util.ChangeListener;

public class OutputFilesTaskStateChanges extends AbstractFileSnapshotTaskStateChanges {
    private final TaskExecution previousExecution;
    private final TaskExecution currentExecution;
    private final TaskInternal task;
    private final FileCollectionSnapshotter outputFilesSnapshotter;
    private final FileCollectionSnapshot outputFilesBefore;

    public OutputFilesTaskStateChanges(TaskExecution previousExecution, TaskExecution currentExecution, TaskInternal task, FileCollectionSnapshotter outputFilesSnapshotter) {
        super(task.getName());
        this.previousExecution = previousExecution;
        this.currentExecution = currentExecution;
        this.task = task;
        this.outputFilesSnapshotter = outputFilesSnapshotter;
        outputFilesBefore = createSnapshot(outputFilesSnapshotter, task.getOutputs().getFiles());
    }

    @Override
    protected String getInputFileType() {
        return "Output";
    }

    @Override
    public FileCollectionSnapshot getPrevious() {
        return previousExecution.getOutputFilesSnapshot();
    }

    @Override
    public FileCollectionSnapshot getCurrent() {
        return outputFilesBefore;
    }

    @Override
    public void saveCurrent() {
        FileCollectionSnapshot lastExecutionOutputFiles;
        if (previousExecution == null || previousExecution.getOutputFilesSnapshot() == null) {
            lastExecutionOutputFiles = outputFilesSnapshotter.emptySnapshot();
        } else {
            lastExecutionOutputFiles = previousExecution.getOutputFilesSnapshot();
        }
        FileCollectionSnapshot newOutputFiles = outputFilesBefore.changesSince(lastExecutionOutputFiles).applyTo(
                lastExecutionOutputFiles, new ChangeListener<FileCollectionSnapshot.Merge>() {
                    public void added(FileCollectionSnapshot.Merge element) {
                        // Ignore added files
                        element.ignore();
                    }

                    public void removed(FileCollectionSnapshot.Merge element) {
                        // Discard any files removed since the task was last executed
                    }

                    public void changed(FileCollectionSnapshot.Merge element) {
                        // Update any files which were change since the task was last executed
                    }
                });
        FileCollectionSnapshot outputFilesAfter = createSnapshot(outputFilesSnapshotter, task.getOutputs().getFiles());
        currentExecution.setOutputFilesSnapshot(outputFilesAfter.changesSince(outputFilesBefore).applyTo(newOutputFiles));
    }
}
