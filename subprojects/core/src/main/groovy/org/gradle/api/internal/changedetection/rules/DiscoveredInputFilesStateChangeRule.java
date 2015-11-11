/*
 * Copyright 2015 the original author or authors.
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
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.util.ChangeListener;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

public class DiscoveredInputFilesStateChangeRule {
    public static DiscoveredTaskStateChanges create(final TaskExecution previousExecution, final TaskExecution currentExecution, final FileCollectionSnapshotter inputFilesSnapshotter) {
        return new DiscoveredTaskStateChanges() {
            private final Collection<File> discoveredFiles = Sets.newHashSet();

            public Iterator<TaskStateChange> iterator() {
                if (previousExecution.getDiscoveredInputFilesSnapshot() == null) {
                    return Collections.<TaskStateChange>singleton(new DescriptiveChange("Discovered input file history is not available.")).iterator();
                }

                Iterables.addAll(discoveredFiles, previousExecution.getDiscoveredInputFilesSnapshot().getFiles());
                final FileCollectionSnapshot discoveredFileSnapshot = inputFilesSnapshotter.snapshot(new SimpleFileCollection(discoveredFiles));

                return new AbstractIterator<TaskStateChange>() {
                    final FileCollectionSnapshot.ChangeIterator<String> changeIterator = discoveredFileSnapshot.iterateChangesSince(previousExecution.getDiscoveredInputFilesSnapshot());
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

            @Override
            public void newInputs(Set<File> files) {
                discoveredFiles.clear();
                discoveredFiles.addAll(files);
            }

            public void snapshotAfterTask() {
                currentExecution.setDiscoveredInputFilesSnapshot(inputFilesSnapshotter.snapshot(new SimpleFileCollection(discoveredFiles)));
            }
        };
    }

    private static class ChangeListenerAdapter implements ChangeListener<String> {
        public InputFileChange lastChange;

        public void added(String fileName) {
            lastChange = new DiscoveredInputFileChange(fileName, ChangeType.ADDED);
        }

        public void removed(String fileName) {
            lastChange = new DiscoveredInputFileChange(fileName, ChangeType.REMOVED);
        }

        public void changed(String fileName) {
            lastChange = new DiscoveredInputFileChange(fileName, ChangeType.MODIFIED);
        }
    }
}
