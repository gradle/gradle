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

import com.google.common.collect.Iterators;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.OutputFilesCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskExecution;

import java.util.EnumSet;
import java.util.Iterator;

public class OutputFilesTaskStateChanges extends AbstractFileSnapshotTaskStateChanges {
    private final TaskExecution previousExecution;
    private final TaskExecution currentExecution;
    private final TaskInternal task;
    private final OutputFilesCollectionSnapshotter outputFilesSnapshotter;
    private final FileCollectionSnapshot.PreCheck outputFilesBeforePreCheck;
    private final boolean noChanges;
    private FileCollectionSnapshot outputFilesBefore;

    public OutputFilesTaskStateChanges(TaskExecution previousExecution, TaskExecution currentExecution, TaskInternal task, OutputFilesCollectionSnapshotter outputFilesSnapshotter) {
        super(task.getName());
        this.previousExecution = previousExecution;
        this.currentExecution = currentExecution;
        this.task = task;
        this.outputFilesSnapshotter = outputFilesSnapshotter;
        outputFilesBeforePreCheck = createSnapshotPreCheck(outputFilesSnapshotter, task.getOutputs().getFiles());
        this.noChanges = previousExecution != null && previousExecution.getOutputFilesHash() != null && previousExecution.getOutputFilesHash().equals(outputFilesBeforePreCheck.getHash());
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
        if (outputFilesBefore == null) {
            outputFilesBefore = outputFilesSnapshotter.snapshot(outputFilesBeforePreCheck);
        }
        return outputFilesBefore;
    }

    @Override
    protected FileCollectionSnapshot.ChangeIterator<String> getChanges() {
        return getCurrent().iterateContentChangesSince(getPrevious(), EnumSet.of(FileCollectionSnapshot.ChangeFilter.IgnoreAddedFiles));
    }

    @Override
    public void saveCurrent() {
        FileCollectionSnapshot lastExecutionOutputFiles;
        if (previousExecution == null || previousExecution.getOutputFilesSnapshot() == null) {
            lastExecutionOutputFiles = outputFilesSnapshotter.emptySnapshot();
        } else {
            lastExecutionOutputFiles = previousExecution.getOutputFilesSnapshot();
        }

        FileCollectionSnapshot.PreCheck outputFilesAfterPreCheck = createSnapshotPreCheck(outputFilesSnapshotter, task.getOutputs().getFiles());
        FileCollectionSnapshot outputFilesAfter = createSnapshot(outputFilesSnapshotter, outputFilesAfterPreCheck);
        currentExecution.setOutputFilesSnapshot(outputFilesSnapshotter.createOutputSnapshot(lastExecutionOutputFiles, outputFilesBefore, outputFilesAfter, task.getOutputs().getFiles()));
        currentExecution.setOutputFilesHash(outputFilesAfterPreCheck.getHash());
    }

    @Override
    protected boolean isAllowSnapshotReuse() {
        return false;
    }

    @Override
    public Iterator<TaskStateChange> iterator() {
        if (noChanges) {
            return Iterators.emptyIterator();
        }
        return super.iterator();
    }
}
