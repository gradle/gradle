/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.launcher.daemon.server.exec;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.internal.buildoption.DefaultInternalOptions;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.service.scopes.VirtualFileSystemServices;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem;
import org.gradle.launcher.daemon.protocol.Build;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Asynchronously cleans up the VFS after a build.
 *
 * Unblocks the client to receive the build finished event while the cleanup is happening.
 * However, the next build is not allowed to start until the cleanup is finished.
 */
@NonNullApi
public class CleanUpVirtualFileSystemAfterBuild extends BuildCommandOnly implements Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(CleanUpVirtualFileSystemAfterBuild.class);

    private final GradleUserHomeScopeServiceRegistry userHomeServiceRegistry;
    private final ManagedExecutor executor;

    private CompletableFuture<Void> pendingCleanup = CompletableFuture.completedFuture(null);

    public CleanUpVirtualFileSystemAfterBuild(ExecutorFactory executorFactory, GradleUserHomeScopeServiceRegistry userHomeServiceRegistry) {
        this.executor = executorFactory.create("VFS cleanup");
        this.userHomeServiceRegistry = userHomeServiceRegistry;
    }

    @Override
    protected void doBuild(DaemonCommandExecution execution, Build build) {
        waitForPendingCleanupToFinish();
        try {
            execution.proceed();
        } finally {
            startAsyncCleanupAfterBuild(build);
        }
    }

    private void startAsyncCleanupAfterBuild(Build build) {
        userHomeServiceRegistry.getCurrentServices().ifPresent(serviceRegistry ->
            pendingCleanup = CompletableFuture.runAsync(() -> {
                LOGGER.debug("Cleaning virtual file system after build finished");

                StartParameterInternal startParameter = build.getAction().getStartParameter();
                InternalOptions options = new DefaultInternalOptions(startParameter.getSystemPropertiesArgs());
                int maximumNumberOfWatchedHierarchies = VirtualFileSystemServices.getMaximumNumberOfWatchedHierarchies(options);

                BuildLifecycleAwareVirtualFileSystem virtualFileSystem = serviceRegistry.get(BuildLifecycleAwareVirtualFileSystem.class);
                virtualFileSystem.afterBuildFinished(maximumNumberOfWatchedHierarchies);
            }, executor));
    }

    private void waitForPendingCleanupToFinish() {
        if (!pendingCleanup.isDone()) {
            LOGGER.debug("Waiting for pending virtual file system cleanup to be finished");
            try {
                pendingCleanup.get();
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.error("Couldn't clean up VFS between builds, dropping content", e);
                userHomeServiceRegistry.getCurrentServices().ifPresent(serviceRegistry -> {
                    VirtualFileSystem virtualFileSystem = serviceRegistry.get(VirtualFileSystem.class);
                    virtualFileSystem.invalidateAll();
                });
            }
        }
    }

    @Override
    public void stop() {
        // If we are shutting down, it's not important to finish cleaning the VFS
        executor.shutdownNow();
    }
}
