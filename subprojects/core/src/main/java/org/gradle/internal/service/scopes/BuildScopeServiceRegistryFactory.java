/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.service.CloseableServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;

import java.io.Closeable;

@ServiceScope(Scope.Build.class)
public class BuildScopeServiceRegistryFactory implements ServiceRegistryFactory, Closeable {
    private final ServiceRegistry services;
    private final CompositeStoppable registries = new CompositeStoppable();

    public BuildScopeServiceRegistryFactory(ServiceRegistry services) {
        this.services = services;
    }

    @Override
    public ServiceRegistry createFor(Object domainObject) {
        if (domainObject instanceof GradleInternal) {
            CloseableServiceRegistry gradleServices = ServiceRegistryBuilder.builder()
                .displayName("Gradle-scope services")
                .scope(Scope.Gradle.class)
                .parent(services)
                .provider(new GradleScopeServices())
                .build();
            registries.add(gradleServices);
            return gradleServices;
        }
        throw new IllegalArgumentException(String.format("Cannot create services for unknown domain object of type %s.", domainObject.getClass().getSimpleName()));
    }

    @Override
    public void close() {
        registries.stop();
    }
}
