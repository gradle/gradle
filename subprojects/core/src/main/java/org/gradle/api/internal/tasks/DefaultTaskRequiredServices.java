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
import org.gradle.api.internal.tasks.properties.PropertyValue;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.internal.BuildServiceProvider;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import static java.util.Collections.emptySet;

public class DefaultTaskRequiredServices implements TaskRequiredServices {
    private final TaskInternal task;
    private final TaskMutator taskMutator;
    private final PropertyWalker propertyWalker;

    /**
     * Services registered explicitly via Task#usesService(provider).
     */
    @Nullable
    private Set<Provider<? extends BuildService<?>>> registeredServices;
    /**
     * Lazy union between #registeredServices and properties annotated with @ServiceReference.
     */
    @Nullable
    private Set<Provider<? extends BuildService<?>>> requiredServices;

    public DefaultTaskRequiredServices(TaskInternal task, TaskMutator taskMutator, PropertyWalker propertyWalker) {
        this.task = task;
        this.taskMutator = taskMutator;
        this.propertyWalker = propertyWalker;
    }

    @Override
    public Set<Provider<? extends BuildService<?>>> getElements() {
        if (requiredServices == null) {
            requiredServices = collectRequiredServices();
        }
        return requiredServices;
    }

    @Override
    public boolean isServiceRequired(Provider<? extends BuildService<?>> toCheck) {
        return getElements().stream().anyMatch(it -> BuildServiceProvider.isSameService(it, toCheck));
    }

    /**
     * Returns both services declared as referenced (via @ServiceReference) or explicitly as used (via Task#usesService()).
     */
    private Set<Provider<? extends BuildService<?>>> collectRequiredServices() {
        Set<Provider<? extends BuildService<?>>> registeredServices = this.registeredServices != null ? this.registeredServices : emptySet();
        Set<Provider<? extends BuildService<?>>> requiredServices = new LinkedHashSet<>(registeredServices);
        visitServiceReferences(referenceProvider ->
            requiredServices.add(asBuildServiceProvider(referenceProvider))
        );
        return requiredServices;
    }

    private Provider<? extends BuildService<?>> asBuildServiceProvider(Provider<? extends BuildService<?>> referenceProvider) {
        if (referenceProvider instanceof DefaultProperty) {
            DefaultProperty<?> asProperty = Cast.uncheckedNonnullCast(referenceProvider);
            return Cast.uncheckedNonnullCast(asProperty.getProvider());
        }
        return referenceProvider;
    }

    private void visitServiceReferences(Consumer<Provider<? extends BuildService<?>>> visitor) {
        TaskPropertyUtils.visitProperties(propertyWalker, task, new PropertyVisitor.Adapter() {
            @Override
            public void visitServiceReference(String propertyName, boolean optional, PropertyValue value, @Nullable String serviceName) {
                visitor.accept(Cast.uncheckedCast(value.call()));
            }
        });
    }

    @Override
    public void registerServiceUsage(Provider<? extends BuildService<?>> service) {
        taskMutator.mutate("Task.usesService(Provider)", () -> {
            if (registeredServices == null) {
                registeredServices = new HashSet<>();
            }
            // TODO:configuration-cache assert build service is from the same build as the task
            registeredServices.add(service);
        });
    }
}
