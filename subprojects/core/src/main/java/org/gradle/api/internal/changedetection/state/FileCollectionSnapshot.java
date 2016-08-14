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
import org.gradle.api.internal.tasks.cache.TaskCacheKeyBuilder;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * An immutable snapshot of the contents and meta-data of a collection of files or directories.
 */
public interface FileCollectionSnapshot {
    boolean isEmpty();

    /**
     * Returns an iterator over the changes to file contents since the given snapshot, subject to the given filters.
     */
    Iterator<TaskStateChange> iterateContentChangesSince(FileCollectionSnapshot oldSnapshot, String title);

    Collection<File> getFiles();

    Map<String, IncrementalFileSnapshot> getSnapshots();

    void appendToCacheKey(TaskCacheKeyBuilder builder);
}
