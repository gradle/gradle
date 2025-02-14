/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.execution;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.io.File;

/**
 * Service for snapshotting {@link FileCollection}s.
 */
@ServiceScope({Scope.UserHome.class, Scope.BuildSession.class})
public interface FileCollectionSnapshotter {

    /**
     * Snapshot the roots of a file collection.
     */
    default FileSystemSnapshot snapshot(FileCollection fileCollection) {
        return snapshot(fileCollection, NO_OP_STRUCTURE_VISITOR);
    }

    /**
     * Snapshot the roots of a file collection and call the given visitor during snapshotting.
     */
    FileSystemSnapshot snapshot(FileCollection fileCollection, FileCollectionStructureVisitor visitor);

    /* private */ FileCollectionStructureVisitor NO_OP_STRUCTURE_VISITOR = new FileCollectionStructureVisitor() {
        @Override
        public void visitCollection(FileCollectionInternal.Source source, Iterable<File> contents) {
        }

        @Override
        public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
        }

        @Override
        public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
        }
    };
}
