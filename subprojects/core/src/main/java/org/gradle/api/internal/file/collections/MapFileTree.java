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
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.AbstractFileTreeElement;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.internal.Factory;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.internal.nativeintegration.filesystem.Chmod;
import org.gradle.util.CollectionUtils;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link MinimalFileTree} which is composed using a mapping from relative path to file source.
 */
public class MapFileTree implements MinimalFileTree, FileSystemMirroringFileTree {
    private final Map<RelativePath, Action<OutputStream>> elements = new LinkedHashMap<RelativePath, Action<OutputStream>>();
    private final Factory<File> tmpDirSource;
    private final Chmod chmod;

    public MapFileTree(final File tmpDir, Chmod chmod) {
        this(new Factory<File>() {
                public File create() {
                    return tmpDir;
                }
        }, chmod);
    }

    public MapFileTree(Factory<File> tmpDirSource, Chmod chmod) {
        this.tmpDirSource = tmpDirSource;
        this.chmod = chmod;
    }

    private File getTmpDir() {
        return tmpDirSource.create();
    }

    public String getDisplayName() {
        return "file tree";
    }

    public DirectoryFileTree getMirror() {
        return new DirectoryFileTree(getTmpDir());
    }

    public void visit(FileVisitor visitor) {
        AtomicBoolean stopFlag = new AtomicBoolean();
        Visit visit = new Visit(visitor, stopFlag);
        for (Map.Entry<RelativePath, Action<OutputStream>> entry : elements.entrySet()) {
            if (stopFlag.get()) {
                break;
            }
            RelativePath path = entry.getKey();
            Action<OutputStream> generator = entry.getValue();
            visit.visit(path, generator);
        }
    }

    public Set<File> getFilesWithoutCreating() {
        return CollectionUtils.collect(elements.keySet(), new Transformer<File, RelativePath>() {
            @Override
            public File transform(RelativePath relativePath) {
                return createFileInstance(relativePath);
            }
        });
    }

    /**
     * Adds an element to this tree. The given closure is passed an OutputStream which it can use to write the content
     * of the element to.
     */
    public void add(String path, Closure contentClosure) {
        Action<OutputStream> action = ConfigureUtil.configureUsing(contentClosure);
        add(path, action);
    }

    public void add(String path, Action<OutputStream> contentWriter) {
        elements.put(RelativePath.parse(true, path), contentWriter);
    }

    private class Visit {
        private final Set<RelativePath> visitedDirs = new LinkedHashSet<RelativePath>();
        private final FileVisitor visitor;
        private final AtomicBoolean stopFlag;

        public Visit(FileVisitor visitor, AtomicBoolean stopFlag) {
            this.visitor = visitor;
            this.stopFlag = stopFlag;
        }

        private void visitDirs(RelativePath path, FileVisitor visitor) {
            if (path == null || path.getParent() == null || !visitedDirs.add(path)) {
                return;
            }

            visitDirs(path.getParent(), visitor);
            visitor.visitDir(new FileVisitDetailsImpl(path, null, stopFlag, chmod));
        }

        public void visit(RelativePath path, Action<OutputStream> generator) {
            visitDirs(path.getParent(), visitor);
            visitor.visitFile(new FileVisitDetailsImpl(path, generator, stopFlag, chmod));
        }
    }

    private File createFileInstance(RelativePath path) {
        return path.getFile(getTmpDir());
    }

    private class FileVisitDetailsImpl extends AbstractFileTreeElement implements FileVisitDetails {
        private final RelativePath path;
        private final Action<OutputStream> generator;
        private long lastModified;
        private long size;
        private final AtomicBoolean stopFlag;
        private File file;
        private final boolean isDirectory;

        public FileVisitDetailsImpl(RelativePath path, Action<OutputStream> generator, AtomicBoolean stopFlag, Chmod chmod) {
            super(chmod);
            this.path = path;
            this.generator = generator;
            this.stopFlag = stopFlag;
            this.isDirectory = !path.isFile();
        }

        public String getDisplayName() {
            return path.toString();
        }

        public void stopVisiting() {
            stopFlag.set(true);
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
