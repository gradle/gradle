/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.internal.file.FilePathUtil;
import org.gradle.internal.fingerprint.hashing.ZipEntryContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Supplier;

public class DefaultZipEntryContext implements ZipEntryContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultZipEntryContext.class);
    private static final int MAX_FALLBACK_CONTENT_SIZE = 1024*1024*10;

    private final ZipEntry entry;
    private final String fullName;
    private final String rootParentName;

    public DefaultZipEntryContext(ZipEntry entry, String fullName, String rootParentName) {
        this.entry = entry;
        this.fullName = fullName;
        this.rootParentName = rootParentName;
    }

    @Override
    public ZipEntry getEntry() {
        return entry;
    }

    @Override
    public String getFullName() {
        return fullName;
    }

    @Override
    public String getRootParentName() {
        return rootParentName;
    }

    @Override
    public Supplier<String[]> getRelativePathSegments() {
        return new ZipEntryRelativePath(entry);
    }

    @Override
    public Optional<ZipEntryContext> withFallbackSafety() {
        if (entry.isSafeForFallback()) {
            return Optional.of(this);
        } else if (entry.size() > MAX_FALLBACK_CONTENT_SIZE) {
            LOGGER.debug(getFullName() + " is too large (" + entry.size() + ") for safe fallback - skipping.");
            return Optional.empty();
        } else {
            return Optional.of(new FallbackSafeZipEntryContext(this));
        }
    }

    private static class ZipEntryRelativePath implements Supplier<String[]> {
        private final ZipEntry zipEntry;

        ZipEntryRelativePath(ZipEntry zipEntry) {
            this.zipEntry = zipEntry;
        }

        @Override
        public String[] get() {
            return FilePathUtil.getPathSegments(zipEntry.getName());
        }
    }

    private static class CachingZipEntry implements ZipEntry {
        private final ZipEntry delegate;
        private byte[] content;

        public CachingZipEntry(ZipEntry delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isDirectory() {
            return delegate.isDirectory();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public byte[] getContent() throws IOException {
            if (content == null) {
                content = delegate.getContent();
            }
            return content;
        }

        @Override
        public <T> T withInputStream(InputStreamAction<T> action) throws IOException {
            return action.run(new ByteArrayInputStream(getContent()));
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isSafeForFallback() {
            return true;
        }
    }

    private static class FallbackSafeZipEntryContext implements ZipEntryContext {
        private final ZipEntryContext delegate;
        private final ZipEntry zipEntry;

        public FallbackSafeZipEntryContext(ZipEntryContext delegate) {
            this.delegate = delegate;
            this.zipEntry = new CachingZipEntry(delegate.getEntry());
        }

        @Override
        public ZipEntry getEntry() {
            return zipEntry;
        }

        @Override
        public String getFullName() {
            return delegate.getFullName();
        }

        @Override
        public String getRootParentName() {
            return delegate.getRootParentName();
        }

        @Override
        public Supplier<String[]> getRelativePathSegments() {
            return delegate.getRelativePathSegments();
        }

        @Override
        public Optional<ZipEntryContext> withFallbackSafety() {
            return Optional.of(this);
        }
    }
}
