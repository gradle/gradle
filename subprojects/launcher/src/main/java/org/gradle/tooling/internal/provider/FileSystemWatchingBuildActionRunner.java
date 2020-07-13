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
import org.gradle.internal.service.scopes.VirtualFileSystemServices;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.internal.watch.vfs.FileSystemWatchingHandler;
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
        FileSystemWatchingHandler watchingHandler = gradle.getServices().get(FileSystemWatchingHandler.class);
        VirtualFileSystem virtualFileSystem = gradle.getServices().get(VirtualFileSystem.class);

        boolean watchFileSystem = startParameter.isWatchFileSystem();

        logMessageForDeprecatedVfsRetentionProperty(startParameter);
        if (watchFileSystem) {
            IncubationLogger.incubatingFeatureUsed("Watching the file system");
            dropVirtualFileSystemIfRequested(startParameter, virtualFileSystem);
        }
        watchingHandler.afterBuildStarted(watchFileSystem);
        try {
            return delegate.run(action, buildController);
        } finally {
            watchingHandler.beforeBuildFinished(watchFileSystem);
        }
    }

    private static void dropVirtualFileSystemIfRequested(StartParameterInternal startParameter, VirtualFileSystem virtualFileSystem) {
        if (VirtualFileSystemServices.isDropVfs(startParameter)) {
            virtualFileSystem.invalidateAll();
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
