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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.properties.GetServiceReferencesVisitor;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.internal.BuildServiceProvider;
import org.gradle.api.services.internal.ConsumedBuildServiceProvider;
import org.gradle.api.services.internal.RegisteredBuildServiceProvider;
import org.gradle.internal.Cast;
import org.gradle.internal.properties.bean.PropertyWalker;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The build services used by a task, from three sources:
 *
 * <ul>
 *     <li>services registered via {@code Task#usesService(Provider)};</li>
 *     <li>services referenced by {@link org.gradle.api.services.ServiceReference} properties of the task,
 *     including those of its {@link org.gradle.api.tasks.Nested} beans. Finding these requires the values
 *     of the nested properties, which can run user code and can change during configuration,
 *     so they are collected lazily, once, when the task is about to execute;</li>
 *     <li>services referenced by {@link org.gradle.api.services.ServiceReference} properties of the task lifecycle listeners.
 *     A listener is only instantiated at execution, so its references are read from its type,
 *     without evaluating anything, when the listener is registered.</li>
 * </ul>
 *
 * <p>Referenced services are resolved by name only when the required services are queried,
 * so services can be registered after the task or listener referencing them.</p>
 *
 * <p>The references of the task are collected once and then kept.
 * Collecting the references of the listeners at the same time would miss listeners registered afterward,
 * whose services would then not be leased by the task.</p>
 */
public class DefaultTaskRequiredServices implements TaskRequiredServices {
    private final TaskInternal task;
    private final TaskMutator taskMutator;
    private final PropertyWalker propertyWalker;

    /**
     * Services registered explicitly via Task#usesService(provider).
     */
    @Nullable
    private Set<RegisteredBuildServiceProvider<?, ?>> registeredServices;

    /**
     * Services referenced via {@link org.gradle.api.services.ServiceReference} properties of the task itself.
     * Collected lazily, either when the task graph is resolved or on the first {@link #searchServices()}.
     */
    @Nullable
    private Collection<? extends BuildServiceProvider<?, ?>> consumedServices;

    /**
     * Services referenced via {@link org.gradle.api.services.ServiceReference} by the lifecycle listeners registered with the task.
     */
    @Nullable
    private List<BuildServiceProvider<?, ?>> lifecycleListenerServices;

    public DefaultTaskRequiredServices(TaskInternal task, TaskMutator taskMutator, PropertyWalker propertyWalker) {
        this.task = task;
        this.taskMutator = taskMutator;
        this.propertyWalker = propertyWalker;
    }

    @Override
    public Set<Provider<? extends BuildService<?>>> getElements() {
        return getElements(false);
    }

    @Override
    public Set<Provider<? extends BuildService<?>>> searchServices() {
        return getElements(true);
    }

    private Set<Provider<? extends BuildService<?>>> getElements(boolean search) {
        ImmutableSet.Builder<BuildServiceProvider<?, ?>> allServicesUsed = ImmutableSet.builder();
        if (registeredServices != null) {
            allServicesUsed.addAll(registeredServices);
        }
        if (search && consumedServices == null) {
            collectConsumedServices();
        }
        if (consumedServices != null) {
            addResolvedServices(consumedServices, allServicesUsed);
        }
        if (lifecycleListenerServices != null) {
            addResolvedServices(lifecycleListenerServices, allServicesUsed);
        }
        return Cast.uncheckedCast(allServicesUsed.build());
    }

    private static void addResolvedServices(
        Collection<? extends BuildServiceProvider<?, ?>> services,
        ImmutableSet.Builder<BuildServiceProvider<?, ?>> allServicesUsed
    ) {
        services.stream()
            .map(it -> ((ConsumedBuildServiceProvider<?>) it).resolveIfPossible())
            .filter(Objects::nonNull)
            .forEach(allServicesUsed::add);
    }

    private void collectConsumedServices() {
        GetServiceReferencesVisitor collector = new GetServiceReferencesVisitor();
        TaskPropertyUtils.visitAnnotatedProperties(propertyWalker, task, TypeValidationContext.NOOP, collector);
        // this goes through task to benefit from services a task has available
        task.acceptServiceReferences(collector.getServiceReferences());
    }

    @Override
    public boolean isServiceRequired(Provider<? extends BuildService<?>> toCheck) {
        return getElements().stream().anyMatch(it -> BuildServiceProvider.isSameService(toCheck, it));
    }

    @Override
    public void registerServiceUsage(Provider<? extends BuildService<?>> service) {
        taskMutator.mutate("Task.usesService(Provider)", () -> {
            // TODO:configuration-cache assert build service is from the same build as the task
            addRegisteredService(Cast.uncheckedNonnullCast(service));
        });
    }

    private void addRegisteredService(RegisteredBuildServiceProvider<?, ?> service) {
        if (registeredServices == null) {
            registeredServices = new LinkedHashSet<>();
        }
        registeredServices.add(service);
    }

    @Override
    public void acceptServiceReferences(List<? extends BuildServiceProvider<?, ?>> serviceReferences) {
        // someone already collected service references for us, just remember them
        consumedServices = serviceReferences;
    }

    @Override
    public boolean hasServiceReferences() {
        return consumedServices != null;
    }

    @Override
    public void registerLifecycleListenerServices(List<? extends BuildServiceProvider<?, ?>> services) {
        if (lifecycleListenerServices == null) {
            lifecycleListenerServices = new ArrayList<>(services.size());
        }
        lifecycleListenerServices.addAll(services);
    }
}
