/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.provider.DefaultProperty;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceRegistration;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultTaskRequiredServices implements TaskRequiredServices {
    private final TaskInternal task;
    private final TaskMutator taskMutator;
    private final PropertyWalker propertyWalker;
    private final BuildServiceRegistry buildServiceRegistry;
    private Set<Provider<? extends BuildService<?>>> servicesRegistered;
    public DefaultTaskRequiredServices(TaskInternal task, TaskMutator taskMutator, PropertyWalker propertyWalker, BuildServiceRegistry buildServiceRegistry) {
        this.task = task;
        this.taskMutator = taskMutator;
        this.propertyWalker = propertyWalker;
        this.buildServiceRegistry = buildServiceRegistry;
    }

    @Override
    public Set<Provider<? extends BuildService<?>>> getRequiredServices() {
        return collectServicesRequired();
    }

    @Override
    public void visitRegisteredProperties(PropertyVisitor visitor) {
        Set<Provider<? extends BuildService<?>>> tmpServicesRegistered = servicesRegistered;
        if (tmpServicesRegistered != null) {
            for (Provider<? extends BuildService<?>> next: tmpServicesRegistered) {
                visitor.visitServiceReference(next, /* no name provided for explicit registrations */ null);
            }
        }
    }

    @Override
    public boolean isServiceRequired(Provider<? extends BuildService<?>> toCheck) {
        return getRequiredServices().contains(toCheck);
    }

    /**
     * Returns both services declared as referenced (via @ServiceReference) or explicitly as used (via Task#usesService()).
     */
    private Set<Provider<? extends BuildService<?>>> collectServicesRequired() {
        final Set<Provider<? extends BuildService<?>>> servicesRequired = new LinkedHashSet<>();
        TaskPropertyUtils.visitProperties(propertyWalker, task, new PropertyVisitor.Adapter() {
            @Override
            public void visitServiceReference(Provider<? extends BuildService<?>> referenceProvider, String serviceName) {
                if (referenceProvider instanceof Property) {
                    DefaultProperty referenceAsProperty = (DefaultProperty) referenceProvider;
                    assignServiceIfNeeded(referenceAsProperty, serviceName);
                    Provider<?> propertyAsProvider = referenceAsProperty.getProvider();
                    servicesRequired.add(Cast.uncheckedCast(propertyAsProvider));
                } else {
                    servicesRequired.add(referenceProvider);
                }
            }
        });
        return servicesRequired;
    }

    @Override
    public void registerServiceUsage(Provider<? extends BuildService<?>> service) {
        taskMutator.mutate("Task.usesService(Provider)", () -> {
            if (servicesRegistered == null) {
                servicesRegistered = new HashSet<>();
            }
            // TODO:configuration-cache assert build service is from the same build as the task
            servicesRegistered.add(service);
        });
    }

    @SuppressWarnings("unchecked")
    private void assignServiceIfNeeded(DefaultProperty<?> asProperty, @Nullable String serviceName) {
        if (serviceName != null && !asProperty.getProvider().isPresent()) {
            BuildServiceRegistration<?, ?> found = buildServiceRegistry.getRegistrations().findByName(serviceName);
            if (found != null) {
                Provider resolved = found.getService();
                asProperty.set(resolved);
            }
        }
    }
}
