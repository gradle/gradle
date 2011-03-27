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
 * A rule which marks a task out-of-date when its output files change.
 */
public class OutputFilesChangedUpToDateRule implements UpToDateRule {
    private final FileSnapshotter outputFilesSnapshotter;

    public OutputFilesChangedUpToDateRule(FileSnapshotter outputFilesSnapshotter) {
        this.outputFilesSnapshotter = outputFilesSnapshotter;
    }

    public TaskUpToDateState create(final TaskInternal task, final TaskExecution previousExecution, final TaskExecution currentExecution) {
        final FileCollectionSnapshot outputFilesBefore = outputFilesSnapshotter.snapshot(task.getOutputs().getFiles());

        return new TaskUpToDateState() {
            public void checkUpToDate(final Collection<String> messages) {
                if (previousExecution.getOutputFilesSnapshot() == null) {
                    messages.add(String.format("Output file history is not available for %s.", task));
                    return;
                }
                outputFilesBefore.changesSince(previousExecution.getOutputFilesSnapshot(), new ChangeListener<File>() {
                    public void added(File element) {
                        messages.add(String.format("Output file '%s' has been added for %s.", element, task));
                    }

                    public void removed(File element) {
                        messages.add(String.format("Output file %s has been removed for %s.", element.getAbsolutePath(), task));
                    }

                    public void changed(File element) {
                        messages.add(String.format("Output file %s for %s has changed.", element.getAbsolutePath(), task));
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
