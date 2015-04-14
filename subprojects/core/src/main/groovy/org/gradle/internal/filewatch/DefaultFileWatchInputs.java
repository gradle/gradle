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

package org.gradle.internal.filewatch;

import org.gradle.api.file.DirectoryTree;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Implementation of {@link FileWatchInputs}
 */
public class DefaultFileWatchInputs implements FileWatchInputs {
    Set<DirectoryTree> directories = new LinkedHashSet<DirectoryTree>();
    Set<File> files = new LinkedHashSet<File>();

    @Override
    public FileWatchInputs watch(DirectoryTree directoryTree) {
        directories.add(directoryTree);
        return this;
    }

    @Override
    public FileWatchInputs watch(File file) {
        files.add(file.getAbsoluteFile());
        return this;
    }

    @Override
    public Collection<DirectoryTree> getDirectoryTrees() {
        return directories;
    }

    @Override
    public Collection<File> getFiles() {
        return files;
    }
}
