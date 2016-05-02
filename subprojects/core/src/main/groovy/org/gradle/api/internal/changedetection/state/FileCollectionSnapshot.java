/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.file.FileCollection;
import org.gradle.util.ChangeListener;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * An immutable snapshot of the contents and meta-data of a collection of files or directories.
 */
public interface FileCollectionSnapshot {
    enum ChangeFilter {
        IgnoreAddedFiles
    }

    boolean isEmpty();

    /**
     * Returns an iterator over the changes to file contents since the given snapshot, subject to the given filters.
     *
     * <p>Note: Ignores changes to file meta-data, such as last modified time. This should be made a {@link ChangeFilter} at some point.
     */
    ChangeIterator<String> iterateContentChangesSince(FileCollectionSnapshot oldSnapshot, Set<ChangeFilter> filters);

    Collection<File> getFiles();

    Map<String, IncrementalFileSnapshot> getSnapshots();

    FilesSnapshotSet getSnapshot();

    interface ChangeIterator<T> {
        boolean next(ChangeListener<T> listener);
    }

    interface PreCheck {
        Integer getHash();

        FileCollection getFiles();

        Collection<VisitedTree> getVisitedTrees();

        Collection<File> getMissingFiles();

        boolean isEmpty();
    }

    Collection<Long> getTreeSnapshotIds();
}
