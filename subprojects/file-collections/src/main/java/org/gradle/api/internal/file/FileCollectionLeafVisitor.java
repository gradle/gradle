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

import org.gradle.api.tasks.util.PatternSet;

import java.io.File;

/**
 * Used with {@link FileCollectionInternal#visitLeafCollections(FileCollectionLeafVisitor)} this visitor
 * is called for each element in a file collection that represents a root of a file tree.
 */
public interface FileCollectionLeafVisitor {
    enum CollectionType {
        ArtifactTransformResult, Other
    }

    /**
     * Called prior to visiting a file collection of the given type, and allows this visitor to skip the collection.
     *
     * This is only intended to be step towards some fine-grained visiting of the contents of a `Configuration` and other collections that may
     * contain files that are expensive to visit, or task/transform outputs that don't yet exist.
     *
     * @return true to indicate that the collection should be visited, false to indicate it should be skipped.
     */
    default boolean beforeVisit(CollectionType type) {
        return true;
    }

    /**
     * Visits a {@link FileCollectionInternal} element that cannot be visited in further detail.
     */
    void visitCollection(FileCollectionInternal fileCollection);

    /**
     * Visits a {@link FileTreeInternal} that does not represents a directory in the file system.
     */
    void visitGenericFileTree(FileTreeInternal fileTree);

    /**
     * Visits a file tree at a root file on the file system (potentially filtered).
     */
    void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree);

    /**
     * Visits a file tree whose content is backed by the contents of a file.
     */
    void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree);
}
