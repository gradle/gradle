/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.collections.MinimalFileSet;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.Factory;
import org.gradle.internal.file.PathToFileResolver;

import java.io.File;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;

public interface FileCollectionFactory {
    /**
     * Creates a copy of this factory that uses the given resolver to convert various types to File instances.
     */
    FileCollectionFactory withResolver(PathToFileResolver fileResolver);

    /**
     * Creates a {@link FileCollection} with the given contents.
     *
     * The collection is live, so that the contents are queried as required on query of the collection.
     */
    FileCollectionInternal create(MinimalFileSet contents);

    /**
     * Creates a {@link FileCollection} with the given contents, and built by the given tasks.
     *
     * The collection is live, so that the contents are queried as required on query of the collection.
     */
    FileCollectionInternal create(TaskDependency builtBy, MinimalFileSet contents);

    /**
     * Creates an empty {@link FileCollection}
     */
    FileCollectionInternal empty(String displayName);

    /**
     * Creates an empty {@link FileCollection}
     */
    FileCollectionInternal empty();

    /**
     * Creates a {@link FileCollection} with the given files as content.
     *
     * <p>The collection is not live. The provided array is queried on construction and discarded.
     */
    FileCollectionInternal fixed(File... files);

    /**
     * Creates a {@link FileCollection} with the given files as content.
     *
     * <p>The collection is not live. The provided {@link Collection} is queried on construction and discarded.
     */
    FileCollectionInternal fixed(Collection<File> files);

    /**
     * Creates a {@link FileCollection} with the given files as content. The result is not live and does not reflect changes to the array.
     *
     * <p>The collection is not live. The provided array is queried on construction and discarded.
     */
    FileCollectionInternal fixed(String displayName, File... files);

    /**
     * Creates a {@link FileCollection} with the given files as content.
     *
     * <p>The collection is not live. The provided {@link Collection} is queried on construction and discarded.
     */
    FileCollectionInternal fixed(String displayName, Collection<File> files);

    /**
     * Creates a {@link FileCollection} with the given files as content.
     *
     * <p>The collection is live and resolves the files on each query. Tracks changes to the source list.
     */
    FileCollectionInternal resolving(String displayName, List<?> sources);

    /**
     * Creates a {@link FileCollection} with the given files as content.
     *
     * <p>The collection is live and resolves the files on each query.
     */
    FileCollectionInternal resolving(String displayName, Object... sources);

    /**
     * Creates a {@link FileCollection} with the given files as content.
     *
     * <p>The collection is live and resolves the files on each query.
     */
    FileCollectionInternal resolving(Object... sources);

    /**
     * Creates an empty {@link ConfigurableFileCollection} instance.
     */
    ConfigurableFileCollection configurableFiles(String displayName);

    /**
     * Creates an empty {@link ConfigurableFileCollection} instance.
     */
    ConfigurableFileCollection configurableFiles();

    /**
     * Creates a {@link ConfigurableFileTree} instance with no base dir specified.
     */
    ConfigurableFileTree fileTree();

    /**
     * Creates a file tree containing the given generated file.
     */
    FileTreeInternal generated(Factory<File> tmpDir, String fileName, Action<File> fileGenerationListener, Action<OutputStream> contentGenerator);

    /**
     * Creates a file tree made up of the union of the given trees.
     *
     * <p>The tree is not live. The provided list is queried on construction and discarded.
     */
    FileTreeInternal treeOf(List<? extends FileTreeInternal> fileTrees);

    FileTreeInternal treeOf(MinimalFileTree tree);
}
