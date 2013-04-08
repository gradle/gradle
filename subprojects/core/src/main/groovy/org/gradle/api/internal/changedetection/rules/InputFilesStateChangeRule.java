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
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskExecution;

/**
 * A rule which detects changes in the input files of a task.
 */
class InputFilesStateChangeRule {
    public static TaskStateChanges create(final TaskInternal task, final TaskExecution previousExecution, final TaskExecution currentExecution, final FileSnapshotter inputFilesSnapshotter,
                                          final int maxCachedChanges) {
        final FileCollectionSnapshot inputFilesSnapshot = inputFilesSnapshotter.snapshot(task.getInputs().getFiles());

        return new TaskStateChanges() {
            private final FileChangeCache fileChangeCache = new FileChangeCache(maxCachedChanges);

            public void findChanges(final UpToDateChangeListener listener) {
                if (previousExecution.getInputFilesSnapshot() == null) {
                    if (listener.isAccepting()) {
                        listener.accept(new DescriptiveChange("Input file history is not available."));
                    }
                    return;
                }

                // First iterate over cached changes
                for (FileChange cachedChange : fileChangeCache) {
                    if (!listener.isAccepting()) {
                        return;
                    }
                    listener.accept(cachedChange);
                }

                if (fileChangeCache.isComplete()) {
                    return;
                }

                processUncachedChanges(listener);

                // If the listener stopped accepting, then we don't know if there are entries missing from the cache.
                if (listener.isAccepting()) {
                    fileChangeCache.hasSeenAllChanges();
                }
            }

            private void processUncachedChanges(final UpToDateChangeListener listener) {
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
                        return fileChangeCache.getLastCachedChange();
                    }

                    public boolean isStopped() {
                        return !listener.isAccepting();
                    }

                    private void accept(InputFileChange change) {
                        listener.accept(change);
                        fileChangeCache.cache(change);
                    }
                });
            }

            public void snapshotAfterTask() {
                currentExecution.setInputFilesSnapshot(inputFilesSnapshot);
            }
        };
    }

}
