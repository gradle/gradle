/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.caching.internal;

import org.apache.commons.io.IOUtils;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * {@code BuildCacheService} decorator that stages files locally from a remote build cache. This provides a separation between
 * a build cache problem and a {@code BuildCacheEntryReader} or {@code BuildCacheEntryWriter} problem.
 */
public class StagingBuildCacheServiceDecorator extends AbstractRoleAwareBuildCacheServiceDecorator {
    private final boolean stageCacheEntries;
    private final TemporaryFileProvider temporaryFileProvider;

    public StagingBuildCacheServiceDecorator(TemporaryFileProvider temporaryFileProvider, boolean stageCacheEntries, RoleAwareBuildCacheService delegate) {
        super(delegate);
        this.stageCacheEntries = stageCacheEntries;
        this.temporaryFileProvider = temporaryFileProvider;
    }

    public StagingBuildCacheServiceDecorator(TemporaryFileProvider temporaryFileProvider, RoleAwareBuildCacheService delegate) {
        this(temporaryFileProvider, !(delegate instanceof DirectoryBuildCacheService), delegate);
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        if (stageCacheEntries) {
            return super.load(key, new StagingBuildCacheEntryReader(reader, temporaryFileProvider));
        } else {
            return super.load(key, reader);
        }
    }

    @Override
    public void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
        if (stageCacheEntries) {
            super.store(key, new StagingBuildCacheEntryWriter(writer, temporaryFileProvider));
        } else {
            super.store(key, writer);
        }
    }

    /**
     * Reads the cache entry to a local file from the build cache and then reads the cache entry from the local file.
     */
     private static class StagingBuildCacheEntryReader implements BuildCacheEntryReader {
        private final BuildCacheEntryReader reader;
        private final TemporaryFileProvider temporaryFileProvider;

        private StagingBuildCacheEntryReader(BuildCacheEntryReader reader, TemporaryFileProvider temporaryFileProvider) {
            this.reader = reader;
            this.temporaryFileProvider = temporaryFileProvider;
        }

        @Override
        public void readFrom(InputStream input) throws IOException {
            File destination = temporaryFileProvider.createTemporaryFile("gradle_cache", "entry");
            try {
                stageCacheEntry(input, destination);
                readCacheEntry(destination);
            } finally {
                destination.delete();
            }
        }

        private void stageCacheEntry(InputStream input, File destination) throws IOException {
            OutputStream fileOutputStream = null;
            try {
                fileOutputStream = new BufferedOutputStream(new FileOutputStream(destination));
                IOUtils.copyLarge(input, fileOutputStream);
            } finally {
                IOUtils.closeQuietly(fileOutputStream);
            }
        }

        private void readCacheEntry(File destination) throws IOException {
            InputStream fileInputStream = null;
            try {
                fileInputStream = new BufferedInputStream(new FileInputStream(destination));
                reader.readFrom(fileInputStream);
            } catch (FileNotFoundException e) {
                throw new BuildCacheException("Couldn't create local file for cache entry", e);
            } finally {
                IOUtils.closeQuietly(fileInputStream);
            }
        }
    }

    /**
     * Writes the new cache entry to a local file and then pushes the local file to the delegate build cache.
     */
    private static class StagingBuildCacheEntryWriter implements BuildCacheEntryWriter {
        private final BuildCacheEntryWriter writer;
        private final TemporaryFileProvider temporaryFileProvider;

        private StagingBuildCacheEntryWriter(BuildCacheEntryWriter writer, TemporaryFileProvider temporaryFileProvider) {
            this.writer = writer;
            this.temporaryFileProvider = temporaryFileProvider;
        }

        @Override
        public void writeTo(OutputStream output) throws IOException {
            File destination = temporaryFileProvider.createTemporaryFile("gradle_cache", "entry");
            try {
                writeCacheEntry(destination);
                unstageCacheEntry(output, destination);
            } finally {
                destination.delete();
            }
        }

        private void writeCacheEntry(File destination) throws IOException {
            OutputStream fileOutputStream = null;
            try {
                fileOutputStream = new BufferedOutputStream(new FileOutputStream(destination));
                writer.writeTo(fileOutputStream);
            } catch (FileNotFoundException e) {
                throw new BuildCacheException("Couldn't create local file for cache entry", e);
            } finally {
                IOUtils.closeQuietly(fileOutputStream);
            }
        }

        private void unstageCacheEntry(OutputStream output, File destination) throws IOException {
            InputStream fileInputStream = null;
            try {
                fileInputStream = new BufferedInputStream(new FileInputStream(destination));
                IOUtils.copyLarge(fileInputStream, output);
            } finally {
                IOUtils.closeQuietly(fileInputStream);
            }
        }
    }
}
