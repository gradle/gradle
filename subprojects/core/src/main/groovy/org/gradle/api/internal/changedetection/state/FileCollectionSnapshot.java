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

/**
 * An immutable snapshot of the contents of a collection of files.
 */
public interface FileCollectionSnapshot {

    ChangeIterator<String> iterateChangesSince(FileCollectionSnapshot oldSnapshot);

    Diff changesSince(FileCollectionSnapshot oldSnapshot);

    FileCollection getFiles();

    public interface Diff {
        /**
         * Applies this diff to the given snapshot. Adds any added or changed files in this diff to the given snapshot.
         * Removes any removed files in this diff from the given snapshot.
         *
         * @param snapshot the snapshot to apply the changes to.
         * @param listener the listener to notify of changes. The listener can veto a particular change.
         * @return an updated copy of the provided snapshot
         */
        FileCollectionSnapshot applyTo(FileCollectionSnapshot snapshot, ChangeListener<Merge> listener);

        /**
         * Applies this diff to the given snapshot. Adds any added or changed files in this diff to the given snapshot.
         * Removes any removed files in this diff from the given snapshot.
         *
         * @param snapshot the snapshot to apply the changes to.
         * @return an updated copy of the provided snapshot
         */
        FileCollectionSnapshot applyTo(FileCollectionSnapshot snapshot);
    }

    public interface Merge {
        void ignore();
    }

    interface ChangeIterator<T> {
        boolean next(ChangeListener<T> listener);
    }
}
