/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs.impl;

import org.gradle.internal.snapshot.FileSystemLocationSnapshot;

import javax.annotation.Nullable;
import java.io.File;

/**
 * An immutable set of directory trees. Intended to be use to efficiently determine whether a particular file is contained in a set of directories or not.
 */
public interface FileHierarchySet {
    @Nullable
    FileSystemLocationSnapshot getSnapshot(String path);
    @Nullable
    default FileSystemLocationSnapshot getSnapshot(File file) {
        return getSnapshot(file.getAbsolutePath());
    }

    /**
     * Returns a set that contains the union of this set and the given directory. The set contains the directory itself, plus all its descendants.
     */
    FileHierarchySet plus(FileSystemLocationSnapshot snapshot);
}
