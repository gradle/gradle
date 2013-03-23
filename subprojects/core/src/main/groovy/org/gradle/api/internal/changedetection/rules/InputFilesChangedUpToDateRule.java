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
package org.gradle.api.internal.changedetection.rules;

import org.gradle.api.Action;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.*;
import org.gradle.util.ChangeListener;

import java.io.File;

/**
 * A rule which detects changes in the input files of a task.
 */
public class InputFilesChangedUpToDateRule {

    public static TaskUpToDateState create(final TaskInternal task, final TaskExecution previousExecution, final TaskExecution currentExecution, final FileSnapshotter inputFilesSnapshotter) {
        final FileCollectionSnapshot inputFilesSnapshot = inputFilesSnapshotter.snapshot(task.getInputs().getFiles());

        return new TaskUpToDateState() {
            public void findChanges(final Action<? super TaskUpToDateStateChange> failures) {
                if (previousExecution.getInputFilesSnapshot() == null) {
                    failures.execute(new DescriptiveChange("Input file history is not available for %s.", task));
                    return;
                }
                inputFilesSnapshot.changesSince(previousExecution.getInputFilesSnapshot(), new ChangeListener<File>() {
                    public void added(File file) {
                        failures.execute(new InputFileChange(task, file, ChangeType.ADDED));
                    }

                    public void removed(File file) {
                        failures.execute(new InputFileChange(task, file, ChangeType.REMOVED));
                    }

                    public void changed(File file) {
                        failures.execute(new InputFileChange(task, file, ChangeType.MODIFIED));
                    }
                });
            }

            public void snapshotAfterTask() {
                currentExecution.setInputFilesSnapshot(inputFilesSnapshot);
            }
        };
    }
}
