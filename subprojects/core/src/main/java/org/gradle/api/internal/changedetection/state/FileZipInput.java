/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.AbstractIterator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.function.Supplier;
import java.util.zip.ZipFile;

class FileZipInput implements ZipInput {

    private final ZipFile file;
    private final Enumeration<? extends java.util.zip.ZipEntry> entries;

    public FileZipInput(File file) {
        try {
            this.file = new ZipFile(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        this.entries = this.file.entries();
    }

    @Override
    public Iterator<ZipEntry> iterator() {
        return new AbstractIterator<ZipEntry>() {
            @Override
            protected ZipEntry computeNext() {
                if (!entries.hasMoreElements()) {
                    return endOfData();
                }
                final java.util.zip.ZipEntry zipEntry = entries.nextElement();
                return new JdkZipEntry(zipEntry, new Supplier<InputStream>() {
                    @Override
                    public InputStream get() {
                        try {
                            return file.getInputStream(zipEntry);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
            }
        };
    }

    @Override
    public void close() throws IOException {
        file.close();
    }
}
