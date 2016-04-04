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
import org.gradle.api.file.FileTreeElement;
import org.gradle.util.ChangeListener;

import java.io.File;
import java.util.Collection;
import java.util.Set;

/**
 * An immutable snapshot of the contents and meta-data of a collection of files or directories.
 */
public interface FileCollectionSnapshot {
    enum ChangeFilter {
        IgnoreAddedFiles
    }

    /**
     * Returns an iterator over the changes to file contents since the given snapshot, subject to the given filters.
     *
     * <p>Note: Ignores changes to file meta-data, such as last modified time. This should be made a {@link ChangeFilter} at some point.
     */
    ChangeIterator<String> iterateContentChangesSince(FileCollectionSnapshot oldSnapshot, Set<ChangeFilter> filters);

    /**
     * Returns a copy of this snapshot with file details updated from the given snapshot. Ignores files that are present in the given snapshot but not this snapshot.
     * Removes files that are present in this snapshot but not the given snapshot.
     */
    FileCollectionSnapshot updateFrom(FileCollectionSnapshot newSnapshot);

    /**
     * Applies any changes to file contents since the given old snapshot, to the given target snapshot.
     *
     * <p>Note: <em>Includes changes to file meta-data, such as last modified time.</em> This should be made a {@link ChangeFilter} at some point.
     */
    FileCollectionSnapshot applyAllChangesSince(FileCollectionSnapshot oldSnapshot, FileCollectionSnapshot target);

    Collection<File> getFiles();

    FilesSnapshotSet getSnapshot();

    interface ChangeIterator<T> {
        boolean next(ChangeListener<T> listener);
    }

    interface PreCheck {
        FileCollection getFileCollection();

        Integer getHash();

        Collection<FileTreeElement> getFileTreeElements();

        Collection<File> getMissingFiles();
    }
}
