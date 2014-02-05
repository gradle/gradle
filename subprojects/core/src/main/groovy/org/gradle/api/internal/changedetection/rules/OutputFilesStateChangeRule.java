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

import com.google.common.collect.AbstractIterator;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.util.ChangeListener;

import java.util.Collections;
import java.util.Iterator;

/**
 * A rule which detects changes in output files.
 */
class OutputFilesStateChangeRule {

    public static TaskStateChanges create(final TaskInternal task, final TaskExecution previousExecution, final TaskExecution currentExecution, final FileCollectionSnapshotter outputFilesSnapshotter) {
        final FileCollectionSnapshot outputFilesBefore = outputFilesSnapshotter.snapshot(task.getOutputs().getFiles());

        return new TaskStateChanges() {

            public Iterator<TaskStateChange> iterator() {
                if (previousExecution.getOutputFilesSnapshot() == null) {
                    return Collections.<TaskStateChange>singleton(new DescriptiveChange("Output file history is not available.")).iterator();
                }

                return new AbstractIterator<TaskStateChange>() {
                    final FileCollectionSnapshot.ChangeIterator<String> changeIterator = outputFilesBefore.iterateChangesSince(previousExecution.getOutputFilesSnapshot());
                    final ChangeListenerAdapter listenerAdapter = new ChangeListenerAdapter();

                    @Override
                    protected TaskStateChange computeNext() {
                        if (changeIterator.next(listenerAdapter)) {
                            return listenerAdapter.lastChange;
                        }
                        return endOfData();
                    }
                };
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

    private static class ChangeListenerAdapter implements ChangeListener<String> {
        public OutputFileChange lastChange;

        public void added(String fileName) {
            lastChange = new OutputFileChange(fileName, ChangeType.ADDED);
        }

        public void removed(String fileName) {
            lastChange = new OutputFileChange(fileName, ChangeType.REMOVED);
        }

        public void changed(String fileName) {
            lastChange = new OutputFileChange(fileName, ChangeType.MODIFIED);
        }
    }
}
