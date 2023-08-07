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
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.file.SymbolicLinkDetails;
import org.gradle.api.internal.file.AbstractFileTreeElement;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.file.Chmod;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.util.internal.GFileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * A generated file tree which is composed using a mapping from relative path to file source.
 */
public class GeneratedSingletonFileTree implements FileSystemMirroringFileTree, GeneratedFiles {
    private final Factory<File> tmpDirSource;
    private final FileSystem fileSystem;

    private final String fileName;
    private final Action<File> fileGenerationListener;
    private final Action<OutputStream> contentWriter;

    public GeneratedSingletonFileTree(Factory<File> tmpDirSource, String fileName, Action<File> fileGenerationListener, Action<OutputStream> contentWriter, FileSystem fileSystem) {
        this.tmpDirSource = tmpDirSource;
        this.fileName = fileName;
        this.fileGenerationListener = fileGenerationListener;
        this.contentWriter = contentWriter;
        this.fileSystem = fileSystem;
    }

    public Spec toSpec() {
        return new Spec(tmpDirSource, fileName, fileGenerationListener, contentWriter);
    }

    public static class Spec {

        public final Factory<File> tmpDir;
        public final String fileName;
        public final Action<File> fileGenerationListener;
        public final Action<OutputStream> contentGenerator;

        public Spec(Factory<File> tmpDir, String fileName, Action<File> fileGenerationListener, Action<OutputStream> contentGenerator) {
            this.tmpDir = tmpDir;
            this.fileName = fileName;
            this.fileGenerationListener = fileGenerationListener;
            this.contentGenerator = contentGenerator;
        }
    }

    private File getTmpDir() {
        return tmpDirSource.create();
    }

    @Override
    public String getDisplayName() {
        return "file tree";
    }

    public File getFileWithoutCreating() {
        return createFileInstance(fileName);
    }

    public File getFile() {
        return new FileVisitDetailsImpl(fileName, contentWriter, fileSystem).getFile();
    }

    private File createFileInstance(String fileName) {
        return new File(getTmpDir(), fileName);
    }

    @Override
    public DirectoryFileTree getMirror() {
        return new DirectoryFileTree(getFile(), new PatternSet(), fileSystem);
    }

    @Override
    public void visitStructure(MinimalFileTreeStructureVisitor visitor, FileTreeInternal owner) {
        visitor.visitFileTree(getFile(), new PatternSet(), owner);
    }

    @Override
    public void visit(FileVisitor visitor) {
        FileVisitDetails fileVisitDetails = new FileVisitDetailsImpl(fileName, contentWriter, fileSystem);
        visitor.visitFile(fileVisitDetails);
    }

    private class FileVisitDetailsImpl extends AbstractFileTreeElement implements FileVisitDetails {
        private final String fileName;
        private final Action<OutputStream> generator;
        private long lastModified;
        private long size;
        private File file;
        private final Chmod chmod;

        public FileVisitDetailsImpl(String fileName, Action<OutputStream> generator, Chmod chmod) {
            super(chmod);
            this.fileName = fileName;
            this.generator = generator;
            this.chmod = chmod;
        }

        @Override
        public String getDisplayName() {
            return fileName;
        }

        @Override
        public void stopVisiting() {
            // only one file
        }

        @Override
        public File getFile() {
            if (file == null) {
                file = createFileInstance(fileName);
                if (!file.exists()) {
                    fileGenerationListener.execute(file);

                    GFileUtils.mkdirs(file.getParentFile());
                    updateFileOnlyWhenGeneratedContentChanges();
                    chmod.chmod(file, getPermissions().toUnixNumeric());
                } else {
                    updateFileOnlyWhenGeneratedContentChanges();
                }
                // round to nearest second
                lastModified = file.lastModified() / 1000 * 1000;
                size = file.length();
            }
            return file;
        }

        // prevent file system change events when generated content
        // remains the same as the content in the existing file
        private void updateFileOnlyWhenGeneratedContentChanges() {
            byte[] generatedContent = generateContent();
            if (!hasContent(generatedContent, file)) {
                try {
                    fileGenerationListener.execute(file);
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

        @Override
        public void copyTo(OutputStream output) {
            generator.execute(output);
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public long getLastModified() {
            getFile();
            return lastModified;
        }

        @Override
        public long getSize() {
            getFile();
            return size;
        }

        @Override
        public InputStream open() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RelativePath getRelativePath() {
            return new RelativePath(true, fileName);
        }

        @Nullable
        @Override
        public SymbolicLinkDetails getSymbolicLinkDetails() {
            return null; //FIXME
        }

    }
}
