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

public class InputFilesTaskStateChanges extends AbstractFileSnapshotTaskStateChanges {
    private final TaskExecution previous;
    private final TaskExecution current;
    private final FileCollectionSnapshot inputFilesSnapshot;

    public InputFilesTaskStateChanges(TaskExecution previous, TaskExecution current, TaskInternal task, FileCollectionSnapshotter snapshotter) {
        super(task.getName());
        this.previous = previous;
        this.current = current;
        inputFilesSnapshot = createSnapshot(snapshotter, task.getInputs().getFiles());
    }

    @Override
    public FileCollectionSnapshot getPrevious() {
        return previous.getInputFilesSnapshot();
    }

    @Override
    public FileCollectionSnapshot getCurrent() {
        return inputFilesSnapshot;
    }

    @Override
    public void saveCurrent() {
        // Inputs are considered to be unchanged during task execution
        current.setInputFilesSnapshot(inputFilesSnapshot);
    }

    @Override
    protected String getInputFileType() {
        return "Input";
    }
}
