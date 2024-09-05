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

import com.google.common.base.Preconditions;
import com.google.common.hash.Hashing;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.file.archive.ZipEntryConstants;
import org.gradle.internal.classpath.ClasspathEntryVisitor.Entry.CompressionMethod;
import org.gradle.util.internal.GFileUtils;

import javax.annotation.Nullable;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@NonNullApi
public class InPlaceClasspathBuilder implements ClasspathBuilder {
    private static final int BUFFER_SIZE = 8192;

    @Override
    public void jar(File jarFile, Action action) {
        try {
            buildJar(jarFile, action);
        } catch (Exception e) {
            throw new GradleException(String.format("Failed to create Jar file %s.", jarFile), e);
        }
    }

    private static void buildJar(File jarFile, Action action) throws IOException {
        Files.createDirectories(jarFile.getParentFile().toPath());
        try (ZipArchiveOutputStream outputStream = new ZipArchiveOutputStream(new BufferedOutputStream(Files.newOutputStream(jarFile.toPath()), BUFFER_SIZE))) {
            outputStream.setLevel(0);
            action.execute(new ZipEntryBuilder(outputStream));
        }
    }

    @NonNullApi
    private static class ZipEntryBuilder implements EntryBuilder {
        private final ZipArchiveOutputStream outputStream;
        private final Set<String> dirs = new HashSet<>();

        public ZipEntryBuilder(ZipArchiveOutputStream outputStream) {
            this.outputStream = outputStream;
        }

        @Override
        public void put(String name, byte[] content, CompressionMethod compressionMethod) throws IOException {
            maybeAddParent(name);
            ZipArchiveEntry zipEntry = newZipEntryWithFixedTime(name);
            configureCompression(zipEntry, compressionMethod, content);
            outputStream.setEncoding("UTF-8");
            outputStream.putArchiveEntry(zipEntry);
            outputStream.write(content);
            outputStream.closeArchiveEntry();
        }

        private void maybeAddParent(String name) throws IOException {
            String dir = dir(name);
            if (dir != null && dirs.add(dir)) {
                maybeAddParent(dir);
                ZipArchiveEntry zipEntry = newZipEntryWithFixedTime(dir);
                outputStream.putArchiveEntry(zipEntry);
                outputStream.closeArchiveEntry();
            }
        }

        @Nullable
        String dir(String name) {
            int pos = name.lastIndexOf('/');
            if (pos == name.length() - 1) {
                pos = name.lastIndexOf('/', pos - 1);
            }
            if (pos >= 0) {
                return name.substring(0, pos + 1);
            } else {
                return null;
            }
        }

        private ZipArchiveEntry newZipEntryWithFixedTime(String name) {
            ZipArchiveEntry entry = new ZipArchiveEntry(name);
            entry.setTime(ZipEntryConstants.CONSTANT_TIME_FOR_ZIP_ENTRIES);
            return entry;
        }

        private void configureCompression(ZipArchiveEntry entry, CompressionMethod compressionMethod, byte[] contents) {
            if (shouldCompress(compressionMethod)) {
                entry.setMethod(ZipArchiveEntry.DEFLATED);
            } else {
                entry.setMethod(ZipArchiveEntry.STORED);
                // A stored ZipEntry requires setting size and CRC32 upfront.
                // See https://stackoverflow.com/q/1206970.
                entry.setSize(contents.length);
                entry.setCompressedSize(contents.length);
                entry.setCrc(computeCrc32Of(contents));
            }
        }

        private static boolean shouldCompress(CompressionMethod compressionMethod) {
            // Stored files may be used for memory mapping, so it is important to store them uncompressed.
            // All other files are fine being compressed to reduce on-disk size.
            // It isn't clear if storing them uncompressed too would bring a performance benefit,
            // as reading less from the disk may save more time than spent unpacking.
            return compressionMethod != CompressionMethod.STORED;
        }

        private static long computeCrc32Of(byte[] contents) {
            return Hashing.crc32().hashBytes(contents).padToLong();
        }
    }

    @Override
    public void directory(File destinationDir, Action action) {
        try {
            buildDirectory(destinationDir, action);
        } catch (Exception e) {
            throw new GradleException(String.format("Failed to create class directory %s.", destinationDir), e);
        }
    }

    private static void buildDirectory(File destinationDir, Action action) throws IOException {
        clearDirectory(destinationDir);
        Files.createDirectories(destinationDir.toPath());
        action.execute(new DirectoryEntryBuilder(destinationDir));
    }

    @NonNullApi
    private static class DirectoryEntryBuilder implements EntryBuilder {
        private final File baseDir;

        public DirectoryEntryBuilder(File baseDir) {
            this.baseDir = baseDir;
        }

        @Override
        public void put(String name, byte[] content, CompressionMethod compressionMethod) throws IOException {
            File target = new File(baseDir, name);
            if (target.exists()) {
                throw new IllegalArgumentException("Duplicate entry " + name);
            }
            Files.createDirectories(target.getParentFile().toPath());
            Files.write(target.toPath(), content, StandardOpenOption.CREATE_NEW);
        }
    }

    private static void clearDirectory(File dir) {
        if (!dir.exists()) {
            return;
        }
        Preconditions.checkArgument(dir.isDirectory(), "Cannot clear contents of %s because it is not a directory", dir.getAbsolutePath());
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            GFileUtils.forceDelete(file);
        }
    }
}
