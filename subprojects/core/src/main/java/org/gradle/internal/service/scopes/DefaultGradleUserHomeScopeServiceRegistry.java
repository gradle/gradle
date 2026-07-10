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
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;

import java.io.Closeable;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.getProperty;
import static org.gradle.internal.concurrent.CompositeStoppable.stoppable;

/**
 * Reuses the services for the most recent Gradle user home dir. Could instead cache several most recent and clean these up on memory pressure, however in practise there is only a single user home dir associated with a given build process.
 */
public class DefaultGradleUserHomeScopeServiceRegistry implements GradleUserHomeScopeServiceRegistry, Closeable {
    public static final String REUSE_USER_HOME_SERVICES = "org.gradle.internal.reuse.user.home.services";
    private final ServiceRegistry sharedServices;
    private final ServiceRegistrationProvider provider;
    private final Lock lock = new ReentrantLock();
    private final Map<File, Services> servicesForHomeDir = new HashMap<>();

    public DefaultGradleUserHomeScopeServiceRegistry(ServiceRegistry sharedServices, ServiceRegistrationProvider provider) {
        this.sharedServices = sharedServices;
        this.provider = provider;
    }

    @Override
    public void close() {
        CompositeStoppable stoppable = new CompositeStoppable();
        lock.lock();
        try {
            for (Map.Entry<File, Services> entry : servicesForHomeDir.entrySet()) {
                Services services = entry.getValue();
                if (services.count != 0) {
                    throw new IllegalStateException("Services for Gradle user home directory '" + entry.getKey() + "' have not been released.");
                }
                stoppable.add(services.registry);
            }
            servicesForHomeDir.clear();
        } finally {
            lock.unlock();
        }
        stoppable.stop();
    }

    @Override
    public ServiceRegistry getServicesFor(final File gradleUserHomeDir) {
        lock.lock();
        try {
            Services services = servicesForHomeDir.get(gradleUserHomeDir);
            if (services == null) {
                if (servicesForHomeDir.size() == 1) {
                    Services otherServices = servicesForHomeDir.values().iterator().next();
                    if (otherServices.count == 0) {
                        // Other home dir cached and not in use, clean it up
                        stoppable(otherServices.registry).stop();
                        servicesForHomeDir.clear();
                    }
                }
                ServiceRegistry userHomeServices = ServiceRegistryBuilder.builder()
                    .scopeStrictly(Scope.UserHome.class)
                    .displayName("services for Gradle user home dir " + gradleUserHomeDir)
                    .parent(sharedServices)
                    .provider(new ServiceRegistrationProvider() {
                        @Provides
                        GradleUserHomeDirProvider createGradleUserHomeDirProvider() {
                            return () -> gradleUserHomeDir;
                        }
                    })
                    .provider(provider)
                    .build();
                services = new Services(userHomeServices);
                servicesForHomeDir.put(gradleUserHomeDir, services);
            }
            services.count++;
            return services.registry;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public Optional<ServiceRegistry> getCurrentServices() {
        lock.lock();
        try {
            return servicesForHomeDir.isEmpty()
                ? Optional.empty()
                : Optional.of(servicesForHomeDir.values().iterator().next().registry);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void release(ServiceRegistry registry) {
        lock.lock();
        try {
            Map.Entry<File, Services> activeService = servicesForHomeDir
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue().registry == registry && entry.getValue().count > 0)
                .findFirst()
                .orElseThrow(() ->
                    new IllegalStateException("Gradle user home directory scoped services have already been released.")
                );

            if (--activeService.getValue().count == 0 && (servicesForHomeDir.size() > 1 || !getProperty(REUSE_USER_HOME_SERVICES, "true").equals("true"))) {
                stoppable(activeService.getValue().registry).stop();
                servicesForHomeDir.remove(activeService.getKey());
            }
        } finally {
            lock.unlock();
        }
    }

    private static class Services {
        private final ServiceRegistry registry;
        private int count;

        public Services(ServiceRegistry registry) {
            this.registry = registry;
        }
    }
}
