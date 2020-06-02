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

import com.google.common.base.Supplier;
import com.google.common.collect.AbstractIterator;
import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.api.internal.file.archive.ZipInput;
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
                return nextEntry == null ? endOfData() : new JdkZipEntry(nextEntry, new Supplier<InputStream>() {
                    @Override
                    public InputStream get() {
                        return in;
                    }
                });
            }
        };
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
