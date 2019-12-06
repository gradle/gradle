/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.launcher.daemon.server.api;

import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.launcher.daemon.protocol.InvalidateFileSystemLocations;
import org.gradle.launcher.daemon.protocol.Success;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HandleInvalidateLocations implements DaemonCommandAction {
    private static final Logger LOGGER = LoggerFactory.getLogger(HandleInvalidateLocations.class);

    private final ServiceRegistry contextServices;

    public HandleInvalidateLocations(ServiceRegistry contextServices) {
        this.contextServices = contextServices;
    }

    @Override
    public void execute(DaemonCommandExecution execution) {
        if (execution.getCommand() instanceof InvalidateFileSystemLocations) {
            InvalidateFileSystemLocations command = (InvalidateFileSystemLocations) execution.getCommand();
            GradleUserHomeScopeServiceRegistry gradleUserHomeScopeServiceRegistry = contextServices.get(GradleUserHomeScopeServiceRegistry.class);
            gradleUserHomeScopeServiceRegistry.getCurrentServices().ifPresent(currentServices -> {
                LOGGER.info("Invalidating {}", command.getLocations());
                VirtualFileSystem virtualFileSystem = currentServices.get(VirtualFileSystem.class);
                virtualFileSystem.update(command.getLocations(), () -> {});
            });
            execution.getConnection().completed(new Success(null));
        } else {
            execution.proceed();
        }
    }
}
