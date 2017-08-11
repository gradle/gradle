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
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.caching.BuildCacheKey;
import org.gradle.caching.BuildCacheService;
import org.gradle.caching.internal.controller.operations.PackOperationDetails;
import org.gradle.caching.internal.controller.operations.PackOperationResult;
import org.gradle.caching.internal.controller.operations.UnpackOperationDetails;
import org.gradle.caching.internal.controller.operations.UnpackOperationResult;
import org.gradle.caching.internal.controller.service.BaseBuildCacheServiceHandle;
import org.gradle.caching.internal.controller.service.BuildCacheServiceHandle;
import org.gradle.caching.internal.controller.service.BuildCacheServiceRole;
import org.gradle.caching.internal.controller.service.BuildCacheServicesConfiguration;
import org.gradle.caching.internal.controller.service.DefaultLocalBuildCacheServiceHandle;
import org.gradle.caching.internal.controller.service.LoadTarget;
import org.gradle.caching.internal.controller.service.LocalBuildCacheServiceHandle;
import org.gradle.caching.internal.controller.service.NullBuildCacheServiceHandle;
import org.gradle.caching.internal.controller.service.NullLocalBuildCacheServiceHandle;
import org.gradle.caching.internal.controller.service.OpFiringBuildCacheServiceHandle;
import org.gradle.caching.internal.controller.service.StoreTarget;
import org.gradle.caching.local.internal.BuildCacheTempFileStore;
import org.gradle.caching.local.internal.DefaultBuildCacheTempFileStore;
import org.gradle.caching.local.internal.LocalBuildCacheService;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class DefaultBuildCacheController implements BuildCacheController {

    @VisibleForTesting
    final BuildCacheServiceHandle legacyLocal;

    @VisibleForTesting
    final BuildCacheServiceHandle remote;

    @VisibleForTesting
    final LocalBuildCacheServiceHandle local;

    private final BuildCacheTempFileStore tmp;
    private final BuildOperationExecutor buildOperationExecutor;

    private boolean closed;

    public DefaultBuildCacheController(
        BuildCacheServicesConfiguration config,
        BuildOperationExecutor buildOperationExecutor,
        File gradleUserHomeDir,
        boolean logStackTraces
    ) {
        this.buildOperationExecutor = buildOperationExecutor;

        if (config.local instanceof LocalBuildCacheService) {
            LocalBuildCacheService castLocal = (LocalBuildCacheService) config.local;
            this.local = toHandle(castLocal, config.localPush);
            this.tmp = castLocal;
            this.legacyLocal = NullBuildCacheServiceHandle.INSTANCE;
        } else {
            this.local = NullLocalBuildCacheServiceHandle.INSTANCE;
            this.legacyLocal = toHandle(config.local, config.localPush, BuildCacheServiceRole.LOCAL, buildOperationExecutor, logStackTraces);
            this.tmp = new DefaultBuildCacheTempFileStore(new File(gradleUserHomeDir, "build-cache-tmp"));
        }

        this.remote = toHandle(config.remote, config.remotePush, BuildCacheServiceRole.REMOTE, buildOperationExecutor, logStackTraces);
    }

    @Nullable
    @Override
    public <T> T load(final BuildCacheLoadCommand<T> command) {
        final Unpack<T> unpack = new Unpack<T>(command);

        if (local.canLoad()) {
            try {
                local.load(command.getKey(), unpack);
            } catch (Exception e) {
                throw new GradleException("Build cache entry " + command.getKey() + " from local build cache is invalid", e);
            }

            if (unpack.result != null) {
                return unpack.result.getMetadata();
            }
        }

        if (legacyLocal.canLoad() || remote.canLoad()) {
            tmp.allocateTempFile(command.getKey(), new Action<File>() {
                @Override
                public void execute(File file) {
                    LoadTarget loadTarget = new LoadTarget(file);
                    BuildCacheServiceRole loadedRole = null;
                    if (legacyLocal.canLoad()) {
                        loadedRole = BuildCacheServiceRole.LOCAL;
                        legacyLocal.load(command.getKey(), loadTarget);
                    }

                    if (remote.canLoad() && !loadTarget.isLoaded()) {
                        loadedRole = BuildCacheServiceRole.REMOTE;
                        remote.load(command.getKey(), loadTarget);
                    }

                    if (loadTarget.isLoaded()) {
                        try {
                            unpack.execute(file);
                        } catch (Exception e) {
                            @SuppressWarnings("ConstantConditions") String roleDisplayName = loadedRole.getDisplayName();
                            throw new GradleException("Build cache entry " + command.getKey() + " from " + roleDisplayName + " build cache is invalid", e);
                        }
                        if (local.canStore()) {
                            local.store(command.getKey(), file);
                        }
                    }
                }
            });
        }

        BuildCacheLoadCommand.Result<T> result = unpack.result;
        if (result == null) {
            return null;
        } else {
            return result.getMetadata();
        }
    }

    private class Unpack<T> implements Action<File> {
        private final BuildCacheLoadCommand<T> command;

        private BuildCacheLoadCommand.Result<T> result;

        private Unpack(BuildCacheLoadCommand<T> command) {
            this.command = command;
        }

        @Override
        public void execute(final File file) {
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    InputStream input;
                    try {
                        input = new FileInputStream(file);
                    } catch (FileNotFoundException e) {
                        throw new UncheckedIOException(e);
                    }

                    try {
                        result = command.load(input);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    } finally {
                        IOUtils.closeQuietly(input);
                    }

                    context.setResult(new UnpackOperationResult(
                        result.getArtifactEntryCount()
                    ));
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Unpack build cache entry " + command.getKey())
                        .details(new UnpackOperationDetails(command.getKey(), file.length()))
                        .progressDisplayName("Unpacking build cache entry");
                }
            });
        }

    }

    @Override
    public void store(final BuildCacheStoreCommand command) {
        boolean anyStore = local.canStore() || legacyLocal.canStore() || remote.canStore();
        if (!anyStore) {
            return;
        }

        final BuildCacheKey key = command.getKey();
        final Pack pack = new Pack(command);

        tmp.allocateTempFile(command.getKey(), new Action<File>() {
            @Override
            public void execute(File file) {
                pack.execute(file);

                if (legacyLocal.canStore()) {
                    legacyLocal.store(key, new StoreTarget(file));
                }

                if (remote.canStore()) {
                    remote.store(key, new StoreTarget(file));
                }

                if (local.canStore()) {
                    local.store(key, file);
                }
            }
        });
    }

    private class Pack implements Action<File> {

        private final BuildCacheStoreCommand command;

        private Pack(BuildCacheStoreCommand command) {
            this.command = command;
        }

        @Override
        public void execute(final File file) {
            buildOperationExecutor.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    try {
                        BuildCacheStoreCommand.Result result = command.store(new FileOutputStream(file));
                        context.setResult(new PackOperationResult(
                            result.getArtifactEntryCount(),
                            file.length()
                        ));
                    } catch (IOException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Pack build cache entry " + command.getKey())
                        .details(new PackOperationDetails(command.getKey()))
                        .progressDisplayName("Packing build cache entry");
                }
            });
        }
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            CompositeStoppable.stoppable(legacyLocal, local, remote).stop();
        }
    }

    private static BuildCacheServiceHandle toHandle(BuildCacheService service, boolean push, BuildCacheServiceRole role, BuildOperationExecutor buildOperationExecutor, boolean logStackTraces) {
        return service == null
            ? NullBuildCacheServiceHandle.INSTANCE
            : toNonNullHandle(service, push, role, buildOperationExecutor, logStackTraces);
    }

    private static BuildCacheServiceHandle toNonNullHandle(BuildCacheService service, boolean push, BuildCacheServiceRole role, BuildOperationExecutor buildOperationExecutor, boolean logStackTraces) {
        if (role == BuildCacheServiceRole.LOCAL) {
            return new BaseBuildCacheServiceHandle(service, push, role, logStackTraces);
        } else {
            return new OpFiringBuildCacheServiceHandle(service, push, role, buildOperationExecutor, logStackTraces);
        }
    }

    private static LocalBuildCacheServiceHandle toHandle(LocalBuildCacheService local, boolean localPush) {
        if (local == null) {
            return NullLocalBuildCacheServiceHandle.INSTANCE;
        } else {
            return new DefaultLocalBuildCacheServiceHandle(local, localPush);
        }
    }

}
