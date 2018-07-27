/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.classpath;

import org.gradle.cache.internal.Cache;
import org.gradle.internal.hash.HashCode;

import java.io.File;
import java.util.Map;

public interface ClasspathEntrySnapshotCache extends Cache<File, ClasspathEntrySnapshot> {
    /**
     * Returns the classpath entry snapshots for the given files. The resulting map has the same order as the input.
     * Fails if no cached data could be found for any of the given files.
     */
    Map<File, ClasspathEntrySnapshot> getClasspathEntrySnapshots(Map<File, HashCode> fileHashes);
}
