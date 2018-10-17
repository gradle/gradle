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

import com.google.common.io.Files;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.AbstractFileTreeElement;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.internal.Factory;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.internal.nativeintegration.filesystem.Chmod;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.services.FileSystems;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A {@link SingletonFileTree} which is composed using a mapping from relative path to file source.
 */
public class GeneratedSingletonFileTree implements SingletonFileTree, FileSystemMirroringFileTree {
    private final Factory<File> tmpDirSource;
    private final FileSystem fileSystem = FileSystems.getDefault();
    private final DirectoryFileTreeFactory directoryFileTreeFactory;

    private final RelativePath relativePath;
    private final Action<OutputStream> contentWriter;

    public GeneratedSingletonFileTree(Factory<File> tmpDirSource, DirectoryFileTreeFactory directoryFileTreeFactory, RelativePath relativePath, Action<OutputStream> contentWriter) {
        this.tmpDirSource = tmpDirSource;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.relativePath = relativePath;
        this.contentWriter = contentWriter;
    }

    private File getTmpDir() {
        return tmpDirSource.create();
    }

    public String getDisplayName() {
        return "file tree";
    }

    public DirectoryFileTree getMirror() {
        return directoryFileTreeFactory.create(getTmpDir());
    }

    public void visit(FileVisitor visitor) {
        Visit visit = new Visit(visitor);
        visit.visit(relativePath, contentWriter);
    }

    public File getFileWithoutCreating() {
        return createFileInstance(relativePath);
    }

    @Override
    public File getFile() {
        File file = createFileInstance(relativePath);
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            try {
                contentWriter.execute(fileOutputStream);
            } finally {
                fileOutputStream.close();
            }
            return file;
        } catch (Exception e) {
            throw new GradleException(String.format("Cannot create file %s", file), e);
        }
    }

    private File createFileInstance(RelativePath path) {
        return path.getFile(getTmpDir());
    }

    private class Visit {
        private final Set<RelativePath> visitedDirs = new LinkedHashSet<RelativePath>();
        private final FileVisitor visitor;

        public Visit(FileVisitor visitor) {
            this.visitor = visitor;
        }

        private void visitDirs(RelativePath path, FileVisitor visitor) {
            if (path == null || path.getParent() == null || !visitedDirs.add(path)) {
                return;
            }

            visitDirs(path.getParent(), visitor);
            visitor.visitDir(new FileVisitDetailsImpl(path, null, fileSystem));
        }

        public void visit(RelativePath path, Action<OutputStream> generator) {
            visitDirs(path.getParent(), visitor);
            visitor.visitFile(new FileVisitDetailsImpl(path, generator, fileSystem));
        }
    }

    private class FileVisitDetailsImpl extends AbstractFileTreeElement implements FileVisitDetails {
        private final RelativePath path;
        private final Action<OutputStream> generator;
        private long lastModified;
        private long size;
        private File file;
        private final boolean isDirectory;

        public FileVisitDetailsImpl(RelativePath path, Action<OutputStream> generator, Chmod chmod) {
            super(chmod);
            this.path = path;
            this.generator = generator;
            this.isDirectory = !path.isFile();
        }

        public String getDisplayName() {
            return path.toString();
        }

        public void stopVisiting() {
            // only one file
        }

        public File getFile() {
            if (file == null) {
                file = createFileInstance(path);
                if (!file.exists()) {
                    copyTo(file);
                } else if (!isDirectory()) {
                    updateFileOnlyWhenGeneratedContentChanges();
                }
                // round to nearest second
                lastModified = file.lastModified() / 1000 * 1000;
                size = file.length();
            }
            return file;
        }

        public void copyTo(OutputStream output) {
            generator.execute(output);
        }

        // prevent file system change events when generated content
        // remains the same as the content in the existing file
        private void updateFileOnlyWhenGeneratedContentChanges() {
            byte[] generatedContent = generateContent();
            if (!hasContent(generatedContent, file)) {
                try {
                    Files.write(generatedContent, file);
                } catch (IOException e) {
                    throw new org.gradle.api.UncheckedIOException(e);
                }
            }
        }

        private byte[] generateContent() {
            StreamByteBuffer buffer = new StreamByteBuffer();
            copyTo(buffer.getOutputStream());
            return buffer.readAsByteArray();
        }

        private boolean hasContent(byte[] generatedContent, File file) {
            if (generatedContent.length != file.length()) {
                return false;
            }

            byte[] existingContent;
            try {
                existingContent = Files.toByteArray(this.file);
            } catch (IOException e) {
                // Assume changed if reading old file fails
                return false;
            }

            return Arrays.equals(generatedContent, existingContent);
        }

        public boolean isDirectory() {
            return isDirectory;
        }

        public long getLastModified() {
            getFile();
            return lastModified;
        }

        public long getSize() {
            getFile();
            return size;
        }

        public InputStream open() {
            throw new UnsupportedOperationException();
        }

        public RelativePath getRelativePath() {
            return path;
        }
    }

    @Override
    public void registerWatchPoints(FileSystemSubset.Builder builder) {

    }

    @Override
    public void visitTreeOrBackingFile(FileVisitor visitor) {
        visit(visitor);
    }
}
