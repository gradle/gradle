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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.changedetection.resources.SnapshottableReadableResource;
import org.gradle.api.internal.changedetection.resources.SnapshottableResource;
import org.gradle.api.internal.changedetection.state.TreeSnapshot;
import org.gradle.internal.IoActions;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipTreeSnapshot implements TreeSnapshot {
    private final SnapshottableReadableResource zipFile;
    private ZipIterator zipIterator;

    public ZipTreeSnapshot(SnapshottableReadableResource zipFile) {
        this.zipFile = zipFile;
    }

    @Override
    public SnapshottableResource getRoot() {
        return zipFile;
    }

    @Override
    public Iterable<? extends SnapshottableResource> getDescendants() throws IOException {
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
        private final SnapshottableReadableResource zipFile;
        private ZipInputStream input;
        private ZipEntry nextEntry;

        public ZipIterator(SnapshottableReadableResource zipFile) {
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
            SnapshottableResource resource = nextEntry.isDirectory() ? new ZipDirectoryEntry(nextEntry) : new ZipFileEntry(input, nextEntry);
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
}
