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
package org.gradle.api.internal.changedetection;

import org.gradle.api.internal.TaskInternal;
import org.gradle.util.ChangeListener;

import java.io.File;
import java.util.Collection;

/**
 * A rule which marks a task out-of-date when its input files change.
 */
public class InputFilesChangedUpToDateRule implements UpToDateRule {
    private final FileSnapshotter inputFilesSnapshotter;

    public InputFilesChangedUpToDateRule(FileSnapshotter inputFilesSnapshotter) {
        this.inputFilesSnapshotter = inputFilesSnapshotter;
    }

    public TaskUpToDateState create(final TaskInternal task, final TaskExecution previousExecution, final TaskExecution currentExecution) {
        final FileCollectionSnapshot inputFilesSnapshot = inputFilesSnapshotter.snapshot(task.getInputs().getFiles());

        return new TaskUpToDateState() {
            public void checkUpToDate(final Collection<String> messages) {
                if (previousExecution.getInputFilesSnapshot() == null) {
                    messages.add(String.format("Input file history is not available for %s.", task));
                    return;
                }
                inputFilesSnapshot.changesSince(previousExecution.getInputFilesSnapshot(), new ChangeListener<File>() {
                    public void added(File file) {
                        messages.add(String.format("Input file %s for %s added.", file, task));
                    }

                    public void removed(File file) {
                        messages.add(String.format("Input file %s for %s removed.", file, task));
                    }

                    public void changed(File file) {
                        messages.add(String.format("Input file %s for %s has changed.", file, task));
                    }
                });
            }

            public void snapshotAfterTask() {
                currentExecution.setInputFilesSnapshot(inputFilesSnapshot);
            }
        };
    }
}
