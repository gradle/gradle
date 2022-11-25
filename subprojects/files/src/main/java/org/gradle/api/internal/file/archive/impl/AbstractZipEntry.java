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

import com.google.common.io.ByteStreams;
import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.internal.io.IoFunction;

import java.io.IOException;
import java.io.InputStream;

abstract class AbstractZipEntry implements ZipEntry {
    private final java.util.zip.ZipEntry entry;

    public AbstractZipEntry(java.util.zip.ZipEntry entry) {
        this.entry = entry;
    }

    protected java.util.zip.ZipEntry getEntry() {
        return entry;
    }

    @Override
    public boolean isDirectory() {
        return entry.isDirectory();
    }

    @Override
    public String getName() {
        return entry.getName();
    }

    @Override
    public int size() {
        return (int) entry.getSize();
    }

    @Override
    public byte[] getContent() throws IOException {
        return withInputStream(new IoFunction<InputStream, byte[]>() {
            @Override
            public byte[] apply(InputStream inputStream) throws IOException {
                int size = size();
                if (size >= 0) {
                    byte[] content = new byte[size];
                    ByteStreams.readFully(inputStream, content);
                    return content;
                } else {
                    return ByteStreams.toByteArray(inputStream);
                }
            }
        });
    }

    @Override
    public ZipCompressionMethod getCompressionMethod() {
        switch (entry.getMethod()) {
            case java.util.zip.ZipEntry.STORED:
                return ZipCompressionMethod.STORED;
            case java.util.zip.ZipEntry.DEFLATED:
                return ZipCompressionMethod.DEFLATED;
            default:
                return ZipCompressionMethod.OTHER;
        }
    }
}
