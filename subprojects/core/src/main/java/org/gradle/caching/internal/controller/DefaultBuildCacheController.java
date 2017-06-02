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

package org.gradle.caching.internal.controller;

import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Nullable;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.caching.BuildCacheException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.util.GFileUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class DefaultBuildCacheController implements BuildCacheController {

    @VisibleForTesting
    static final int MAX_ERRORS = 3;

    @VisibleForTesting
    final BuildCacheServiceHandle local;

    @VisibleForTesting
    final BuildCacheServiceHandle remote;

    private final TemporaryFileProvider temporaryFileProvider;

    public DefaultBuildCacheController(
        @Nullable BuildCacheServiceRef local,
        @Nullable BuildCacheServiceRef remote,
        BuildOperationExecutor buildOperationExecutor,
        TemporaryFileProvider temporaryFileProvider,
        boolean logStackTraces
    ) {
        this.local = toHandle(local, BuildCacheServiceRole.LOCAL, buildOperationExecutor, logStackTraces);
        this.remote = toHandle(remote, BuildCacheServiceRole.REMOTE, buildOperationExecutor, logStackTraces);
        this.temporaryFileProvider = temporaryFileProvider;
    }

    @Nullable
    @Override
    public <T> T load(BuildCacheLoadCommand<T> command) {
        T metadata = null;
        if (local.canLoad()) {
            metadata = local.doLoad(command);
        }
        if (metadata == null && remote.canLoad()) {
            metadata = remote.doLoad(command);
        }
        return metadata;
    }

    @Override
    public void store(BuildCacheStoreCommand command) {
        boolean localStore = local.canStore();
        boolean remoteStore = remote.canStore();

        if (localStore && remoteStore) {
            doStoreBoth(command);
        } else if (localStore) {
            local.doStore(command);
        } else if (remoteStore) {
            remote.doStore(command);
        }
    }

    private void doStoreBoth(BuildCacheStoreCommand storeOp) {
        File destination = temporaryFileProvider.createTemporaryFile("gradle_cache", "entry");
        try {
            // TODO: demarcate this with an operation.
            BuildCacheStoreCommand.Result result = doTmpFileStore(storeOp, destination);
            local.doStore(storeOp.getKey(), destination, result);
            remote.doStore(storeOp.getKey(), destination, result);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            GFileUtils.deleteQuietly(destination);
        }
    }

    private BuildCacheStoreCommand.Result doTmpFileStore(BuildCacheStoreCommand storeOp, File destination) throws IOException {
        OutputStream fileOutputStream = null;
        try {
            fileOutputStream = new BufferedOutputStream(new FileOutputStream(destination));
            return storeOp.store(fileOutputStream);
        } catch (FileNotFoundException e) {
            throw new BuildCacheException("Couldn't create local file for cache entry", e);
        } finally {
            IOUtils.closeQuietly(fileOutputStream);
        }
    }

    @Override
    public void close() {
        CompositeStoppable.stoppable(local, remote).stop();
    }

    private static BuildCacheServiceHandle toHandle(BuildCacheServiceRef ref, BuildCacheServiceRole role, BuildOperationExecutor buildOperationExecutor, boolean logStackTraces) {
        return ref == null
            ? NullBuildCacheServiceHandle.INSTANCE
            : new DefaultBuildCacheServiceHandle(ref, role, buildOperationExecutor, logStackTraces);
    }

}
