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
import com.google.common.collect.Lists;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.changedetection.state.DirContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.file.AbstractFileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.internal.file.FileType;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Deque;

public class FilteredHierarchicalVisitableTree implements HierarchicalVisitableTree {

    private final Spec<FileTreeElement> spec;
    private final HierarchicalVisitableTree delegate;
    private final FileSystem fileSystem;

    public FilteredHierarchicalVisitableTree(Spec<FileTreeElement> spec, HierarchicalVisitableTree delegate, FileSystem fileSystem) {
        this.spec = spec;
        this.delegate = delegate;
        this.fileSystem = fileSystem;
    }

    @Override
    public void accept(final HierarchicalFileTreeVisitor visitor) {
        delegate.accept(new HierarchicalFileTreeVisitor() {
            private Deque<String> relativePath = Lists.newLinkedList();
            private boolean seenRoot;

            @Override
            public boolean preVisitDirectory(Path path, String name) {
                if (!seenRoot) {
                    seenRoot = true;
                } else {
                    relativePath.addLast(name);
                }
                if (relativePath.isEmpty() || spec.isSatisfiedBy(new LogicalFileTreeElement(path, relativePath, DirContentSnapshot.INSTANCE, fileSystem))) {
                    visitor.preVisitDirectory(path, name);
                    return true;
                }
                relativePath.removeLast();
                return false;
            }

            @Override
            public void visit(Path path, String name, FileContentSnapshot content) {
                relativePath.addLast(name);
                if (spec.isSatisfiedBy(new LogicalFileTreeElement(path, relativePath, content, fileSystem))) {
                    visitor.visit(path, name, content);
                }
                relativePath.removeLast();
            }

            @Override
            public void postVisitDirectory() {
                if (relativePath.isEmpty()) {
                    seenRoot = false;
                } else {
                    relativePath.removeLast();
                }
                visitor.postVisitDirectory();
            }
        });
    }

    /**
     * Adapts a logical file snapshot to the {@link FileTreeElement} interface, e.g. to allow
     * passing it to a {@link org.gradle.api.tasks.util.PatternSet} for filtering.
     *
     * The fields on this class are prefixed with _ to avoid users from accidentally referencing them
     * in dynamic Groovy code.
     */
    private static class LogicalFileTreeElement extends AbstractFileTreeElement {
        private final Path _path;
        private final Iterable<String> _relativePathIterable;
        private final FileContentSnapshot _content;
        private final FileSystem _fileSystem;
        private RelativePath _relativePath;
        private File _file;

        public LogicalFileTreeElement(Path path, Iterable<String> relativePathIterable, FileContentSnapshot content, FileSystem fileSystem) {
            super(fileSystem);
            this._path = path;
            this._relativePathIterable = relativePathIterable;
            this._content = content;
            this._fileSystem = fileSystem;
        }

        @Override
        public String getDisplayName() {
            return "file '" + getFile() + "'";
        }

        @Override
        public File getFile() {
            if (_file == null) {
                _file = _path.toFile();
            }
            return _file;
        }

        @Override
        public boolean isDirectory() {
            return _content.getType() == FileType.Directory;
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
