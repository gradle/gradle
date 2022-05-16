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
import org.gradle.api.internal.file.archive.ZipEntryHandler;

import java.io.IOException;
import java.io.InputStream;

class JdkZipEntry implements ZipEntry {
    private final ZipEntryHandler zipEntryHandler;

    public JdkZipEntry(ZipEntryHandler zipEntryHandler) {
        this.zipEntryHandler = zipEntryHandler;
    }

    @Override
    public boolean isDirectory() {
        return zipEntryHandler.getZipEntry().isDirectory();
    }

    @Override
    public String getName() {
        return zipEntryHandler.getZipEntry().getName();
    }

    @Override
    public byte[] getContent() throws IOException {
        return withInputStream(new InputStreamAction<byte[]>() {
            @Override
            public byte[] run(InputStream inputStream) throws IOException {
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
    public <T> T withInputStream(InputStreamAction<T> action) throws IOException {
        InputStream is = zipEntryHandler.getInputStream();
        try {
            return action.run(is);
        } finally {
            zipEntryHandler.closeEntry();
        }
    }

    @Override
    public int size() {
        return (int) zipEntryHandler.getZipEntry().getSize();
    }

    @Override
    public boolean isSafeForFallback() {
        return zipEntryHandler.canReopen();
    }
}
