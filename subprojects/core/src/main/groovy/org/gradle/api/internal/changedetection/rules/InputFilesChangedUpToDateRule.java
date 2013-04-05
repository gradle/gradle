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

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.*;

import java.util.ArrayList;

/**
 * A rule which detects changes in the input files of a task.
 */
public class InputFilesChangedUpToDateRule {
    // TODO:DAZ Unit test

    public static TaskUpToDateState create(final TaskInternal task, final TaskExecution previousExecution, final TaskExecution currentExecution, final FileSnapshotter inputFilesSnapshotter) {
        final FileCollectionSnapshot inputFilesSnapshot = inputFilesSnapshotter.snapshot(task.getInputs().getFiles());

        return new TaskUpToDateState() {
            private final ArrayList<FileChange> cachedChanges = new ArrayList<FileChange>();
            private String lastFileChange;

            public void findChanges(final UpToDateChangeListener listener) {
                if (previousExecution.getInputFilesSnapshot() == null) {
                    if (listener.isAccepting()) {
                        listener.accept(new DescriptiveChange("Input file history is not available for %s.", task));
                    }
                    return;
                }

                // First iterate over cached changes
                for (FileChange cachedChange : cachedChanges) {
                    if (!listener.isAccepting()) {
                        return;
                    }
                    listener.accept(cachedChange);
                }

                // TODO:DAZ Remember if all changes are already cached, so we don't need to do anything more.

                // Now get any new changes
                inputFilesSnapshot.changesSince(previousExecution.getInputFilesSnapshot(), new FileCollectionSnapshot.SnapshotChangeListener() {
                    public void added(String fileName) {
                        accept(new InputFileChange(fileName, ChangeType.ADDED));
                    }

                    public void removed(String fileName) {
                        accept(new InputFileChange(fileName, ChangeType.REMOVED));
                    }

                    public void changed(String fileName) {
                        accept(new InputFileChange(fileName, ChangeType.MODIFIED));
                    }

                    public String getResumeAfter() {
                        return lastFileChange;
                    }

                    public boolean isStopped() {
                        return !listener.isAccepting();
                    }

                    private void accept(InputFileChange change) {
                        assert listener.isAccepting();

                        listener.accept(change);

                        // TODO:DAZ Restrict how many changes are cached: for now we don't need to cache more than the max number reported in up-to-date check (10).
                        cachedChanges.add(change);
                        lastFileChange = change.getPath();
                    }
                });
            }

            public void snapshotAfterTask() {
                currentExecution.setInputFilesSnapshot(inputFilesSnapshot);
            }
        };
    }
}
