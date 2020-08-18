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
import org.gradle.util.IncubationLogger;

public class FileSystemWatchingBuildActionRunner implements BuildActionRunner {
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

        logMessageForDeprecatedVfsRetentionProperty(startParameter);
        if (watchFileSystem) {
            IncubationLogger.incubatingFeatureUsed("Watching the file system");
            dropVirtualFileSystemIfRequested(startParameter, virtualFileSystem);
        }
        virtualFileSystem.afterBuildStarted(watchFileSystem, buildOperationRunner);
        try {
            return delegate.run(action, buildController);
        } finally {
            int maximumNumberOfWatchedHierarchies = VirtualFileSystemServices.getMaximumNumberOfWatchedHierarchies(startParameter);
            virtualFileSystem.beforeBuildFinished(watchFileSystem, buildOperationRunner, maximumNumberOfWatchedHierarchies);
        }
    }

    private static void dropVirtualFileSystemIfRequested(StartParameterInternal startParameter, BuildLifecycleAwareVirtualFileSystem virtualFileSystem) {
        if (VirtualFileSystemServices.isDropVfs(startParameter)) {
            virtualFileSystem.update(VirtualFileSystem.INVALIDATE_ALL);
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
