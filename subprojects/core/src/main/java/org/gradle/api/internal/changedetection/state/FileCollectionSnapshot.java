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

import org.gradle.api.internal.changedetection.rules.TaskStateChange;
import org.gradle.caching.internal.BuildCacheKeyBuilder;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.UNORDERED;

/**
 * An immutable snapshot of the contents and meta-data of a collection of files or directories.
 */
public interface FileCollectionSnapshot {
    FileCollectionSnapshot EMPTY = new DefaultFileCollectionSnapshot(Collections.<String, NormalizedFileSnapshot>emptyMap(), UNORDERED, true);

    boolean isEmpty();

    /**
     * Returns an iterator over the changes to file contents since the given snapshot, subject to the given filters.
     */
    Iterator<TaskStateChange> iterateContentChangesSince(FileCollectionSnapshot oldSnapshot, String title);

    /**
     * Returns the elements of this snapshot, including regular files, directories and missing files
     */
    Collection<File> getElements();

    /**
     * Returns the regular files that make up this snapshot.
     */
    Collection<File> getFiles();

    Map<String, NormalizedFileSnapshot> getSnapshots();

    void appendToCacheKey(BuildCacheKeyBuilder builder);
}
