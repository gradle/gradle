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

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistration;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.internal.Cast;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nullable;

/**
 * A build service that is consumed.
 *
 *
 */
public class ConsumedBuildServiceProvider<T extends BuildService<P>, P extends BuildServiceParameters> extends BuildServiceProvider<T, P> {

    protected final ServiceRegistry internalServices;
    private final String serviceName;
    private final Class<T> serviceType;
    private BuildServiceProvider<T, P> serviceProvider;

    public ConsumedBuildServiceProvider(String serviceName,
                                        Class<T> serviceType,
                                        ServiceRegistry internalServices) {
        this.serviceName = serviceName;
        this.serviceType = serviceType;
        this.internalServices = internalServices;
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        return getServiceProvider().calculateValue(consumer);
    }

    private BuildServiceProvider<T, P> getServiceProvider() {
        if (serviceProvider == null) {
            BuildServiceRegistry buildServiceRegistry = internalServices.get(BuildServiceRegistry.class);
            BuildServiceRegistration<?, ?> registration = buildServiceRegistry.getRegistrations().getByName(serviceName);
            serviceProvider = Cast.uncheckedCast(registration.getService());
        }
        return serviceProvider;
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return serviceType;
    }

    public String getName() {
        return serviceName;
    }

    @Override
    public BuildServiceDetails<T, P> getServiceDetails() {
        return getServiceProvider().getServiceDetails();
    }
}
