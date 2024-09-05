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

import com.google.common.collect.Iterables;
import org.gradle.api.Describable;
import org.gradle.api.file.FilePermissions;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.DefaultFilePermissions;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.file.FileType;
import org.gradle.internal.file.Stat;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.SnapshottingFilter;
import org.gradle.util.internal.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class PatternSetSnapshottingFilter implements SnapshottingFilter {
    private final PatternSet patternSet;
    private final Stat stat;

    public PatternSetSnapshottingFilter(PatternSet patternSet, Stat stat) {
        this.stat = stat;
        this.patternSet = patternSet;
    }

    @Override
    public boolean isEmpty() {
        return patternSet.isEmpty();
    }

    @Override
    public FileSystemSnapshotPredicate getAsSnapshotPredicate() {
        Spec<FileTreeElement> spec = patternSet.getAsSpec();
        return (snapshot, relativePath) -> spec.isSatisfiedBy(new LogicalFileTreeElement(snapshot, relativePath, stat));
    }

    @Override
    public DirectoryWalkerPredicate getAsDirectoryWalkerPredicate() {
        Spec<FileTreeElement> spec = patternSet.getAsSpec();
        return (Path path, String name, boolean isDirectory, Iterable<String> relativePath) ->
            spec.isSatisfiedBy(new PathBackedFileTreeElement(path, name, isDirectory, relativePath, stat));
    }

    /**
     * Adapts a {@link FileSystemLocationSnapshot} to the {@link FileTreeElement} interface, e.g. to allow
     * passing it to a {@link org.gradle.api.tasks.util.PatternSet} for filtering.
     */
    private static class LogicalFileTreeElement implements FileTreeElement, Describable {
        private final Iterable<String> relativePathIterable;
        private final Stat stat;
        private final FileSystemLocationSnapshot snapshot;
        private RelativePath relativePath;
        private File file;

        public LogicalFileTreeElement(FileSystemLocationSnapshot snapshot, Iterable<String> relativePathIterable, Stat stat) {
            this.snapshot = snapshot;
            this.relativePathIterable = relativePathIterable;
            this.stat = stat;
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
        public void copyTo(OutputStream output) {
            throw new UnsupportedOperationException("Copy to not supported for filters");
        }

        @Override
        public boolean copyTo(File target) {
            throw new UnsupportedOperationException("Copy to not supported for filters");
        }

        @Override
        public String getName() {
            return getRelativePath().getLastName();
        }

        @Override
        public String getPath() {
            return getRelativePath().getPathString();
        }

        @Override
        public RelativePath getRelativePath() {
            if (relativePath == null) {
                relativePath = new RelativePath(!isDirectory(), Iterables.toArray(relativePathIterable, String.class));
            }
            return relativePath;
        }

        @Override
        @Deprecated
        public int getMode() {
            DeprecationLogger.deprecateMethod(FileTreeElement.class, "getMode()")
                .replaceWith("getPermissions()")
                .willBeRemovedInGradle9()
                .withUpgradeGuideSection(8, "unix_file_permissions_deprecated")
                .nagUser();
            return getPermissions().toUnixNumeric();
        }

        @Override
        public FilePermissions getPermissions() {
            int unixNumeric = stat.getUnixMode(getFile());
            return new DefaultFilePermissions(unixNumeric);
        }
    }

    private static class PathBackedFileTreeElement implements FileTreeElement {
        private final Path path;
        private final String name;
        private final boolean isDirectory;
        private final Iterable<String> relativePath;
        private final Stat stat;

        public PathBackedFileTreeElement(Path path, String name, boolean isDirectory, Iterable<String> relativePath, Stat stat) {
            this.path = path;
            this.name = name;
            this.isDirectory = isDirectory;
            this.relativePath = relativePath;
            this.stat = stat;
        }

        @Override
        public File getFile() {
            return path.toFile();
        }

        @Override
        public boolean isDirectory() {
            return isDirectory;
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
            try {
                return Files.newInputStream(path);
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        @Override
        public void copyTo(OutputStream output) {
            throw new UnsupportedOperationException("Copy to not supported for filters");
        }

        @Override
        public boolean copyTo(File target) {
            throw new UnsupportedOperationException("Copy to not supported for filters");
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getPath() {
            return getRelativePath().getPathString();
        }

        @Override
        public RelativePath getRelativePath() {
            return new RelativePath(!isDirectory, Iterables.toArray(relativePath, String.class));
        }

        @Override
        @Deprecated
        public int getMode() {
            DeprecationLogger.deprecateMethod(FileTreeElement.class, "getMode()")
                .replaceWith("getPermissions()")
                .willBeRemovedInGradle9()
                .withUpgradeGuideSection(8, "unix_file_permissions_deprecated")
                .nagUser();
            return getPermissions().toUnixNumeric();
        }

        @Override
        public FilePermissions getPermissions() {
            int unixNumeric = stat.getUnixMode(path.toFile());
            return new DefaultFilePermissions(unixNumeric);
        }
    }
}
