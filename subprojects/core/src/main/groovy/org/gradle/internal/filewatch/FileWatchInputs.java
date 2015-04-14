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

/**
 * Builder type of interface for building the input for {@link FileWatcher}
 *
 */
public interface FileWatchInputs {
    /**
     * Watch changes to a directory filtered by a {@link org.gradle.api.tasks.util.PatternSet}
     *
     * @param directoryTree watch changes to this directory tree
     * @return this
     */
    FileWatchInputs watch(DirectoryTree directoryTree);

    /**
     * Watch changes to an individual file
     *
     * @param file watch changes to this file
     * @return this
     */
    FileWatchInputs watch(File file);

    /**
     * @return all added DirectoryTree watches
     */
    Collection<DirectoryTree> getDirectoryTrees();
    /**
     * @return all added File watches
     */
    Collection<File> getFiles();
}
