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

package org.gradle.internal.service.scopes;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.initialization.StartParameterBuildOptions;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.watch.vfs.WatchingAwareVirtualFileSystem;
import org.gradle.util.IncubationLogger;

class VirtualFileSystemBuildLifecycleListener implements RootBuildLifecycleListener {

    private final WatchingAwareVirtualFileSystem virtualFileSystem;

    public VirtualFileSystemBuildLifecycleListener(WatchingAwareVirtualFileSystem virtualFileSystem) {
        this.virtualFileSystem = virtualFileSystem;
    }

    @Override
    public void afterStart(GradleInternal gradle) {
        StartParameterInternal startParameter = (StartParameterInternal) gradle.getStartParameter();
        if (VirtualFileSystemServices.isDeprecatedVfsRetentionPropertyPresent(startParameter)) {
            @SuppressWarnings("deprecation")
            String deprecatedVfsRetentionEnabledProperty = VirtualFileSystemServices.DEPRECATED_VFS_RETENTION_ENABLED_PROPERTY;
            DeprecationLogger.deprecateIndirectUsage("Using the system property " + deprecatedVfsRetentionEnabledProperty + " to enable watching the file system")
                .withAdvice("Use the gradle property " + StartParameterBuildOptions.WatchFileSystemOption.GRADLE_PROPERTY + " instead.")
                .willBeRemovedInGradle7()
                .withUserManual("gradle_daemon")
                .nagUser();
        }
        boolean watchFileSystem = startParameter.isWatchFileSystem();
        if (watchFileSystem) {
            IncubationLogger.incubatingFeatureUsed("Watching the file system");
            if (VirtualFileSystemServices.isDropVfs(startParameter)) {
                virtualFileSystem.invalidateAll();
            }
        }
        virtualFileSystem.afterBuildStarted(watchFileSystem);
    }

    @Override
    public void beforeComplete(GradleInternal gradle) {
        virtualFileSystem.beforeBuildFinished(((StartParameterInternal) gradle.getStartParameter()).isWatchFileSystem());
    }
}
