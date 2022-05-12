/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.classpath;

import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.artifacts.GradleApiVersionProvider;
import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.api.internal.file.archive.ZipInput;
import org.gradle.api.internal.file.archive.impl.FileZipInput;
import org.gradle.internal.file.FileException;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.FileType;
import org.gradle.internal.file.Stat;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.ProviderNotFoundException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

/**
 * Allows the classes and resources of a classpath element such as a jar or directory to be visited.
 */
@ServiceScope(Scopes.UserHome.class)
public class ClasspathWalker {
    private final Stat stat;

    public ClasspathWalker(Stat stat) {
        this.stat = stat;
    }

    /**
     * Visits the entries of the given classpath element.
     *
     * @throws FileException On failure to open a Jar file.
     */
    public void visit(File root, ClasspathEntryVisitor visitor) throws IOException, FileException {
        FileMetadata fileMetadata = stat.stat(root);
        if (fileMetadata.getType() == FileType.RegularFile) {
            visitJarContents(root, visitor);
        } else if (fileMetadata.getType() == FileType.Directory) {
            visitDirectoryContents(root, visitor);
        }
    }

    private void visitDirectoryContents(File dir, ClasspathEntryVisitor visitor) throws IOException {
        boolean isCurrentGradleApi = new File(dir, GradleApiVersionProvider.GRADLE_VERSION_MARKER).exists();
        if (!isCurrentGradleApi) {
            LoggerFactory.getLogger(ClasspathWalker.class).warn("Not using current Gradle API for " + dir);
        }
        visitDir(dir, "", isCurrentGradleApi, visitor);
    }

    private void visitDir(File dir, String prefix, boolean isCurrentGradleApi, ClasspathEntryVisitor visitor) throws IOException {
        File[] files = dir.listFiles();

        // Apply a consistent order, regardless of file system ordering
        Arrays.sort(files, Comparator.comparing(File::getName));

        for (File file : files) {
            FileMetadata fileMetadata = stat.stat(file);
            if (fileMetadata.getType() == FileType.RegularFile) {
                visitFile(file, prefix + file.getName(), isCurrentGradleApi, visitor);
            } else if (fileMetadata.getType() == FileType.Directory) {
                visitDir(file, prefix + file.getName() + "/", isCurrentGradleApi, visitor);
            }
        }
    }

    private void visitFile(File file, String name, boolean isCurrentGradleApi, ClasspathEntryVisitor visitor) throws IOException {
        visitor.visit(new FileEntry(name, file, isCurrentGradleApi));
    }

    private void visitJarContents(File jarFile, ClasspathEntryVisitor visitor) throws IOException {
        boolean isCurrentGradleApi;
        try (FileSystem zipFileSystem = FileSystems.newFileSystem(URI.create("jar:" + jarFile.toPath().toUri()), Collections.emptyMap())) {
            isCurrentGradleApi = Files.exists(zipFileSystem.getPath(GradleApiVersionProvider.GRADLE_VERSION_MARKER));
        } catch (ProviderNotFoundException e) {
            throw new FileException(e);
        }
        try (ZipInput entries = FileZipInput.create(jarFile)) {
            for (ZipEntry entry : entries) {
                if (entry.isDirectory()) {
                    continue;
                }
                visitor.visit(new ZipClasspathEntry(entry, isCurrentGradleApi));
            }
        }
    }

    public static class ZipClasspathEntry implements ClasspathEntryVisitor.Entry {
        private final ZipEntry entry;
        private final boolean isCurrentGradleApi;

        public ZipClasspathEntry(ZipEntry entry, boolean isCurrentGradleApi) {
            this.entry = entry;
            this.isCurrentGradleApi = isCurrentGradleApi;
        }

        @Override
        public String getName() {
            return entry.getName();
        }

        @Override
        public RelativePath getPath() {
            return RelativePath.parse(false, getName());
        }

        @Override
        public boolean isCurrentGradleApi() {
            return isCurrentGradleApi;
        }

        @Override
        public byte[] getContent() throws IOException {
            return entry.getContent();
        }
    }

    private static class FileEntry implements ClasspathEntryVisitor.Entry {
        private final String name;
        private final File file;
        private final boolean isCurrentGradleApi;

        public FileEntry(String name, File file, boolean isCurrentGradleApi) {
            this.name = name;
            this.file = file;
            this.isCurrentGradleApi = isCurrentGradleApi;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public RelativePath getPath() {
            return RelativePath.parse(false, name);
        }

        @Override
        public boolean isCurrentGradleApi() {
            return isCurrentGradleApi;
        }

        @Override
        public byte[] getContent() throws IOException {
            return Files.readAllBytes(file.toPath());
        }
    }
}
