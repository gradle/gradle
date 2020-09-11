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

package org.gradle.tooling.internal.provider;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.service.scopes.VirtualFileSystemServices;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem.VfsLogging;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem.WatchLogging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileSystemWatchingBuildActionRunner implements BuildActionRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemWatchingBuildActionRunner.class);

    private final BuildActionRunner delegate;

    public FileSystemWatchingBuildActionRunner(BuildActionRunner delegate) {
        this.delegate = delegate;
    }

    @Override
    public Result run(BuildAction action, BuildController buildController) {
        GradleInternal gradle = buildController.getGradle();
        StartParameterInternal startParameter = gradle.getStartParameter();
        BuildLifecycleAwareVirtualFileSystem virtualFileSystem = gradle.getServices().get(BuildLifecycleAwareVirtualFileSystem.class);
        BuildOperationRunner buildOperationRunner = gradle.getServices().get(BuildOperationRunner.class);

        boolean watchFileSystem = startParameter.isWatchFileSystem();
        VfsLogging verboseVfsLogging = startParameter.isVfsVerboseLogging()
            ? VfsLogging.VERBOSE
            : VfsLogging.NORMAL;
        WatchLogging debugWatchLogging = startParameter.isWatchFileSystemDebugLogging()
            ? WatchLogging.DEBUG
            : WatchLogging.NORMAL;

        logMessageForDeprecatedWatchFileSystemProperty(startParameter);
        logMessageForDeprecatedVfsRetentionProperty(startParameter);
        LOGGER.info("Watching the file system is {}", watchFileSystem ? "enabled" : "disabled");
        if (watchFileSystem) {
            dropVirtualFileSystemIfRequested(startParameter, virtualFileSystem);
        }
        virtualFileSystem.afterBuildStarted(watchFileSystem, verboseVfsLogging, debugWatchLogging, buildOperationRunner);
        try {
            return delegate.run(action, buildController);
        } finally {
            int maximumNumberOfWatchedHierarchies = VirtualFileSystemServices.getMaximumNumberOfWatchedHierarchies(startParameter);
            virtualFileSystem.beforeBuildFinished(watchFileSystem, verboseVfsLogging, debugWatchLogging, buildOperationRunner, maximumNumberOfWatchedHierarchies);
        }
    }

    private static void dropVirtualFileSystemIfRequested(StartParameterInternal startParameter, BuildLifecycleAwareVirtualFileSystem virtualFileSystem) {
        if (VirtualFileSystemServices.isDropVfs(startParameter)) {
            virtualFileSystem.update(VirtualFileSystem.INVALIDATE_ALL);
        }
    }

    private static void logMessageForDeprecatedWatchFileSystemProperty(StartParameterInternal startParameter) {
        if (startParameter.isWatchFileSystemUsingDeprecatedOption()) {
            @SuppressWarnings("deprecation")
            String deprecatedWatchFsProperty = StartParameterBuildOptions.DeprecatedWatchFileSystemOption.GRADLE_PROPERTY;
            DeprecationLogger.deprecateSystemProperty(deprecatedWatchFsProperty)
                .replaceWith(StartParameterBuildOptions.WatchFileSystemOption.GRADLE_PROPERTY)
                .willBeRemovedInGradle7()
                .withUserManual("gradle_daemon")
                .nagUser();
        }
    }

    private static void logMessageForDeprecatedVfsRetentionProperty(StartParameterInternal startParameter) {
        if (VirtualFileSystemServices.isDeprecatedVfsRetentionPropertyPresent(startParameter)) {
            @SuppressWarnings("deprecation")
            String deprecatedVfsRetentionEnabledProperty = VirtualFileSystemServices.DEPRECATED_VFS_RETENTION_ENABLED_PROPERTY;
            DeprecationLogger.deprecateIndirectUsage("Using the system property " + deprecatedVfsRetentionEnabledProperty + " to enable watching the file system")
                .withAdvice("Use the gradle property " + StartParameterBuildOptions.WatchFileSystemOption.GRADLE_PROPERTY + " instead.")
                .willBeRemovedInGradle7()
                .withUserManual("gradle_daemon")
                .nagUser();
        }
    }
}
