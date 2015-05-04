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


import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.MinimalFileSet;

import java.io.File;

public interface FileCollectionInternal extends FileCollection, MinimalFileSet {

    /**
     * Returns the outermost points on the filesystem that encompass (inclusively) all of the file collection's potential contents.
     * <p>
     * The results are guaranteed to be unique.
     * A zero length iterable may be returned if it is not possible for this file collection to ever contain any files.
     * The return value is representative of the file collections state at the time.
     * Mutable file collections may return different values over time.
     * Immutable file collections will always return the same logical value.
     * <p>
     * The term “root” here does not respond to the root of the file system (e.g. "/").
     * <p>
     * This method does not distinguish between files or directories, or non existent files.
     *
     * @return the unique outermost file system roots that encompass this file collection's contents
     */
    Iterable<? extends File> getFileSystemRoots();

    /**
     * Determines whether this collection would contain the given file.
     * <p>
     * The file doesn't have to exist. This is the main difference compared to the {@link FileCollection#contains(File)} method.
     *
     * @param file The file to check for.
     * @return true if this collection would contain the given file, false otherwise.
     */
    boolean wouldContain(File file);
}
