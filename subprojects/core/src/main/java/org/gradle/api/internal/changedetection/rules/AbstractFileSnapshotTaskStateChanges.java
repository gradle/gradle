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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareType;

import java.util.Collections;
import java.util.Iterator;

abstract class AbstractFileSnapshotTaskStateChanges implements TaskStateChanges {
    private final String taskName;

    protected AbstractFileSnapshotTaskStateChanges(String taskName) {
        this.taskName = taskName;
    }

    protected abstract String getInputFileType();
    protected abstract FileCollectionSnapshot getPrevious();
    protected abstract FileCollectionSnapshot getCurrent();
    protected abstract void saveCurrent();

    protected Iterator<TaskStateChange> getChanges(String fileType) {
        return getCurrent().iterateContentChangesSince(getPrevious(), fileType);
    }

    protected FileCollectionSnapshot createSnapshot(FileCollectionSnapshotter snapshotter, FileCollection fileCollection, TaskFilePropertyCompareType compareType) {
        try {
            return snapshotter.snapshot(fileCollection, compareType);
        } catch (UncheckedIOException e) {
            throw new UncheckedIOException(String.format("Failed to capture snapshot of %s files for task '%s' during up-to-date check.", getInputFileType().toLowerCase(), taskName), e);
        }
    }

    public Iterator<TaskStateChange> iterator() {
        if (getPrevious() == null) {
            return Collections.<TaskStateChange>singleton(new DescriptiveChange(getInputFileType() + " file history is not available.")).iterator();
        }
        return getChanges(getInputFileType());
    }

    public void snapshotAfterTask() {
        saveCurrent();
    }
}
