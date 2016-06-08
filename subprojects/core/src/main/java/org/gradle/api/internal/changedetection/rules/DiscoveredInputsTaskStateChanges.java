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
import org.gradle.api.internal.file.FileCollectionFactory;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class DiscoveredInputsTaskStateChanges extends AbstractFileSnapshotTaskStateChanges implements DiscoveredInputsListener {
    private final FileCollectionSnapshotter snapshotter;
    private final FileCollectionFactory fileCollectionFactory;
    private final TaskExecution previous;
    private final TaskExecution current;
    private Collection<File> discoveredFiles = Collections.emptySet();

    public DiscoveredInputsTaskStateChanges(TaskExecution previous, TaskExecution current, FileCollectionSnapshotter snapshotter, FileCollectionFactory fileCollectionFactory,
                                            TaskInternal task) {
        super(task.getName());
        this.snapshotter = snapshotter;
        this.fileCollectionFactory = fileCollectionFactory;
        this.previous = previous;
        this.current = current;
    }

    @Override
    public FileCollectionSnapshot getPrevious() {
        return previous != null ? previous.getDiscoveredInputFilesSnapshot() : null;
    }

    @Override
    public FileCollectionSnapshot getCurrent() {
        if (getPrevious() != null) {
            // Get the current state of the files from the previous execution
            return createSnapshot(snapshotter, createSnapshotPreCheck(snapshotter, fileCollectionFactory.fixed("Discovered input files", getPrevious().getFiles())));
        } else {
            return null;
        }
    }

    @Override
    protected boolean isAllowSnapshotReuse() {
        return false;
    }

    @Override
    public void saveCurrent() {
        FileCollectionSnapshot discoveredFilesSnapshot = createSnapshot(snapshotter, createSnapshotPreCheck(snapshotter, fileCollectionFactory.fixed("Discovered input files", discoveredFiles)));
        current.setDiscoveredInputFilesSnapshot(discoveredFilesSnapshot);
    }

    public void newInputs(Set<File> files) {
        this.discoveredFiles = files;
    }

    @Override
    protected String getInputFileType() {
        return "Discovered input";
    }
}
