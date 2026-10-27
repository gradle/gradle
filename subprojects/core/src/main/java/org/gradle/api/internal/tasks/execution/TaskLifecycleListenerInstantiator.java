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
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.Problems;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.api.tasks.TaskLifecycleListener;
import org.gradle.internal.Cast;
import org.gradle.internal.instantiation.InstanceFactory;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.instantiation.managed.ManagedObjectRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.UnknownServiceException;
import org.gradle.process.ExecOperations;

/**
 * Creates instances of {@link TaskLifecycleListener}s for the tasks of a project.
 *
 * <p>Listeners can only inject a curated set of services.</p>
 */
public class TaskLifecycleListenerInstantiator {

    /**
     * Services the generated listener classes need internally, which listeners cannot inject.
     */
    private static final Class<?>[] INTERNAL_SERVICES = {BuildServiceRegistry.class, ManagedObjectRegistry.class};

    private final InstantiationScheme instantiationScheme;

    public TaskLifecycleListenerInstantiator(InstantiatorFactory instantiatorFactory, ServiceRegistry projectServices) {
        this.instantiationScheme = createInstantiationScheme(instantiatorFactory, projectServices);
    }

    public TaskLifecycleListener<Task> newInstance(Class<? extends TaskLifecycleListener<?>> listenerType) {
        InstanceFactory<? extends TaskLifecycleListener<?>> factory = instantiationScheme.forType(listenerType);
        for (Class<?> internalService : INTERNAL_SERVICES) {
            if (factory.requiresService(internalService)) {
                throw new UnknownServiceException(internalService, "No service of type " + internalService.getSimpleName() + " available in task lifecycle listener services.");
            }
        }
        return Cast.uncheckedNonnullCast(factory.newInstance());
    }

    private static InstantiationScheme createInstantiationScheme(InstantiatorFactory instantiatorFactory, ServiceRegistry projectServices) {
        ServiceRegistry services = ServiceRegistryBuilder.builder()
            .displayName("task lifecycle listener services")
            .provider(registration -> {
                registration.add(ObjectFactory.class, projectServices.get(ObjectFactory.class));
                registration.add(ProviderFactory.class, projectServices.get(ProviderFactory.class));
                registration.add(FileSystemOperations.class, projectServices.get(FileSystemOperations.class));
                registration.add(ArchiveOperations.class, projectServices.get(ArchiveOperations.class));
                registration.add(ExecOperations.class, projectServices.get(ExecOperations.class));
                registration.add(ProjectLayout.class, projectServices.get(ProjectLayout.class));
                registration.add(Problems.class, projectServices.get(Problems.class));
                // Used by the generated listener classes, for @ServiceReference and managed properties
                registration.add(BuildServiceRegistry.class, projectServices.get(BuildServiceRegistry.class));
                registration.add(ManagedObjectRegistry.class, projectServices.get(ManagedObjectRegistry.class));
            })
            .build();
        // Inject only, decorating the listeners with DSL support would require more services
        return instantiatorFactory.injectScheme().withServices(services);
    }
}
