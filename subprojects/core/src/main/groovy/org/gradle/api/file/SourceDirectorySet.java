/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.file;

import org.gradle.api.Named;
import org.gradle.api.tasks.util.PatternFilterable;

import java.io.File;
import java.util.Set;

/**
 * <p>A {@code SourceDirectorySet} represents a set of source files composed from a set of source directories, along
 * with associated include and exclude patterns.</p>
 *
 * TODO - configure includes/excludes for individual source dirs, and sync up with CopySpec
 * TODO - allow add FileTree
 */
public interface SourceDirectorySet extends FileTree, PatternFilterable, Named {

    /**
     * A concise name for the source directory set (typically used to identify it in a collection).
     */
    String getName();

    /**
     * Adds the given source directory to this set.
     *
     * @param srcPath The source directory. This is evaluated as per {@link org.gradle.api.Project#file(Object)}
     * @return this
     */
    SourceDirectorySet srcDir(Object srcPath);

    /**
     * Adds the given source directories to this set.
     *
     * @param srcPaths The source directories. These are evaluated as per {@link org.gradle.api.Project#files(Object...)}
     * @return this
     */
    SourceDirectorySet srcDirs(Object... srcPaths);

    /**
     * Returns the source directories which make up this set. Does not filter source directories which do not exist.
     *
     * @return The source directories. Returns an empty set when this set contains no source directories.
     */
    Set<File> getSrcDirs();

    /**
     * Sets the source directories for this set.
     *
     * @param srcPaths The source directories. These are evaluated as per {@link org.gradle.api.Project#files(Object...)}
     * @return this
     */
    SourceDirectorySet setSrcDirs(Iterable<?> srcPaths);

    /**
     * Adds the given source to this set.
     *
     * @param source The source to add.
     * @return this
     */
    SourceDirectorySet source(SourceDirectorySet source);

    /**
     * Returns the source directory trees which make up this set. Does not filter source directories which do not exist.
     *
     * @return The source directory trees. Returns an empty set when this set contains no source directories.
     */
    Set<DirectoryTree> getSrcDirTrees();

    /**
     * Returns the filter used to select the source from the source directories. These filter patterns are applied after
     * the include and exclude patterns of the source directory set itself. Generally, the filter patterns are used to
     * select certain types of files, eg {@code *.java}.
     *
     * @return The filter patterns.
     */
    PatternFilterable getFilter();
}
