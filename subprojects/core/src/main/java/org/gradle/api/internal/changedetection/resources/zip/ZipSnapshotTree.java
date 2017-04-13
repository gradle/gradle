/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.resources.zip;

import com.google.common.base.Preconditions;
import com.google.common.hash.Hasher;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.changedetection.resources.HashContentSnapshot;
import org.gradle.api.internal.changedetection.resources.SnapshottableResource;
import org.gradle.api.internal.changedetection.state.SnapshotTree;
import org.gradle.internal.IoActions;
import org.gradle.internal.nativeintegration.filesystem.FileType;
import org.gradle.internal.resource.ResourceContentMetadataSnapshot;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.google.common.hash.Funnels.asOutputStream;
import static com.google.common.hash.Hashing.md5;
import static com.google.common.io.ByteStreams.copy;

public class ZipSnapshotTree implements SnapshotTree {
    private final SnapshottableResource zipFile;
    private ZipIterator zipIterator;

    public ZipSnapshotTree(SnapshottableResource zipFile) {
        this.zipFile = zipFile;
    }

    @Override
    public SnapshottableResource getRoot() {
        return zipFile;
    }

    @Override
    public Iterable<? extends SnapshottableResource> getElements() throws IOException {
        return new Iterable<SnapshottableResource>() {
            @Override
            public Iterator<SnapshottableResource> iterator() {
                IoActions.closeQuietly(zipIterator);
                zipIterator = new ZipIterator(zipFile);
                return zipIterator;
            }
        };
    }

    @Override
    public void close() throws IOException {
        IoActions.closeQuietly(zipIterator);
    }

    private static class ZipIterator implements Iterator<SnapshottableResource>, Closeable {
        private final SnapshottableResource zipFile;
        private ZipInputStream input;
        private ZipEntry nextEntry;

        public ZipIterator(SnapshottableResource zipFile) {
            this.zipFile = zipFile;
        }

        @Override
        public boolean hasNext() {
            try {
                if (input == null) {
                    input = new ZipInputStream(zipFile.read());
                }
                if (nextEntry == null) {
                    nextEntry = input.getNextEntry();
                }
                return nextEntry != null;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public SnapshottableResource next() {
            ZipFileResource resource = new ZipFileResource(input, nextEntry);
            nextEntry = null;
            return resource;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Cannot modify zip iterator");
        }

        @Override
        public void close() throws IOException {
            input.close();
        }
    }

    private static class ZipFileResource implements SnapshottableResource {
        private final ZipInputStream input;
        private final String path;
        private final FileType type;
        private boolean used;
        private HashContentSnapshot snapshot;

        public ZipFileResource(ZipInputStream input, ZipEntry zipEntry) {
            this.input = input;
            this.type = zipEntry.isDirectory() ? FileType.Directory : FileType.RegularFile;
            String zipEntryName = zipEntry.getName();
            this.path = type == FileType.Directory ? zipEntryName.substring(0, zipEntryName.length() - 1) : zipEntryName;
        }

        private void ensureUnused() {
            Preconditions.checkState(!used, "Zip file entry cannot be opened twice");
            used = true;
        }

        @Override
        public String getPath() {
            return path;
        }

        @Override
        public String getName() {
            return FilenameUtils.getName(path);
        }

        @Override
        public RelativePath getRelativePath() {
            return new RelativePath(type == FileType.RegularFile, path);
        }

        @Override
        public FileType getType() {
            return type;
        }

        @Override
        public boolean isRoot() {
            return false;
        }

        @Override
        public InputStream read() throws IOException {
            ensureUnused();
            return new CloseShieldInputStream(input);
        }

        @Override
        public ResourceContentMetadataSnapshot getContent() {
            if (snapshot != null) {
                return snapshot;
            }
            ensureUnused();
            Hasher hasher = md5().newHasher();
            try {
                copy(input, asOutputStream(hasher));
                snapshot = new HashContentSnapshot(getType(), hasher.hash());
                return snapshot;
            } catch (IOException e) {
                throw new UncheckedIOException(String.format("Failed to hash file '%s' in Jar found on classpath", getPath()), e);
            }
        }

        @Override
        public SnapshottableResource getRoot() {
            return this;
        }

        @Override
        public Iterable<? extends SnapshottableResource> getElements() {
            return Collections.singleton(this);
        }

        @Override
        public String getDisplayName() {
            return getPath();
        }

        @Override
        public void close() throws IOException {
        }
    }
}
