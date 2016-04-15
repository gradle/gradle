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
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskExecution;

import java.util.Iterator;

public class InputFilesTaskStateChanges extends AbstractFileSnapshotTaskStateChanges {
    private final TaskExecution previous;
    private final TaskExecution current;
    private final FileCollectionSnapshotter snapshotter;
    private final FileCollectionSnapshot.PreCheck inputFilesSnapshotPreCheck;
    private FileCollectionSnapshot inputFilesSnapshot;
    private final boolean noChanges;

    public InputFilesTaskStateChanges(TaskExecution previous, TaskExecution current, TaskInternal task, FileCollectionSnapshotter snapshotter) {
        super(task.getName());
        this.previous = previous;
        this.current = current;
        this.snapshotter = snapshotter;
        inputFilesSnapshotPreCheck = createSnapshotPreCheck(snapshotter, task.getInputs().getFiles());
        this.noChanges = previous != null && previous.getInputFilesHash() != null && previous.getInputFilesHash().equals(inputFilesSnapshotPreCheck.getHash());
    }

    @Override
    public FileCollectionSnapshot getPrevious() {
        return previous.getInputFilesSnapshot();
    }

    @Override
    public FileCollectionSnapshot getCurrent() {
        if (inputFilesSnapshot == null) {
            inputFilesSnapshot = createSnapshot(snapshotter, inputFilesSnapshotPreCheck);
        }
        return inputFilesSnapshot;
    }

    @Override
    public void saveCurrent() {
        // Inputs are considered to be unchanged during task execution
        current.setInputFilesHash(inputFilesSnapshotPreCheck.getHash());
        current.setInputFilesSnapshot(getCurrent());
    }

    public Iterator<TaskStateChange> iterator() {
        if (noChanges) {
            return Iterators.emptyIterator();
        }
        return super.iterator();
    }

    @Override
    protected String getInputFileType() {
        return "Input";
    }
}
