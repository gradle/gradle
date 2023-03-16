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

package org.gradle.api.services.internal;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistration;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.internal.Cast;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * A build service that is consumed.
 */
public class ConsumedBuildServiceProvider<T extends BuildService<BuildServiceParameters>> extends BuildServiceProvider<T, BuildServiceParameters> {
    protected final ServiceRegistry internalServices;
    private final String serviceName;
    private final Class<T> serviceType;
    private final BuildIdentifier buildIdentifier;
    private volatile RegisteredBuildServiceProvider<T, BuildServiceParameters> resolvedProvider;

    public ConsumedBuildServiceProvider(
        BuildIdentifier buildIdentifier,
        String serviceName,
        Class<T> serviceType,
        ServiceRegistry internalServices
    ) {
        this.buildIdentifier = buildIdentifier;
        this.serviceName = serviceName;
        this.serviceType = serviceType;
        this.internalServices = internalServices;
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        RegisteredBuildServiceProvider<T, ?> resolvedProvider = resolve(true);
        if (resolvedProvider == null) {
            return Value.missing();
        }
        return resolvedProvider.calculateValue(consumer);
    }

    @Nullable
    public RegisteredBuildServiceProvider<T, ?> resolveIfPossible() {
        resolve(false);
        return resolvedProvider;
    }

    @Nullable
    private RegisteredBuildServiceProvider<T, BuildServiceParameters> resolve(boolean failIfAmbiguous) {
        if (resolvedProvider == null) {
            BuildServiceRegistry buildServiceRegistry = internalServices.get(BuildServiceRegistry.class);
            Set<BuildServiceRegistration<?, ?>> results = ((BuildServiceRegistryInternal) buildServiceRegistry).findRegistrations(this.getType(), this.getName());
            if (results.isEmpty()) {
                return null;
            }
            if (results.size() > 1) {
                if (!failIfAmbiguous) {
                    return null;
                }
                String names = results.stream()
                    .map(it -> it.getName() + ": " + getProvidedType(it.getService()).getTypeName())
                    .collect(Collectors.joining(", "));
                throw new IllegalArgumentException(String.format("Cannot resolve service by type for type '%s' when there are two or more instances. Please also provide a service name. Instances found: %s.", getType().getTypeName(), names));
            }
            // resolved, so remember it
            resolvedProvider = Cast.uncheckedCast(results.stream().findFirst().get().getService());
        }
        return resolvedProvider;
    }

    @Nonnull
    @Override
    public Class<T> getType() {
        return serviceType;
    }

    public String getName() {
        return serviceName;
    }

    @Override
    public BuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    @Override
    public BuildServiceDetails<T, BuildServiceParameters> getServiceDetails() {
        BuildServiceProvider<T, BuildServiceParameters> resolvedProvider = resolve(true);
        return resolvedProvider != null ? resolvedProvider.getServiceDetails() : new BuildServiceDetails<>(buildIdentifier, serviceName, serviceType);
    }

    @Override
    public ProviderInternal<T> withFinalValue(ValueConsumer consumer) {
        RegisteredBuildServiceProvider<T, BuildServiceParameters> resolved = resolve(true);
        return resolved != null ? resolved.withFinalValue(consumer) : super.withFinalValue(consumer);
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        return resolve(false) != null;
    }
}
