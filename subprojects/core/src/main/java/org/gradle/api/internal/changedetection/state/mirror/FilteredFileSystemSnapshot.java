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

package org.gradle.api.internal.changedetection.state.mirror;

import com.google.common.collect.Iterables;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.AbstractFileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.internal.file.FileType;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.InputStream;

public class FilteredFileSystemSnapshot implements FileSystemSnapshot {

    private final Spec<FileTreeElement> spec;
    private final FileSystemSnapshot delegate;
    private final FileSystem fileSystem;

    public FilteredFileSystemSnapshot(Spec<FileTreeElement> spec, FileSystemSnapshot delegate, FileSystem fileSystem) {
        this.spec = spec;
        this.delegate = delegate;
        this.fileSystem = fileSystem;
    }

    @Override
    public void accept(final PhysicalSnapshotVisitor visitor) {
        delegate.accept(new PhysicalSnapshotVisitor() {
            private final RelativePathSegmentsTracker relativePath = new RelativePathSegmentsTracker();

            @Override
            public boolean preVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
                relativePath.enter(directorySnapshot);
                if (relativePath.isRoot() || spec.isSatisfiedBy(new LogicalFileTreeElement(directorySnapshot, relativePath.getRelativePath(), fileSystem))) {
                    visitor.preVisitDirectory(directorySnapshot);
                    return true;
                }
                relativePath.leave();
                return false;
            }

            @Override
            public void visit(PhysicalSnapshot fileSnapshot) {
                relativePath.enter(fileSnapshot);
                if (spec.isSatisfiedBy(new LogicalFileTreeElement(fileSnapshot, relativePath.getRelativePath(), fileSystem))) {
                    visitor.visit(fileSnapshot);
                }
                relativePath.leave();
            }

            @Override
            public void postVisitDirectory() {
                relativePath.leave();
                visitor.postVisitDirectory();
            }
        });
    }

    /**
     * Adapts a {@link PhysicalSnapshot} to the {@link FileTreeElement} interface, e.g. to allow
     * passing it to a {@link org.gradle.api.tasks.util.PatternSet} for filtering.
     *
     * The fields on this class are prefixed with _ to avoid users from accidentally referencing them
     * in dynamic Groovy code.
     */
    private static class LogicalFileTreeElement extends AbstractFileTreeElement {
        private final Iterable<String> _relativePathIterable;
        private final FileSystem _fileSystem;
        private final PhysicalSnapshot _snapshot;
        private RelativePath _relativePath;
        private File _file;

        public LogicalFileTreeElement(PhysicalSnapshot snapshot, Iterable<String> relativePathIterable, FileSystem fileSystem) {
            super(fileSystem);
            this._snapshot = snapshot;
            this._relativePathIterable = relativePathIterable;
            this._fileSystem = fileSystem;
        }

        @Override
        public String getDisplayName() {
            return "file '" + getFile() + "'";
        }

        @Override
        public File getFile() {
            if (_file == null) {
                _file = new File(_snapshot.getAbsolutePath());
            }
            return _file;
        }

        @Override
        public boolean isDirectory() {
            return _snapshot.getType() == FileType.Directory;
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
            if (_relativePath == null) {
                _relativePath = new RelativePath(!isDirectory(), Iterables.toArray(_relativePathIterable, String.class));
            }
            return _relativePath;
        }

        @Override
        public int getMode() {
            return _fileSystem.getUnixMode(getFile());
        }
    }
}
