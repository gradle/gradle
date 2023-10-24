/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.execution.plan;

import org.gradle.api.file.FilePermissions;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.file.SymbolicLinkDetails;
import org.gradle.api.internal.file.DefaultFilePermissions;
import org.gradle.api.specs.Spec;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.file.Stat;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;

public class SingleFileTreeElementMatcher {

    private final Stat stat;

    public SingleFileTreeElementMatcher(Stat stat) {
        this.stat = stat;
    }

    public boolean elementWithRelativePathMatches(Spec<FileTreeElement> filter, File element, String relativePathString) {
        // A better solution for output files would be to record the type of the output file and then using this type here instead of looking at the disk.
        // Though that is more involved and as soon as the file has been produced, the right file type will be detected here as well.
        boolean elementIsFile = !element.isDirectory();
        RelativePath relativePath = RelativePath.parse(elementIsFile, relativePathString);
        if (!filter.isSatisfiedBy(new ReadOnlyFileTreeElement(element, relativePath, stat))) {
            return false;
        }
        // All parent paths need to match the spec as well, since this is how we implement the file system walking for file tree.
        RelativePath parentRelativePath = relativePath.getParent();
        File parentFile = element.getParentFile();
        while (parentRelativePath != null && parentRelativePath != RelativePath.EMPTY_ROOT) {
            if (!filter.isSatisfiedBy(new ReadOnlyFileTreeElement(parentFile, parentRelativePath, stat))) {
                return false;
            }
            parentRelativePath = parentRelativePath.getParent();
            parentFile = parentFile.getParentFile();
        }
        return true;
    }

    private static class ReadOnlyFileTreeElement implements FileTreeElement {
        private final File file;
        private final RelativePath relativePath;
        private final Stat stat;

        public ReadOnlyFileTreeElement(File file, RelativePath relativePath, Stat stat) {
            this.file = file;
            this.relativePath = relativePath;
            this.stat = stat;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public boolean isDirectory() {
            return !relativePath.isFile();
        }

        @Override
        public long getLastModified() {
            return file.lastModified();
        }

        @Override
        public long getSize() {
            return file.length();
        }

        @Override
        public InputStream open() {
            try {
                return Files.newInputStream(file.toPath());
            } catch (IOException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        @Override
        public void copyTo(OutputStream output) {
            throw new UnsupportedOperationException("Copy to not supported for filters");
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean copyTo(File target) {
            throw new UnsupportedOperationException("Copy to not supported for filters");
        }

        @Override
        public String getName() {
            return file.getName();
        }

        @Override
        public String getPath() {
            return relativePath.getPathString();
        }

        @Override
        public RelativePath getRelativePath() {
            return relativePath;
        }

        @Override
        public int getMode() {
            return getPermissions().toUnixNumeric();
        }

        @Override
        public FilePermissions getPermissions() {
            int unixNumeric = stat.getUnixMode(file);
            return new DefaultFilePermissions(unixNumeric);
        }

        @Nullable
        @Override
        // NOTE: usually only the path is considered in FileTree specs, so it's ok to omit link details here.
        public SymbolicLinkDetails getSymbolicLinkDetails() {
            return null;
        }
    }
}
