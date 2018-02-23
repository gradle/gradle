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

import java.util.Collection;

/**
 * Used to build a {@link FileCollectionSnapshot} by collecting normalized file snapshots.
 */
public class FileCollectionVisitingSnapshotBuilder implements VisitingFileCollectionSnapshotBuilder {
    private final CollectingFileCollectionSnapshotBuilder builder;

    public FileCollectionVisitingSnapshotBuilder(CollectingFileCollectionSnapshotBuilder builder) {
        this.builder = builder;
    }

    @Override
    public void visitFileTreeSnapshot(Collection<FileSnapshot> descendants) {
        for (FileSnapshot fileSnapshot : descendants) {
            builder.collectFileSnapshot(fileSnapshot);
        }
    }

    @Override
    public void visitDirectorySnapshot(DirectoryFileSnapshot directory) {
        builder.collectFileSnapshot(directory);
    }

    @Override
    public void visitFileSnapshot(RegularFileSnapshot file) {
        builder.collectFileSnapshot(file);
    }

    @Override
    public void visitMissingFileSnapshot(MissingFileSnapshot missingFile) {
        builder.collectFileSnapshot(missingFile);
    }

    public FileCollectionSnapshot build() {
        return builder.build();
    }
}
