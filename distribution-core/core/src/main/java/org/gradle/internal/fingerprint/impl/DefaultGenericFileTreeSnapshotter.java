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

package org.gradle.internal.fingerprint.impl;

import com.google.common.collect.Interner;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.internal.file.FileMetadata.AccessType;
import org.gradle.internal.file.impl.DefaultFileMetadata;
import org.gradle.internal.fingerprint.GenericFileTreeSnapshotter;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.nio.file.Files;
import java.nio.file.Paths;

public class DefaultGenericFileTreeSnapshotter implements GenericFileTreeSnapshotter {

    private final FileHasher hasher;
    private final Interner<String> stringInterner;

    public DefaultGenericFileTreeSnapshotter(FileHasher hasher, Interner<String> stringInterner) {
        this.hasher = hasher;
        this.stringInterner = stringInterner;
    }

    @Override
    public FileSystemSnapshot snapshotFileTree(FileTreeInternal tree) {
        FileSystemSnapshotBuilder builder = new FileSystemSnapshotBuilder(stringInterner, hasher);
        tree.visit(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                builder.addDir(
                    dirDetails.getFile(),
                    dirDetails.getRelativePath().getSegments()
                );
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                builder.addFile(
                    fileDetails.getFile(),
                    fileDetails.getRelativePath().getSegments(),
                    fileDetails.getName(),
                    DefaultFileMetadata.file(
                        fileDetails.getLastModified(),
                        fileDetails.getSize(),
                        AccessType.viaSymlink(Files.isSymbolicLink(Paths.get(fileDetails.getPath())))
                    )
                );
            }
        });
        return builder.build();
    }
}
