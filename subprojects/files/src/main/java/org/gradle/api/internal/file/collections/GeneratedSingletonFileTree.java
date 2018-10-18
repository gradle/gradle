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

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.AbstractFileTreeElement;
import org.gradle.api.internal.file.DefaultFileVisitDetails;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.internal.Factory;
import org.gradle.internal.nativeintegration.filesystem.Chmod;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.services.FileSystems;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A {@link SingletonFileTree} which is composed using a mapping from relative path to file source.
 */
public class GeneratedSingletonFileTree implements SingletonFileTree, FileSystemMirroringFileTree {
    private final Factory<File> tmpDirSource;
    private final FileSystem fileSystem = FileSystems.getDefault();
    private final DirectoryFileTreeFactory directoryFileTreeFactory;

    private final String fileName;
    private final Action<OutputStream> contentWriter;

    public GeneratedSingletonFileTree(Factory<File> tmpDirSource, DirectoryFileTreeFactory directoryFileTreeFactory, String fileName, Action<OutputStream> contentWriter) {
        this.tmpDirSource = tmpDirSource;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.fileName = fileName;
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
        visitor.visitFile(new FileVisitDetailsImpl(fileName, contentWriter, fileSystem));
    }

    public File getFileWithoutCreating() {
        return createFileInstance(fileName);
    }

    @Override
    public File getFile() {
        File file = createFileInstance(fileName);
        if(!file.exists()){
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
        return file;
    }

    private File createFileInstance(String fileName) {
        return new File(getTmpDir(), fileName);
    }

    private class FileVisitDetailsImpl extends AbstractFileTreeElement implements FileVisitDetails {
        private final String fileName;
        private final Action<OutputStream> generator;
        private long lastModified;
        private long size;
        private File file;

        public FileVisitDetailsImpl(String fileName, Action<OutputStream> generator, Chmod chmod) {
            super(chmod);
            this.fileName = fileName;
            this.generator = generator;
        }

        public String getDisplayName() {
            return fileName;
        }

        public void stopVisiting() {
            // only one file
        }

        public File getFile() {
            if (file == null) {
                file = createFileInstance(fileName);
                if (!file.exists()) {
                    copyTo(file);
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

        public boolean isDirectory() {
            return false;
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
            return RelativePath.parse(true, fileName);
        }
    }

    @Override
    public void registerWatchPoints(FileSystemSubset.Builder builder) {
    }

    @Override
    public void visitTreeOrBackingFile(FileVisitor visitor) {
        visitor.visitFile(new DefaultFileVisitDetails(getFile(), fileSystem, fileSystem));
    }
}
