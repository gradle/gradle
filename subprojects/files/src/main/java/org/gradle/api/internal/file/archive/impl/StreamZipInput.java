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
import org.gradle.internal.file.FileException;
import org.gradle.internal.io.IoFunction;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.zip.ZipInputStream;

public class StreamZipInput implements ZipInput {

    private final ZipInputStream inputStream;

    public StreamZipInput(InputStream inputStream) {
        this.inputStream = new ZipInputStream(inputStream);
    }

    @Override
    public Iterator<ZipEntry> iterator() {
        return new AbstractIterator<ZipEntry>() {
            @Override
            protected ZipEntry computeNext() {
                java.util.zip.ZipEntry nextEntry;
                try {
                    nextEntry = inputStream.getNextEntry();
                } catch (IOException e) {
                    throw new FileException(e);
                }
                return nextEntry == null ? endOfData() : new StreamZipEntry(nextEntry);
            }
        };
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }

    private class StreamZipEntry extends AbstractZipEntry {
        private boolean opened;

        public StreamZipEntry(java.util.zip.ZipEntry entry) {
            super(entry);
        }

        @Override
        public <T> T withInputStream(IoFunction<InputStream, T> action) throws IOException {
            if (opened) {
                throw new IllegalStateException("The input stream for " + getName() + " has already been opened.  It cannot be reopened again.");
            }

            opened = true;

            try {
                return action.apply(inputStream);
            } finally {
                closeEntry();
            }
        }

        private void closeEntry() {
            try {
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
