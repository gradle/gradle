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
package org.gradle.api.file;

import groovy.lang.Closure;
import org.gradle.api.Buildable;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.AntBuilderAware;
import org.gradle.api.tasks.StopExecutionException;

import java.io.File;
import java.util.Set;

/**
 * <p>A {@code FileCollection} represents a collection of files which you can query in certain ways. A file collection
 * is often used to define a classpath, or to add files to a container.</p>
 *
 * <p>You can obtain a {@code FileCollection} instance using {@link org.gradle.api.Project#files}.</p>
 */
public interface FileCollection extends Iterable<File>, AntBuilderAware, Buildable {
    /**
     * Returns the content of this collection, asserting it contains exactly one file.
     *
     * @return The file.
     * @throws IllegalStateException when this collection does not contain exactly one file.
     */
    File getSingleFile() throws IllegalStateException;

    /**
     * Returns the contents of this collection as a Set.
     *
     * @return The files. Returns an empty set if this collection is empty.
     */
    Set<File> getFiles();

    /**
     * Determines whether this collection contains the given file. Generally, this method is more efficient than calling
     * {@code getFiles().contains(file)}.
     *
     * @param file The file to check for.
     * @return true if this collection contains the given file, false otherwise.
     */
    boolean contains(File file);

    /**
     * Returns the contents of this collection as a platform-specific path. This can be used, for example, in an Ant
     * <path> element.
     *
     * @return The path. Returns an empty string if this collection is empty.
     */
    String getAsPath();

    /**
     * <p>Returns a {@code FileCollection} which contains the union of this collection and the given collection. The
     * returned collection is live, and tracks changes to both source collections.</p>
     *
     * <p>You can call this method in your build script using the {@code +} operator.</p>
     *
     * @param collection The other collection. Should not be null.
     * @return A new collection containing the union.
     */
    FileCollection plus(FileCollection collection);

    /**
     * <p>Returns a {@code FileCollection} which contains the intersection of this collection and the given collection.
     * The returned collection is live, and tracks changes to both source collections.</p>
     *
     * <p>You can call this method in your build script using the {@code -} operator.</p>
     *
     * @param collection The other collection. Should not be null.
     * @return A new collection containing the intersection.
     */
    FileCollection minus(FileCollection collection);

    /**
     * <p>Restricts the contents of this collection to those files which match the given criteria. The filtered
     * collection is live, so that it reflects any changes to this collection.</p>
     *
     * <p>The given closure is passed the File as a parameter, and should return a boolean value.</p>
     *
     * @param filterClosure The closure to use to select the contents of the filtered collection.
     * @return The filtered collection.
     */
    FileCollection filter(Closure filterClosure);

    /**
     * <p>Restricts the contents of this collection to those files which match the given criteria. The filtered
     * collection is live, so that it reflects any changes to this collection.</p>
     *
     * @param filterSpec The criteria to use to select the contents of the filtered collection.
     * @return The filtered collection.
     */
    FileCollection filter(Spec<? super File> filterSpec);

    /**
     * <p>Converts this collection into an object of the specified type. Supported types are: {@code Collection}, {@code
     * List}, {@code Set}, {@code Object[]}, {@code File[]}, {@code File}, and {@link FileTree}.</p>
     *
     * <p>You can call this method in your build script using the {@code as} operator.</p>
     *
     * @param type The type to convert to.
     * @return The converted value.
     * @throws UnsupportedOperationException When an unsupported type is specified.
     */
    Object asType(Class<?> type) throws UnsupportedOperationException;

    /**
     * <p>Adds another collection to this collection. This is an optional operation.</p>
     *
     * @param collection The collection to add.
     * @return This
     * @throws UnsupportedOperationException When this collection does not allow modification.
     */
    FileCollection add(FileCollection collection) throws UnsupportedOperationException;

    /**
     * Returns true if this collection is empty. Generally, calling this method is more efficient than calling {@code
     * getFiles().isEmpty()}.
     *
     * @return true if this collection is empty, false otherwise.
     */
    boolean isEmpty();

    /**
     * Throws a {@link StopExecutionException} if this collection is empty.
     *
     * @return this
     * @throws StopExecutionException When this collection is empty.
     */
    FileCollection stopExecutionIfEmpty() throws StopExecutionException;

    /**
     * Converts this collection to a {@link FileTree}. Generally, for each file in this collection, the resulting file
     * tree will contain the source file at the root of the tree. For each directory in this collection, the resulting
     * file tree will contain all the files under the source directory.
     *
     * @return this collection as a {@link FileTree}. Never returns null.
     */
    FileTree getAsFileTree();

    /**
     * Ant types which a {@code FileCollection} can be mapped to.
     */
    enum AntType {
        MatchingTask, FileSet, ResourceCollection
    }

    /**
     * Adds this collection to an Ant task as a nested node. The given type determines how this collection is added:
     *
     * <ul>
     *
     * <li>{@link AntType#MatchingTask}: adds this collection to an Ant MatchingTask. The collection is converted to a
     * set of source directories and include and exclude patterns. The source directories as added as an Ant Path with
     * the given node name. The patterns are added using 'include' and 'exclude' nodes.</li>
     *
     * <li>{@link AntType#FileSet}: adds this collection as zero or more Ant FileSets with the given node name.</li>
     *
     * <li>{@link AntType#ResourceCollection}: adds this collection as zero or more Ant ResourceCollections with the
     * given node name.</li>
     *
     * </ul>
     *
     * You should prefer using {@link AntType#ResourceCollection}, if the target Ant task supports it, as this is
     * generally the most efficient. Using the other types may involve copying the contents of this collection to a
     * temporary directory.
     *
     * @param builder The builder to add this collection to.
     * @param nodeName The target node name.
     * @param type The target Ant type
     */
    void addToAntBuilder(Object builder, String nodeName, AntType type);

    /**
     * Adds this collection to an Ant task as a nested node. Equivalent to calling {@code addToAntBuilder(builder,
     *nodeName,AntType.ResourceCollection)}.
     */
    Object addToAntBuilder(Object builder, String nodeName);
}
