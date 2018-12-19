/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.snapshot.impl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.AbstractFileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.internal.MutableBoolean;
import org.gradle.internal.file.FileType;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.MerkleDirectorySnapshotBuilder;
import org.gradle.internal.snapshot.RelativePathSegmentsTracker;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.InputStream;

public class FileSystemSnapshotFilter {

    private FileSystemSnapshotFilter() {
    }

    public static FileSystemSnapshot filterSnapshot(final Spec<FileTreeElement> spec, FileSystemSnapshot unfiltered, final FileSystem fileSystem) {
        final MerkleDirectorySnapshotBuilder builder = MerkleDirectorySnapshotBuilder.noSortingRequired();
        final MutableBoolean hasBeenFiltered = new MutableBoolean(false);
        unfiltered.accept(new FileSystemSnapshotVisitor() {
            private final RelativePathSegmentsTracker relativePathTracker = new RelativePathSegmentsTracker();

            @Override
            public boolean preVisitDirectory(DirectorySnapshot directorySnapshot) {
                boolean root = relativePathTracker.isRoot();
                relativePathTracker.enter(directorySnapshot);
                if (root || spec.isSatisfiedBy(new LogicalFileTreeElement(directorySnapshot, relativePathTracker.getRelativePath(), fileSystem))) {
                    builder.preVisitDirectory(directorySnapshot);
                    return true;
                } else {
                    hasBeenFiltered.set(true);
                }
                relativePathTracker.leave();
                return false;
            }

            @Override
            public void visit(FileSystemLocationSnapshot fileSnapshot) {
                boolean root = relativePathTracker.isRoot();
                relativePathTracker.enter(fileSnapshot);
                Iterable<String> relativePathForFiltering = root ? ImmutableList.of(fileSnapshot.getName()) : relativePathTracker.getRelativePath();
                if (spec.isSatisfiedBy(new LogicalFileTreeElement(fileSnapshot, relativePathForFiltering, fileSystem))) {
                    builder.visit(fileSnapshot);
                } else {
                    hasBeenFiltered.set(true);
                }
                relativePathTracker.leave();
            }

            @Override
            public void postVisitDirectory(DirectorySnapshot directorySnapshot) {
                relativePathTracker.leave();
                builder.postVisitDirectory();
            }
        });
        if (builder.getResult() == null) {
            return FileSystemSnapshot.EMPTY;
        }
        return hasBeenFiltered.get() ? builder.getResult() : unfiltered;
    }

    /**
     * Adapts a {@link FileSystemLocationSnapshot} to the {@link FileTreeElement} interface, e.g. to allow
     * passing it to a {@link org.gradle.api.tasks.util.PatternSet} for filtering.
     */
    private static class LogicalFileTreeElement extends AbstractFileTreeElement {
        private final Iterable<String> relativePathIterable;
        private final FileSystem fileSystem;
        private final FileSystemLocationSnapshot snapshot;
        private RelativePath relativePath;
        private File file;

        public LogicalFileTreeElement(FileSystemLocationSnapshot snapshot, Iterable<String> relativePathIterable, FileSystem fileSystem) {
            super(fileSystem);
            this.snapshot = snapshot;
            this.relativePathIterable = relativePathIterable;
            this.fileSystem = fileSystem;
        }

        @Override
        public String getDisplayName() {
            return "file '" + getFile() + "'";
        }

        @Override
        public File getFile() {
            if (file == null) {
                file = new File(snapshot.getAbsolutePath());
            }
            return file;
        }

        @Override
        public boolean isDirectory() {
            return snapshot.getType() == FileType.Directory;
        }

        @Override
        public long getLastModified() {
            return getFile().lastModified();
        }

        @Override
        public long getSize() {
            return getFile().length();
        }

        @Override
        public InputStream open() {
            return GFileUtils.openInputStream(getFile());
        }

        @Override
        public RelativePath getRelativePath() {
            if (relativePath == null) {
                relativePath = new RelativePath(!isDirectory(), Iterables.toArray(relativePathIterable, String.class));
            }
            return relativePath;
        }

        @Override
        public int getMode() {
            return fileSystem.getUnixMode(getFile());
        }
    }
}
