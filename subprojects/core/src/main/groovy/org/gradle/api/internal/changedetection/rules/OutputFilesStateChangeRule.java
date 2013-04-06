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
import org.gradle.util.ChangeListener;

/**
 * A rule which detects changes in output files.
 */
class OutputFilesStateChangeRule {

    public static TaskStateChanges create(final TaskInternal task, final TaskExecution previousExecution, final TaskExecution currentExecution, final FileSnapshotter outputFilesSnapshotter) {
        final FileCollectionSnapshot outputFilesBefore = outputFilesSnapshotter.snapshot(task.getOutputs().getFiles());

        return new TaskStateChanges() {
            public void findChanges(final UpToDateChangeListener listener) {
                if (previousExecution.getOutputFilesSnapshot() == null) {
                    if (listener.isAccepting()) {
                        listener.accept(new DescriptiveChange("Output file history is not available for %s.", task));
                    }
                    return;
                }
                outputFilesBefore.changesSince(previousExecution.getOutputFilesSnapshot(), new FileCollectionSnapshot.SnapshotChangeListener() {
                    public void added(String element) {
                        accept(new OutputFileChange(element, ChangeType.ADDED));
                    }

                    public void changed(String element) {
                        accept(new OutputFileChange(element, ChangeType.MODIFIED));
                    }

                    public void removed(String element) {
                        accept(new OutputFileChange(element, ChangeType.REMOVED));
                    }

                    public String getResumeAfter() {
                        return null;
                    }

                    public boolean isStopped() {
                        return !listener.isAccepting();
                    }

                    private void accept(OutputFileChange change) {
                        listener.accept(change);
                    }
                });
            }

            public void snapshotAfterTask() {
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
                FileCollectionSnapshot outputFilesAfter = outputFilesSnapshotter.snapshot(task.getOutputs().getFiles());
                currentExecution.setOutputFilesSnapshot(outputFilesAfter.changesSince(outputFilesBefore).applyTo(newOutputFiles));
            }
        };
    }
}
