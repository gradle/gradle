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

package org.gradle.launcher.daemon.server;

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.internal.buildoption.DefaultInternalOptions;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.service.scopes.VirtualFileSystemServices;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem;
import org.gradle.launcher.daemon.protocol.Build;
import org.gradle.launcher.daemon.server.api.DaemonCommandExecution;
import org.gradle.launcher.daemon.server.exec.BuildCommandOnly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullApi
public class CleanUpVirtualFileSystemAfterBuild extends BuildCommandOnly {
    private static final Logger LOGGER = LoggerFactory.getLogger(CleanUpVirtualFileSystemAfterBuild.class);
    private final GradleUserHomeScopeServiceRegistry gradleUserHomeScopeServiceRegistry;

    public CleanUpVirtualFileSystemAfterBuild(GradleUserHomeScopeServiceRegistry gradleUserHomeScopeServiceRegistry) {
        this.gradleUserHomeScopeServiceRegistry = gradleUserHomeScopeServiceRegistry;
    }

    @Override
    protected void doBuild(DaemonCommandExecution execution, Build build) {
        // TODO After adding this command, we are now getting a message in the daemon log after the build finishes:
        //      2024-01-23T14:59:21.988+0100 [WARN] [org.gradle.launcher.daemon.server.DefaultIncomingConnectionHandler] Timed out waiting for finished message from client socket connection from /127.0.0.1:64930 to /127.0.0.1:64931. Discarding connection.
        try {
            execution.proceed();
        } finally {
            gradleUserHomeScopeServiceRegistry.getCurrentServices().ifPresent(serviceRegistry -> {
                StartParameterInternal startParameter = build.getAction().getStartParameter();
                InternalOptions options = new DefaultInternalOptions(startParameter.getSystemPropertiesArgs());
                int maximumNumberOfWatchedHierarchies = VirtualFileSystemServices.getMaximumNumberOfWatchedHierarchies(options);

                LOGGER.debug("Cleaning virtual file system after build finished, allowed number of watched hierarchies: {}", maximumNumberOfWatchedHierarchies);
                BuildLifecycleAwareVirtualFileSystem virtualFileSystem = serviceRegistry.get(BuildLifecycleAwareVirtualFileSystem.class);
                virtualFileSystem.afterBuildFinished(maximumNumberOfWatchedHierarchies);
            });
        }
    }
}
