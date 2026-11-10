/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.execution;

import org.gradle.api.Task;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.api.tasks.TaskLifecycleListener;
import org.gradle.internal.Cast;
import org.gradle.internal.instantiation.InstanceFactory;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.instantiation.managed.ManagedObjectRegistry;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates instances of {@link TaskLifecycleListener}s for the tasks of a project.
 *
 * <p>Listeners can only inject {@link TaskLifecycleListenerClassInfoStore#INJECTABLE_SERVICES a curated set of services}.</p>
 */
@ServiceScope(Scope.ProjectExecution.class)
public class TaskLifecycleListenerInstantiator {

    private final InstantiationScheme instantiationScheme;
    private final Map<Class<?>, InstanceFactory<?>> instanceFactories = new ConcurrentHashMap<>();

    public TaskLifecycleListenerInstantiator(InstantiatorFactory instantiatorFactory, ServiceRegistry projectServices) {
        this.instantiationScheme = createInstantiationScheme(instantiatorFactory, projectServices);
    }

    public TaskLifecycleListener<Task> newInstance(Class<? extends TaskLifecycleListener<?>> listenerType) {
        InstanceFactory<?> instanceFactory = instanceFactories.computeIfAbsent(listenerType, instantiationScheme::forType);
        return Cast.uncheckedNonnullCast(instanceFactory.newInstance());
    }

    private static InstantiationScheme createInstantiationScheme(InstantiatorFactory instantiatorFactory, ServiceRegistry projectServices) {
        ServiceRegistry services = ServiceRegistryBuilder.builder()
            .displayName("task lifecycle listener services")
            .provider(registration -> {
                for (Class<?> serviceType : TaskLifecycleListenerClassInfoStore.INJECTABLE_SERVICES) {
                    addProjectService(registration, projectServices, serviceType);
                }
                // Used by the generated listener classes, for @ServiceReference and managed properties
                addProjectService(registration, projectServices, BuildServiceRegistry.class);
                addProjectService(registration, projectServices, ManagedObjectRegistry.class);
            })
            .build();
        // Inject only, decorating the listeners with DSL support would require more services
        return instantiatorFactory.injectScheme().withServices(services);
    }

    private static <T> void addProjectService(ServiceRegistration registration, ServiceRegistry projectServices, Class<T> serviceType) {
        registration.add(serviceType, projectServices.get(serviceType));
    }
}
