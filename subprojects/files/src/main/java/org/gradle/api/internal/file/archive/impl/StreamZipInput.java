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

package org.gradle.api.internal.file.archive.impl;

import com.google.common.collect.AbstractIterator;
import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.api.internal.file.archive.ZipInput;
import org.gradle.api.internal.file.archive.ZipEntryHandler;
import org.gradle.internal.file.FileException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.zip.ZipInputStream;

public class StreamZipInput implements ZipInput {

    private final ZipInputStream in;

    public StreamZipInput(InputStream in) {
        this.in = new ZipInputStream(in);
    }

    @Override
    public Iterator<ZipEntry> iterator() {
        return new AbstractIterator<ZipEntry>() {
            @Override
            protected ZipEntry computeNext() {
                java.util.zip.ZipEntry nextEntry;
                try {
                    nextEntry = in.getNextEntry();
                } catch (IOException e) {
                    throw new FileException(e);
                }
                return nextEntry == null ? endOfData() : new JdkZipEntry(new StreamZipEntryHandler(nextEntry, in));
            }
        };
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    private static class StreamZipEntryHandler implements ZipEntryHandler {
        private final java.util.zip.ZipEntry zipEntry;
        private final ZipInputStream inputStream;
        private boolean closed;

        public StreamZipEntryHandler(java.util.zip.ZipEntry zipEntry, ZipInputStream inputStream) {
            this.zipEntry = zipEntry;
            this.inputStream = inputStream;
        }

        @Override
        public java.util.zip.ZipEntry getZipEntry() {
            return zipEntry;
        }

        @Override
        public InputStream getInputStream() {
            if (closed) {
                throw new IllegalStateException("The input stream for " + zipEntry.getName() + " has already been closed.  It cannot be reopened again.");
            }

            return inputStream;
        }

        @Override
        public void closeEntry() {
            try {
                closed = true;
                inputStream.closeEntry();
            } catch (IOException e) {
                throw new FileException(e);
            }
        }

        @Override
        public boolean canReopen() {
            return false;
        }
    }
}
