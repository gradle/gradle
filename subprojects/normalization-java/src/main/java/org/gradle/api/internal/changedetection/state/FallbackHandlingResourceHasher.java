/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContext;
import org.gradle.internal.fingerprint.hashing.ResourceHasher;
import org.gradle.internal.fingerprint.hashing.ZipEntryContext;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.io.IoFunction;
import org.gradle.internal.io.IoSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.function.Supplier;

abstract class FallbackHandlingResourceHasher implements ResourceHasher {
    private static final Logger LOGGER = LoggerFactory.getLogger(FallbackHandlingResourceHasher.class);
    private static final int MAX_FALLBACK_CONTENT_SIZE = 1024*1024*10;

    private final ResourceHasher delegate;

    public FallbackHandlingResourceHasher(ResourceHasher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        delegate.appendConfigurationToHasher(hasher);
        hasher.putString(this.getClass().getName());
    }

    @Nullable
    @Override
    public HashCode hash(RegularFileSnapshotContext snapshotContext) throws IOException {
        return Optional.of(snapshotContext)
            .filter(this::filter)
            .flatMap(path -> tryHash(snapshotContext))
            .orElseGet(IoSupplier.wrap(() -> delegate.hash(snapshotContext)));
    }

    @Nullable
    @Override
    public HashCode hash(ZipEntryContext zipEntryContext) throws IOException {
        // If we should hash this resource and can fallback safely, attempt to hash the resource.
        // If we encounter an error, or the hasher elects not to handle the resource, hash with
        // the delegate using the fallback safe context.
        // If we should not handle this resource, or we cannot fallback safely, hash with the delegate
        // using the original context.
        return Optional.of(zipEntryContext)
            .filter(this::filter)
            .flatMap(FallbackHandlingResourceHasher::withFallbackSafety)
            .map(this::hashSafely)
            .orElse(hashWithDelegate(zipEntryContext))
            .get();
    }

    private Supplier<HashCode> hashSafely(ZipEntryContext safeContext) {
        return IoSupplier.wrap(() -> tryHash(safeContext).orElseGet(hashWithDelegate(safeContext)));
    }

    private Supplier<HashCode> hashWithDelegate(ZipEntryContext context) {
        return IoSupplier.wrap(() -> delegate.hash(context));
    }

    private static Optional<ZipEntryContext> withFallbackSafety(ZipEntryContext zipEntryContext) {
        ZipEntry entry = zipEntryContext.getEntry();
        if (entry.canReopen()) {
            return Optional.of(zipEntryContext);
        } else if (entry.size() > MAX_FALLBACK_CONTENT_SIZE) {
            LOGGER.debug(zipEntryContext.getFullName() + " is too large (" + entry.size() + ") for safe fallback - skipping.");
            return Optional.empty();
        } else {
            return Optional.of(new DefaultZipEntryContext(new CachingZipEntry(entry), zipEntryContext.getFullName(), zipEntryContext.getRootParentName()));
        }
    }

    /**
     * Whether this resource hasher should try to hash the file or pass it to the delegate
     */
    abstract boolean filter(RegularFileSnapshotContext context);

    /**
     * Whether this resource hasher should try to hash the zip entry or pass it to the delegate
     */
    abstract boolean filter(ZipEntryContext context);

    /**
     * Try to hash the resource, and signal for fallback if it can't be hashed.
     *
     * @return An Optional containing the hash, or an empty Optional if fallback should be triggered
     */
    abstract Optional<HashCode> tryHash(RegularFileSnapshotContext snapshotContext);

    /**
     * Try to hash the resource, and signal for fallback if it can't be hashed.
     *
     * @return An Optional containing the hash, or an empty Optional if fallback should be triggered
     */
    abstract Optional<HashCode> tryHash(ZipEntryContext zipEntryContext);

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
        public <T> T withInputStream(IoFunction<InputStream, T> action) throws IOException {
            return action.apply(new ByteArrayInputStream(getContent()));
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean canReopen() {
            return true;
        }

        @Override
        public ZipCompressionMethod getCompressionMethod() {
            return delegate.getCompressionMethod();
        }
    }
}
