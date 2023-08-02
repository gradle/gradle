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

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class DefaultTaskRequiredServices implements TaskRequiredServices {
    private final TaskInternal task;
    private final TaskMutator taskMutator;
    private final PropertyWalker propertyWalker;

    /**
     * Services registered explicitly via Task#usesService(provider).
     */
    @Nullable
    private Set<RegisteredBuildServiceProvider<?, ?>> registeredServices;

    @Nullable
    private Collection<? extends BuildServiceProvider<?, ?>> consumedServices;

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
            consumedServices.stream()
                .map(it -> ((ConsumedBuildServiceProvider<?>) it).resolveIfPossible())
                .filter(Objects::nonNull)
                .forEach(allServicesUsed::add);
        }
        ImmutableSet<BuildServiceProvider<?, ?>> build = allServicesUsed.build();
        return Cast.uncheckedCast(build);
    }

    private void collectConsumedServices() {
        GetServiceReferencesVisitor collector = new GetServiceReferencesVisitor();
        TaskPropertyUtils.visitAnnotatedProperties(propertyWalker, task, TypeValidationContext.NOOP, collector);
        // this goes through task to benefit from services a task has available
        task.acceptServiceReferences(collector.getServiceReferences());
    }

    @Override
    public boolean isServiceRequired(Provider<? extends BuildService<?>> toCheck) {
        return getElements(false).stream().anyMatch(it -> BuildServiceProvider.isSameService(toCheck, it));
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
}
