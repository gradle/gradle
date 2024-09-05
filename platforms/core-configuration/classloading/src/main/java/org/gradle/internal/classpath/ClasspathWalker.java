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
import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.api.internal.file.archive.ZipInput;
import org.gradle.api.internal.file.archive.impl.FileZipInput;
import org.gradle.internal.file.FileException;
import org.gradle.internal.file.FileMetadata;
import org.gradle.internal.file.FileType;
import org.gradle.internal.file.Stat;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Allows the classes and resources of a classpath element such as a jar or directory to be visited.
 */
@ServiceScope(Scope.UserHome.class)
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
        visitDir(dir, "", visitor);
    }

    private void visitDir(File dir, String prefix, ClasspathEntryVisitor visitor) throws IOException {
        File[] files = dir.listFiles();

        // Apply a consistent order, regardless of file system ordering
        Arrays.sort(files, Comparator.comparing(File::getName));

        for (File file : files) {
            FileMetadata fileMetadata = stat.stat(file);
            if (fileMetadata.getType() == FileType.RegularFile) {
                visitFile(file, prefix + file.getName(), visitor);
            } else if (fileMetadata.getType() == FileType.Directory) {
                visitDir(file, prefix + file.getName() + "/", visitor);
            }
        }
    }

    private void visitFile(File file, String name, ClasspathEntryVisitor visitor) throws IOException {
        visitor.visit(new FileEntry(name, file));
    }

    private void visitJarContents(File jarFile, ClasspathEntryVisitor visitor) throws IOException {
        try (ZipInput entries = FileZipInput.create(jarFile)) {
            for (ZipEntry entry : entries) {
                if (entry.isDirectory()) {
                    continue;
                }
                visitor.visit(new ZipClasspathEntry(entry));
            }
        }
    }

    private static class ZipClasspathEntry implements ClasspathEntryVisitor.Entry {
        private final ZipEntry entry;

        public ZipClasspathEntry(ZipEntry entry) {
            this.entry = entry;
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
        public byte[] getContent() throws IOException {
            return entry.getContent();
        }

        @Override
        public CompressionMethod getCompressionMethod() {
            switch (entry.getCompressionMethod()) {
                case STORED:
                    return CompressionMethod.STORED;
                case DEFLATED:
                    return CompressionMethod.DEFLATED;
                default:
                    // Zip entries can be in many formats but JARs are unlikely to have them as JVM doesn't
                    // support exotic ones, and the clients mostly don't care.
                    return CompressionMethod.UNDEFINED;
            }
        }
    }

    private static class FileEntry implements ClasspathEntryVisitor.Entry {
        private final String name;
        private final File file;

        public FileEntry(String name, File file) {
            this.name = name;
            this.file = file;
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
        public byte[] getContent() throws IOException {
            return Files.readAllBytes(file.toPath());
        }

        @Override
        public CompressionMethod getCompressionMethod() {
            // One could argue that files have STORED as the compression method, as they obviously aren't compressed.
            // However, this property is mostly an accident of the way this classpath entry was produced.
            // Exposing it may put unnecessary burden on clients if, for example, they try to keep the compression method
            // while repackaging entries.
            return CompressionMethod.UNDEFINED;
        }
    }
}
