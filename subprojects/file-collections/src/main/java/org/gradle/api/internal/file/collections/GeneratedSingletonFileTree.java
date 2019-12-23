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
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.AbstractFileTreeElement;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.file.Chmod;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.services.FileSystems;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * A {@link SingletonFileTree} which is composed using a mapping from relative path to file source.
 */
public class GeneratedSingletonFileTree extends AbstractSingletonFileTree implements GeneratedFiles {
    private final Factory<File> tmpDirSource;
    private final FileSystem fileSystem = FileSystems.getDefault();

    private final String fileName;
    private final Consumer<String> beforeFileChange;
    private final Action<OutputStream> contentWriter;

    public GeneratedSingletonFileTree(Factory<File> tmpDirSource, String fileName, Consumer<String> beforeFileChange, Action<OutputStream> contentWriter) {
        this(tmpDirSource, fileName, new PatternSet(), beforeFileChange, contentWriter);
    }

    public GeneratedSingletonFileTree(Factory<File> tmpDirSource, String fileName, PatternSet patternSet, Consumer<String> beforeFileChange, Action<OutputStream> contentWriter) {
        super(patternSet);
        this.tmpDirSource = tmpDirSource;
        this.fileName = fileName;
        this.beforeFileChange = beforeFileChange;
        this.contentWriter = contentWriter;
    }

    private File getTmpDir() {
        return tmpDirSource.create();
    }

    @Override
    public String getDisplayName() {
        return "file tree";
    }

    @Override
    protected FileVisitDetails createFileVisitDetails() {
        return new FileVisitDetailsImpl(fileName, contentWriter, fileSystem);
    }

    public File getFileWithoutCreating() {
        return createFileInstance(fileName);
    }

    @Override
    public File getFile() {
        return new FileVisitDetailsImpl(fileName, contentWriter, fileSystem).getFile();
    }

    private File createFileInstance(String fileName) {
        return new File(getTmpDir(), fileName);
    }

    @Override
    public MinimalFileTree filter(PatternFilterable patterns) {
        return new GeneratedSingletonFileTree(tmpDirSource, fileName, filterPatternSet(patterns), beforeFileChange, contentWriter);
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
                    copyTo(file);
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
                    beforeFileChange.accept(file.getAbsolutePath());
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

    }
}
