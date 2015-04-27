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

package org.gradle.api.internal.file;

import org.gradle.api.file.DirectoryTree;

public interface BackedByDirectoryTrees {
    /**
     * returns true if getAsDirectoryTrees can be called to extract directory trees
     * @return true if this instance is backed by directory trees
     */
    boolean hasDirectoryTrees();

    /**
     * Converts this collection to a collection of {@link DirectoryTree} instances.
     *
     * The DirectoryTree instance will implement {@link org.gradle.api.file.FileBackedDirectoryTree} when it's backed by files that don't change (a single file).
     *
     * @return this collection as a collection of {@link DirectoryTree}s. Never returns null.
     */
    Iterable<DirectoryTree> getAsDirectoryTrees();
}
