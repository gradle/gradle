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
import com.google.errorprone.annotations.concurrent.GuardedBy;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.internal.Try;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.collect.PersistentList;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.isolated.IsolationScheme;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;

// TODO:configuration-cache - complain when used at configuration time, except when opted in to this
public class RegisteredBuildServiceProvider<T extends BuildService<P>, P extends BuildServiceParameters> extends BuildServiceProvider<T, P> {

    protected final ServiceRegistry internalServices;
    protected final BuildServiceDetails<T, P> serviceDetails;

    private final IsolationScheme<BuildService<?>, BuildServiceParameters> isolationScheme;
    private final InstantiationScheme instantiationScheme;
    private final IsolatableFactory isolatableFactory;
    private final Listener listener;

    private final Object instanceLock = new Object();
    @GuardedBy("instanceLock")
    @Nullable
    private Try<T> instance;
    /**
     * Use {@link #getStopActions()} to get the list instead of reading the field directly.
     */
    @GuardedBy("instanceLock")
    private PersistentList<Consumer<? super RegisteredBuildServiceProvider<T, P>>> stopActions = PersistentList.of();
    private boolean keepAlive;

    public RegisteredBuildServiceProvider(
        BuildIdentifier buildIdentifier,
        String name,
        Class<T> implementationType,
        @Nullable P parameters,
        IsolationScheme<BuildService<?>, BuildServiceParameters> isolationScheme,
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

    @Override
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

    /**
     * Registers a callback to be called just before the service represented by this provider is stopped.
     * The callback runs even if the service wasn't created.
     * This provider is used as a callback argument.
     * <p>
     * The service will only be stopped after completing all registered callbacks.
     *
     * @param stopAction the callback
     */
    public void beforeStopping(Consumer<? super RegisteredBuildServiceProvider<T, P>> stopAction) {
        synchronized (instanceLock) {
            stopActions = stopActions.plus(stopAction);
        }
    }

    @Override
    protected Value<? extends T> calculateOwnValue(ValueConsumer consumer) {
        return Value.of(getInstance());
    }

    private T getInstance() {
        listener.beforeGet(this);
        synchronized (instanceLock) {
            if (instance == null) {
                instance = instantiate();
            }
            return instance.get();
        }
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

    private Iterable<Consumer<? super RegisteredBuildServiceProvider<T, P>>> getStopActions() {
        synchronized (instanceLock) {
            return stopActions;
        }
    }

    @Override
    public void maybeStop() {
        // We don't want to call stop actions with the lock held.
        ExecutionResult<Void> stopResult = ExecutionResult.forEach(getStopActions(), action -> action.accept(this));
        synchronized (instanceLock) {
            try {
                if (instance != null) {
                    instance.ifSuccessful(t -> {
                        if (t instanceof AutoCloseable) {
                            try {
                                ((AutoCloseable) t).close();
                            } catch (Exception e) {
                                ServiceLifecycleException failure = new ServiceLifecycleException("Failed to stop service '" + getName() + "'.", e);
                                stopResult.getFailures().forEach(failure::addSuppressed);
                                throw failure;
                            }
                        }
                    });
                }
                stopResult.rethrow();
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
