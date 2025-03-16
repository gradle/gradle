/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Describable;
import org.gradle.api.Named;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.internal.instrumentation.api.annotations.NotToBeMigratedToLazy;
import org.gradle.model.internal.core.UnmanagedStruct;

import java.io.File;
import java.util.Set;
import java.util.function.Function;

/**
 * A {@code SourceDirectorySet} represents a set of source files composed from a set of source directories, along
 * with associated include and exclude patterns.
 * <p>
 * {@code SourceDirectorySet} extends {@link FileTree}. The contents of the file tree represent the source files of this set,
 * arranged in a hierarchy. The file tree is live and reflects changes to the source directories and their contents.
 * <p>
 * You can create an instance of {@code SourceDirectorySet} using the {@link org.gradle.api.model.ObjectFactory#sourceDirectorySet(String, String)}
 * method.
 * <p>
 * You can filter the <strong>files</strong> that are obtainable in this set using patterns via {@link #include(Spec)}
 * and {@link #include(Spec)} (or any overload of these methods).  The set of included source directories themselves are
 * <strong>not filtered</strong>.
 */
@UnmanagedStruct
@NotToBeMigratedToLazy
public interface SourceDirectorySet extends FileTree, PatternFilterable, Named, Describable {

    /**
     * A concise name for the source directory set (typically used to identify it in a collection).
     */
    @Override
    String getName();

    /**
     * Adds the given source directory to this set. The given directory does not need to exist. Directories that do not exist are ignored.
     *
     * @param srcPath The source directory. This is evaluated as per {@link org.gradle.api.Project#files(Object...)}
     * @return this
     */
    SourceDirectorySet srcDir(Object srcPath);

    /**
     * Adds the given source directories to this set. The given directories do not need to exist. Directories that do not exist are ignored.
     *
     * @param srcPaths The source directories. These are evaluated as per {@link org.gradle.api.Project#files(Object...)}
     * @return this
     */
    SourceDirectorySet srcDirs(Object... srcPaths);

    /**
     * Returns the source directories that make up this set.
     * <p>
     * Note that filtering via patterns using {@link #exclude(Spec)} and {@link #include(Spec)} (or any overload of these methods) only
     * filters <strong>files</strong> within the {@link DirectoryTree}s returned by this method and <strong>does not filter</strong>
     * the set of source directory trees themselves.  This result should agree with {@link #getSrcDirTrees()}.
     * <p>
     * Does not filter source directories that do not exist.
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
     * Returns the source directories that make up this set, represented as a {@link FileCollection}. Does not filter source directories that do not exist.
     * Generally, it is preferable to use this method instead of {@link #getSrcDirs()}, as this method does not require the source directories to be calculated when it is called. Instead, the source directories are calculated when queried. The return value of this method also maintains dependency information.
     *
     * <p>The returned collection is live and reflects changes to this source directory set.
     */
    FileCollection getSourceDirectories();

    /**
     * Returns the source directory trees that make up this set.
     * <p>
     * Note that filtering via patterns using {@link #exclude(Spec)} and {@link #include(Spec)} (or any overload of these methods) only
     * filters <strong>files</strong> within the {@link DirectoryTree}s returned by this method and <strong>does not filter</strong>
     * the set of source directory trees themselves.  This result should agree with {@link #getSrcDirs()}.
     * <p>
     * Does not filter source directories that do not exist.
     * <p>
     * Note that if there are multiple source directories added to this set that include the same dir,
     * we use the patterns from the first one we find (in the order they were added) to filter files in the returned
     * {@link DirectoryTree}s.  It is discouraged to add multiple source directories that include the same dir to the same set.
     *
     * @return The source directory trees. Returns an empty set when this set contains no source directories.
     */
    Set<DirectoryTree> getSrcDirTrees();

    /**
     * Returns the filter used to select the source from the source directories. These filter patterns are applied after
     * the include and exclude patterns of this source directory set. Generally, the filter patterns are used to
     * restrict the contents to certain types of files, eg {@code *.java}.
     *
     * @return The filter patterns.
     */
    PatternFilterable getFilter();

    /**
     * Configure the directory to assemble the compiled classes into.
     *
     * @return The destination directory property for this set of sources.
     * @since 6.1
     */
    DirectoryProperty getDestinationDirectory();

    /**
     * Returns the directory property that is bound to the task that produces the output via {@link #compiledBy(TaskProvider, Function)}.
     * Use this as part of a classpath or input to another task to ensure that the output is created before it is used.
     *
     * Note: To define the path of the output folder use {@link #getDestinationDirectory()}
     *
     * @return The classes directory property for this set of sources.
     * @since 6.1
     */
    Provider<Directory> getClassesDirectory();

    /**
     * Define the task responsible for processing the source.
     *
     * @param taskProvider the task responsible for compiling the sources (.e.g. compileJava)
     * @param mapping a mapping from the task to the task's output directory (e.g. AbstractCompile::getDestinationDirectory)
     * @since 6.1
     */
    <T extends Task> void compiledBy(TaskProvider<T> taskProvider, Function<T, DirectoryProperty> mapping);
}
