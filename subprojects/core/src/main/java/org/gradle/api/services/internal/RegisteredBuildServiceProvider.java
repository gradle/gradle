/*
 * Copyright 2019 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.internal.Try;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.isolated.IsolationScheme;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

// TODO:configuration-cache - complain when used at configuration time, except when opted in to this
public class RegisteredBuildServiceProvider<T extends BuildService<P>, P extends BuildServiceParameters> extends BuildServiceProvider<T, P> {

    protected final ServiceRegistry internalServices;
    protected BuildServiceDetails<T, P> serviceDetails;

    @SuppressWarnings("rawtypes")
    private final IsolationScheme<BuildService, BuildServiceParameters> isolationScheme;
    private final InstantiationScheme instantiationScheme;
    private final IsolatableFactory isolatableFactory;
    private final Listener listener;
    private Try<T> instance;
    private boolean keepAlive;

    public RegisteredBuildServiceProvider(
        BuildIdentifier buildIdentifier,
        String name,
        Class<T> implementationType,
        @Nullable P parameters,
        @SuppressWarnings("rawtypes")
        IsolationScheme<BuildService, BuildServiceParameters> isolationScheme,
        InstantiationScheme instantiationScheme,
        IsolatableFactory isolatableFactory,
        ServiceRegistry internalServices,
        Listener listener,
        @Nullable Integer maxUsages
    ) {
        this.serviceDetails = new BuildServiceDetails<>(buildIdentifier, name, implementationType, parameters, maxUsages);
        this.internalServices = internalServices;
        this.isolationScheme = isolationScheme;
        this.instantiationScheme = instantiationScheme;
        this.isolatableFactory = isolatableFactory;
        this.listener = listener;
    }

    @Override
    public BuildServiceDetails<T, P> getServiceDetails() {
        return serviceDetails;
    }

    @Override
    public BuildIdentifier getBuildIdentifier() {
        return serviceDetails.getBuildIdentifier();
    }

    public String getName() {
        return serviceDetails.getName();
    }

    public Class<T> getImplementationType() {
        return serviceDetails.getImplementationType();
    }

    @Nullable
    public P getParameters() {
        return serviceDetails.getParameters();
    }

    @Nonnull
    @Override
    public Class<T> getType() {
        return serviceDetails.getImplementationType();
    }

    /**
     * When true, this service should be kept alive until the end of the build.
     */
    public boolean isKeepAlive() {
        return keepAlive;
    }

    public void keepAlive() {
        keepAlive = true;
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        return Value.of(getInstance());
    }

    private T getInstance() {
        listener.beforeGet(this);
        synchronized (this) {
            if (instance == null) {
                instance = instantiate();
            }
        }
        return instance.get();
    }

    private Try<T> instantiate() {
        // TODO - extract some shared infrastructure to take care of instantiation (eg which services are visible, strict vs lenient, decorated or not?)
        // TODO - should hold the project lock to do the isolation. Should work the same way as artifact transforms (a work node does the isolation, etc)
        P isolatedParameters = isolatableFactory.isolate(getParameters()).isolate();
        // TODO - reuse this in other places
        ServiceLookup instantiationServices = instantiationServicesFor(isolatedParameters);
        try {
            return Try.successful(instantiate(instantiationServices));
        } catch (Exception e) {
            return Try.failure(instantiationException(e));
        }
    }

    private ServiceLifecycleException instantiationException(Exception e) {
        return new ServiceLifecycleException("Failed to create service '" + getName() + "'.", e);
    }

    private T instantiate(ServiceLookup instantiationServices) {
        return instantiationScheme.withServices(instantiationServices).instantiator().newInstance(getImplementationType());
    }

    private ServiceLookup instantiationServicesFor(@Nullable P isolatedParameters) {
        return isolationScheme.servicesForImplementation(
            isolatedParameters,
            internalServices,
            ImmutableList.of(),
            serviceType -> false
        );
    }

    @Override
    public ExecutionTimeValue<? extends T> calculateExecutionTimeValue() {
        return ExecutionTimeValue.changingValue(this);
    }

    @Override
    public void maybeStop() {
        synchronized (this) {
            try {
                if (instance != null) {
                    instance.ifSuccessful(t -> {
                        if (t instanceof AutoCloseable) {
                            try {
                                ((AutoCloseable) t).close();
                            } catch (Exception e) {
                                throw new ServiceLifecycleException("Failed to stop service '" + getName() + "'.", e);
                            }
                        }
                    });
                }
            } finally {
                instance = null;
            }
        }
    }

    @Override
    public ProviderInternal<T> withFinalValue(ValueConsumer consumer) {
        return this;
    }

    @Override
    public boolean calculatePresence(ValueConsumer consumer) {
        return true;
    }
}
