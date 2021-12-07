/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.file.collections;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;

/**
 * A minimal file tree implementation. An implementation can optionally also implement the following interfaces:
 *
 * <ul>
 *  <li>{@link FileSystemMirroringFileTree}</li>
 *  <li>{@link LocalFileTree}</li>
 *  <li>{@link PatternFilterableFileTree}</li>
 * </ul>
 */
public interface MinimalFileTree extends MinimalFileCollection {
    /**
     * Visits the elements of this tree, in depth-first prefix order.
     */
    void visit(FileVisitor visitor);

    void visitStructure(MinimalFileTreeStructureVisitor visitor, FileTreeInternal owner);

    interface MinimalFileTreeStructureVisitor {

        /**
         * Called prior to visiting a file source with the given spec, and allows this visitor to skip these files.
         * A "file source" is some opaque source of files that is not a full {@link FileCollection}.
         *
         * <p>Note that this method is not necessarily called immediately before one of the visit methods, as some collections may be
         * resolved in parallel. However, all visiting is performed sequentially and in order.
         * This method is also called sequentially and in order.
         *
         * @return how should the collection be visited?
         */
        default FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
            return FileCollectionStructureVisitor.VisitType.Visit;
        }

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
}
