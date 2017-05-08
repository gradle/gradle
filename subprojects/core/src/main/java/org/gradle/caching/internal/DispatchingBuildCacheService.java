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

package org.gradle.caching.internal;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Files;
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.caching.BuildCacheEntryReader;
import org.gradle.caching.BuildCacheEntryWriter;
import org.gradle.caching.BuildCacheException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.util.GFileUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class DispatchingBuildCacheService implements RoleAwareBuildCacheService {

    @VisibleForTesting
    final RoleAwareBuildCacheService local;
    @VisibleForTesting
    final boolean pushToLocal;
    @VisibleForTesting
    final RoleAwareBuildCacheService remote;
    @VisibleForTesting
    final boolean pushToRemote;

    private final TemporaryFileProvider temporaryFileProvider;
    private final String role;

    DispatchingBuildCacheService(RoleAwareBuildCacheService local, boolean pushToLocal, RoleAwareBuildCacheService remote, boolean pushToRemote, TemporaryFileProvider temporaryFileProvider) {
        this.local = local;
        this.pushToLocal = pushToLocal;
        this.remote = remote;
        this.pushToRemote = pushToRemote;
        this.temporaryFileProvider = temporaryFileProvider;
        this.role = local.getRole() + " and " + remote.getRole();
    }

    @Override
    public boolean load(BuildCacheKey key, BuildCacheEntryReader reader) throws BuildCacheException {
        return local.load(key, reader) || remote.load(key, reader);
    }

    @Override
    public void store(BuildCacheKey key, BuildCacheEntryWriter writer) throws BuildCacheException {
        if (pushToLocal && pushToRemote) {
            pushToLocalAndRemote(key, writer);
        } else if (pushToLocal) {
            local.store(key, writer);
        } else if (pushToRemote) {
            remote.store(key, writer);
        }
    }

    private void pushToLocalAndRemote(BuildCacheKey key, BuildCacheEntryWriter writer) {
        File destination = temporaryFileProvider.createTemporaryFile("gradle_cache", "entry");
        try {
            writeCacheEntryLocally(writer, destination);
            BuildCacheEntryWriter copier = new CopyBuildCacheEntryWriter(destination);
            local.store(key, copier);
            remote.store(key, copier);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            GFileUtils.deleteQuietly(destination);
        }
    }

    private void writeCacheEntryLocally(BuildCacheEntryWriter writer, File destination) throws IOException {
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

    @Override
    public String getRole() {
        return role;
    }

    @Override
    public void close() throws IOException {
        CompositeStoppable.stoppable(local, remote).stop();
    }

    private class CopyBuildCacheEntryWriter implements BuildCacheEntryWriter {
        private final File source;

        private CopyBuildCacheEntryWriter(File source) {
            this.source = source;
        }

        @Override
        public void writeTo(OutputStream output) throws IOException {
            Files.copy(source, output);
        }
    }
}
