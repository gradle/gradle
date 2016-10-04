/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;

import java.io.Closeable;
import java.io.File;

/**
 * Reuses the services for the most recent Gradle user home dir. Could instead cache several most recent and clean these up on memory pressure, however in practise there is only a single user home dir associated with a given build process.
 */
public class DefaultGradleUserHomeScopeServiceRegistry implements GradleUserHomeScopeServiceRegistry, Closeable {
    private final ServiceRegistry sharedServices;
    private final Object provider;
    private File previousUserHomeDir;
    private ServiceRegistry userHomeServices;
    private boolean inUse;

    public DefaultGradleUserHomeScopeServiceRegistry(ServiceRegistry sharedServices, Object provider) {
        this.sharedServices = sharedServices;
        this.provider = provider;
    }

    @Override
    public void close() {
        assertNotInUse();
        if (userHomeServices != null) {
            closePreviousServices();
        }
    }

    @Override
    public ServiceRegistry getServicesFor(final File gradleUserHomeDir) {
        assertNotInUse();
        if (!gradleUserHomeDir.equals(previousUserHomeDir)) {
            if (previousUserHomeDir != null) {
                closePreviousServices();
            }
            userHomeServices = ServiceRegistryBuilder.builder()
                .parent(sharedServices)
                .displayName("shared services for Gradle user home dir " + gradleUserHomeDir)
                .provider(new Object() {
                    GradleUserHomeDirProvider createGradleUserHomeDirProvider() {
                        return new GradleUserHomeDirProvider() {
                            @Override
                            public File getGradleUserHomeDirectory() {
                                return gradleUserHomeDir;
                            }
                        };
                    }
                })
                .provider(provider)
                .build();
            previousUserHomeDir = gradleUserHomeDir;
        }
        inUse = true;
        return userHomeServices;
    }

    @Override
    public void release(ServiceRegistry services) {
        if (!inUse) {
            throw new IllegalStateException("Gradle user home directory scoped services have already been released.");
        }
        inUse = false;
    }

    private void assertNotInUse() {
        if (inUse) {
            throw new IllegalStateException("Gradle user home directory scoped services have not been released.");
        }
    }

    private void closePreviousServices() {
        CompositeStoppable.stoppable(userHomeServices).stop();
    }
}
