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
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotterRegistry;
import org.gradle.api.internal.changedetection.state.GenericFileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy;
import org.gradle.api.internal.changedetection.state.TaskFilePropertySnapshotNormalizationStrategy;
import org.gradle.api.internal.file.FileCollectionFactory;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

public class DiscoveredInputsTaskStateChanges implements TaskStateChanges, DiscoveredInputsListener {
    private final String taskName;
    private final FileCollectionSnapshotter snapshotter;
    private final FileCollectionFactory fileCollectionFactory;
    private final TaskExecution previous;
    private final TaskExecution current;
    private Collection<File> discoveredFiles = Collections.emptySet();

    public DiscoveredInputsTaskStateChanges(TaskExecution previous, TaskExecution current, FileCollectionSnapshotterRegistry snapshotterRegistry, FileCollectionFactory fileCollectionFactory,
                                            TaskInternal task) {
        this.taskName = task.getName();
        this.snapshotter = snapshotterRegistry.getSnapshotter(GenericFileCollectionSnapshotter.class);
        this.fileCollectionFactory = fileCollectionFactory;
        this.previous = previous;
        this.current = current;
    }

    private FileCollectionSnapshot getPrevious() {
        return previous != null ? previous.getDiscoveredInputFilesSnapshot() : null;
    }

    private FileCollectionSnapshot getCurrent() {
        if (getPrevious() != null) {
            // Get the current state of the files from the previous execution
            return createSnapshot(snapshotter, fileCollectionFactory.fixed("Discovered input files", getPrevious().getElements()));
        } else {
            return null;
        }
    }

    @Override
    public Iterator<TaskStateChange> iterator() {
        if (getPrevious() == null) {
            return Iterators.<TaskStateChange>singletonIterator(new DescriptiveChange("Discovered input file history is not available."));
        }
        return getCurrent().iterateContentChangesSince(getPrevious(), "discovered input");
    }

    @Override
    public void snapshotAfterTask() {
        FileCollectionSnapshot discoveredFilesSnapshot = createSnapshot(snapshotter, fileCollectionFactory.fixed("Discovered input files", discoveredFiles));
        current.setDiscoveredInputFilesSnapshot(discoveredFilesSnapshot);
    }

    @Override
    public void newInputs(Set<File> files) {
        this.discoveredFiles = files;
    }

    private FileCollectionSnapshot createSnapshot(FileCollectionSnapshotter snapshotter, FileCollection fileCollection) {
        try {
            return snapshotter.snapshot(fileCollection, TaskFilePropertyCompareStrategy.UNORDERED, TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE);
        } catch (UncheckedIOException e) {
            throw new UncheckedIOException(String.format("Failed to capture snapshot of discovered input files for task '%s' during up-to-date check.", taskName), e);
        }
    }
}
