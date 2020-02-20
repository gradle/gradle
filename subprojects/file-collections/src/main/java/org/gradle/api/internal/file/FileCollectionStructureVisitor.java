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

import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;

/**
 * Used with {@link FileCollectionInternal#visitStructure(FileCollectionStructureVisitor)} this visitor
 * is called for each element in a file collection that is an atomic source of files.
 */
public interface FileCollectionStructureVisitor {
    enum VisitType {
        // Visitor is interested in the contents of the collection
        Visit,
        // Visitor is not interested in the contents of the collection, but would like to receive the source and other metadata
        NoContents,
        // Visitor is interested in the spec of the collection - that is, the files that the collection <em>might</em> include in the future
        // For most collections, this will be the same as the elements of the collection. However, for a collection that includes
        // all of the files from a directory, the spec for the collection would be the directory + the patterns it matches files with
        // Or, for a collection that contains some transformation of another collection, the spec for the collection would include the spec
        // for the original collection
        Spec
    }

    /**
     * Called prior to visiting a file collection with the given spec, and allows this visitor to skip the collection.
     *
     * <p>Note that this method is not necessarily called immediately before one of the visit methods, as some collections may be
     * resolved in parallel. However, all visiting is performed sequentially and in order.
     * This method is also called sequentially and in order.
     *
     * @return how should the collection be visited?
     */
    default VisitType prepareForVisit(FileCollectionInternal.Source source) {
        return VisitType.Visit;
    }

    /**
     * Visits an opaque file collection element that cannot be visited in further detail.
     */
    void visitCollection(FileCollectionInternal.Source source, Iterable<File> contents);

    /**
     * Visits a file tree whose content is generated from some opaque source.
     */
    void visitGenericFileTree(FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree);

    /**
     * Visits a file tree at a root file on the file system (potentially filtered).
     */
    void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree);

    /**
     * Visits a file tree whose content is generated from the contents of a file.
     */
    void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree);
}
