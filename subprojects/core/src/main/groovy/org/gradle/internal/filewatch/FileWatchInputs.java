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
 * Builder type of interface for building the input for {@link FileWatcher}
 *
 */
public class FileWatchInputs {
    private Set<DirectoryTree> directories = new LinkedHashSet<DirectoryTree>();
    private Set<File> files = new LinkedHashSet<File>();

    /**
     * Watch changes to a directory filtered by a {@link org.gradle.api.tasks.util.PatternSet}
     *
     * @param directoryTree watch changes to this directory tree
     * @return this
     */
    public FileWatchInputs watch(DirectoryTree directoryTree) {
        directories.add(directoryTree);
        return this;
    }

    /**
     * Watch changes to an individual file
     *
     * @param file watch changes to this file
     * @return this
     */
    public FileWatchInputs watch(File file) {
        files.add(file.getAbsoluteFile());
        return this;
    }

    /**
     * @return all added DirectoryTree watches
     */
    public Collection<DirectoryTree> getDirectoryTrees()  {
        return directories;
    }

    /**
     * @return all added File watches
     */
    public Collection<File> getFiles() { return files; }
}
