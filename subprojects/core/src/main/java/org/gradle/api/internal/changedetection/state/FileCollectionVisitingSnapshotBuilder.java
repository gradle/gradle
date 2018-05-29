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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalFileTreeVisitor;
import org.gradle.api.internal.changedetection.state.mirror.VisitableDirectoryTree;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Used to build a {@link FileCollectionSnapshot} by collecting normalized file snapshots.
 */
@SuppressWarnings("Since15")
@NonNullApi
public class FileCollectionVisitingSnapshotBuilder implements VisitingFileCollectionSnapshotBuilder {
    private final CollectingFileCollectionSnapshotBuilder builder;

    public FileCollectionVisitingSnapshotBuilder(CollectingFileCollectionSnapshotBuilder builder) {
        this.builder = builder;
    }

    @Override
    public void visitFileTreeSnapshot(VisitableDirectoryTree tree) {
        tree.visit(new PhysicalFileTreeVisitor() {
            @Override
            public void visit(Path path, String basePath, String name, Iterable<String> relativePath, FileContentSnapshot content) {
                builder.collectFile(path, relativePath, content);
            }
        });
    }

    @Override
    public void visitDirectorySnapshot(DirectoryFileSnapshot directory) {
        builder.collectRootFile(Paths.get(directory.getPath()), directory.getName(), directory.getContent());
    }

    @Override
    public void visitFileSnapshot(RegularFileSnapshot file) {
        builder.collectRootFile(Paths.get(file.getPath()), file.getName(), file.getContent());
    }

    @Override
    public void visitMissingFileSnapshot(MissingFileSnapshot missingFile) {
        builder.collectRootFile(Paths.get(missingFile.getPath()), missingFile.getName(), missingFile.getContent());
    }

    public FileCollectionSnapshot build() {
        return builder.build();
    }
}
