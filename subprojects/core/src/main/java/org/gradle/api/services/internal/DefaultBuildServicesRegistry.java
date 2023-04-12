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
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.StringUtils;
import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.NonExtensible;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistration;
import org.gradle.api.services.BuildServiceSpec;
import org.gradle.internal.Cast;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolated.IsolationScheme;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.resources.SharedResource;
import org.gradle.internal.resources.SharedResourceLeaseRegistry;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.gradle.api.services.internal.BuildServiceProvider.asBuildServiceProvider;
import static org.gradle.internal.Cast.uncheckedCast;
import static org.gradle.internal.Cast.uncheckedNonnullCast;

public class DefaultBuildServicesRegistry implements BuildServiceRegistryInternal, HoldsProjectState {

    private final BuildIdentifier buildIdentifier;
    private final Lock registrationsLock = new ReentrantLock();
    private NamedDomainObjectSet<BuildServiceRegistration<?, ?>> registrations;
    private final DomainObjectCollectionFactory collectionFactory;
    private final InstantiatorFactory instantiatorFactory;
    private final ServiceRegistry services;
    private final IsolatableFactory isolatableFactory;
    private final SharedResourceLeaseRegistry leaseRegistry;
    private final IsolationScheme<BuildService, BuildServiceParameters> isolationScheme = new IsolationScheme<>(BuildService.class, BuildServiceParameters.class, BuildServiceParameters.None.class);
    private final Instantiator paramsInstantiator;
    private final Instantiator specInstantiator;
    private final BuildServiceProvider.Listener listener;

    public DefaultBuildServicesRegistry(
        BuildIdentifier buildIdentifier,
        DomainObjectCollectionFactory collectionFactory,
        InstantiatorFactory instantiatorFactory,
        ServiceRegistry services,
        ListenerManager listenerManager,
        IsolatableFactory isolatableFactory,
        SharedResourceLeaseRegistry leaseRegistry,
        BuildServiceProvider.Listener listener
    ) {
        this.buildIdentifier = buildIdentifier;
        this.registrations = uncheckedCast(collectionFactory.newNamedDomainObjectSet(BuildServiceRegistration.class));
        this.collectionFactory = collectionFactory;
        this.instantiatorFactory = instantiatorFactory;
        this.services = services;
        this.isolatableFactory = isolatableFactory;
        this.leaseRegistry = leaseRegistry;
        this.paramsInstantiator = instantiatorFactory.decorateScheme().withServices(services).instantiator();
        this.specInstantiator = instantiatorFactory.decorateLenientScheme().withServices(services).instantiator();
        this.listener = listener;
        listenerManager.addListener(new ServiceCleanupListener());
    }

    private <U> U withRegistrations(Function<NamedDomainObjectSet<BuildServiceRegistration<?, ?>>, U> function) {
        registrationsLock.lock();
        try {
            return function.apply(registrations);
        } finally {
            registrationsLock.unlock();
        }
    }

    @Override
    public NamedDomainObjectSet<BuildServiceRegistration<?, ?>> getRegistrations() {
        return registrations;
    }

    @Override
    public SharedResource forService(BuildServiceProvider<?, ?> service) {
        DefaultServiceRegistration<?, ?> registration = findRegistration(service.getType(), service.getName());
        if (registration == null) {
            // no corresponding service registered
            return null;
        }
        return registration.asSharedResource(() -> {
            // Prevent further changes to registration
            registration.getMaxParallelUsages().finalizeValue();
            int maxUsages = registration.getMaxParallelUsages().getOrElse(-1);

            if (maxUsages > 0) {
                leaseRegistry.registerSharedResource(registration.getName(), maxUsages);
            }
            return new ServiceBackedSharedResource(registration.getName(), maxUsages, leaseRegistry);
        });
    }

    @Nullable
    @Override
    public DefaultServiceRegistration<?, ?> findRegistration(Class<?> type, String name) {
        return uncheckedCast(!name.isEmpty() ?
            findByName(name) :
            findByType(type)
        );
    }

    @Override
    public Set<BuildServiceRegistration<?, ?>> findRegistrations(Class<?> type, String name) {
        return withRegistrations(registrations ->
            ImmutableSet.<BuildServiceRegistration<?, ?>>builder().addAll(registrations.matching(it ->
                type.isAssignableFrom(BuildServiceProvider.getProvidedType(it.getService()))
                    &&
                (StringUtils.isEmpty(name) || it.getName().equals(name))
            )).build()
        );
    }

    @Override
    @Nullable
    public BuildServiceRegistration<?, ?> findByName(String name) {
        return withRegistrations(registrations -> registrations.findByName(name));
    }

    @Nullable
    @Override
    public BuildServiceRegistration<?, ?> findByType(Class<?> type) {
        return findRegistrations(type, null).stream().findFirst().orElse(null);
    }

    @Override
    public <T extends BuildService<P>, P extends BuildServiceParameters> Provider<T> registerIfAbsent(String name, Class<T> implementationType, Action<? super BuildServiceSpec<P>> configureAction) {
        return doRegisterIfAbsent(name, implementationType, () -> {
            // TODO - extract some shared infrastructure to take care of parameter instantiation (eg strict vs lenient, which services are visible)
            P parameters = instantiateParametersOf(implementationType);

            // TODO - should defer execution of the action, to match behaviour for other container `register()` methods.
            DefaultServiceSpec<P> spec = uncheckedNonnullCast(specInstantiator.newInstance(DefaultServiceSpec.class, parameters));
            configureAction.execute(spec);
            return spec;
        });
    }

    @Override
    public BuildServiceProvider<?, ?> registerIfAbsent(String name, Class<? extends BuildService<?>> implementationType, @Nullable BuildServiceParameters parameters, int maxUsages) {
        Supplier<BuildServiceSpec<?>> buildServiceSpecSupplier = () -> {
            DefaultServiceSpec<?> spec = uncheckedNonnullCast(specInstantiator.newInstance(DefaultServiceSpec.class, parameters));
            spec.getMaxParallelUsages().set(maxUsages);
            return spec;
        };
        return doRegisterIfAbsent(name, uncheckedNonnullCast(implementationType), uncheckedNonnullCast(buildServiceSpecSupplier));
    }

    private <T extends BuildService<P>, P extends BuildServiceParameters> BuildServiceProvider<T, P> doRegisterIfAbsent(String name, Class<T> implementationType, Supplier<BuildServiceSpec<P>> specSupplier) {
        return withRegistrations(registrations -> {
            BuildServiceRegistration<?, ?> existing = registrations.findByName(name);
            if (existing != null) {
                // TODO - assert same type
                // TODO - assert same parameters
                return uncheckedNonnullCast(existing.getService());
            }
            // TODO - finalize the parameters during isolation
            // TODO - need to lock the project during isolation - should do this the same way as artifact transforms
            BuildServiceSpec<P> spec = specSupplier.get();
            return doRegister(name, implementationType, spec.getParameters(), spec.getMaxParallelUsages().getOrNull(), registrations);
        });
    }

    public List<ResourceLock> getSharedResources(Set<Provider<? extends BuildService<?>>> services) {
        if (services.isEmpty()) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<ResourceLock> locks = ImmutableList.builder();
        for (Provider<? extends BuildService<?>> service : services) {
            if (!service.isPresent()) {
                continue;
            }
            SharedResource resource = forService(asBuildServiceProvider(service));
            if (resource != null && resource.getMaxUsages() > 0) {
                locks.add(resource.getResourceLock());
            }
        }
        return locks.build();
    }

    @Nullable
    private <T extends BuildService<P>, P extends BuildServiceParameters> P instantiateParametersOf(Class<T> implementationType) {
        Class<P> parameterType = isolationScheme.parameterTypeFor(implementationType);
        return parameterType != null
            ? paramsInstantiator.newInstance(parameterType)
            : null;
    }

    @Override
    public BuildServiceProvider<?, ?> register(String name, Class<? extends BuildService<?>> implementationType, @Nullable BuildServiceParameters parameters, int maxUsages) {
        return withRegistrations(registrations -> {
            DefaultServiceRegistration<?, ?> registration = Cast.uncheckedCast(registrations.findByName(name));
            if (registration != null) {
                if (registration.provider.isKeepAlive()) {
                    // Reuse the service instance
                    return registration.provider;
                }
                throw new IllegalArgumentException(String.format("Service '%s' has already been registered.", name));
            }
            return doRegister(name, uncheckedNonnullCast(implementationType), parameters, maxUsages <= 0 ? null : maxUsages, registrations);
        });
    }

    @Override
    public BuildServiceProvider<?, ?> consume(String name, Class<? extends BuildService<?>> implementationType) {
        return doConsume(name, uncheckedCast(implementationType));
    }

    private <T extends BuildService<BuildServiceParameters>> BuildServiceProvider<T, BuildServiceParameters> doConsume(String name, Class<T> implementationType) {
        return new ConsumedBuildServiceProvider<>(buildIdentifier, name, implementationType, services);
    }

    private <T extends BuildService<P>, P extends BuildServiceParameters> BuildServiceProvider<T, P> doRegister(
        String name,
        Class<T> implementationType,
        @Nullable P parameters,
        @Nullable Integer maxParallelUsages,
        NamedDomainObjectSet<BuildServiceRegistration<?, ?>> registrations
    ) {
        RegisteredBuildServiceProvider<T, P> provider = new RegisteredBuildServiceProvider<>(
            buildIdentifier,
            name,
            implementationType,
            parameters,
            isolationScheme,
            instantiatorFactory.injectScheme(),
            isolatableFactory,
            services,
            listener,
            maxParallelUsages
        );

        DefaultServiceRegistration<T, P> registration = uncheckedNonnullCast(specInstantiator.newInstance(DefaultServiceRegistration.class, name, parameters, provider));
        registration.getMaxParallelUsages().set(maxParallelUsages);
        registrations.add(registration);

        // TODO - should stop the service after last usage (ie after the last task that uses it) instead of at the end of the build
        // TODO - should reuse service across build invocations, until the parameters change (which contradicts the previous item)
        return provider;
    }

    @Override
    public void discardAll() {
        discardAll(false);
    }

    private void discardAll(boolean forceAll) {
        withRegistrations(registrations -> {
            List<DefaultServiceRegistration<?, ?>> preserved = new ArrayList<>();
            try {
                ExecutionResult.forEach(registrations, registration -> {
                    DefaultServiceRegistration<?, ?> serviceRegistration = (DefaultServiceRegistration<?, ?>) registration;
                    // Do not stop services that are to be retained beyond configuration time (e.g. build event listeners)
                    if (forceAll || !serviceRegistration.provider.isKeepAlive()) {
                        serviceRegistration.provider.maybeStop();
                    } else {
                        preserved.add(serviceRegistration);
                    }
                }).rethrow();
            } finally {
                // Replace the entire container, rather than clear it, to discard all the service instances and because it may contain configuration actions and
                // other state that can affect the service instances when they are registered again
                this.registrations = uncheckedCast(collectionFactory.newNamedDomainObjectSet(BuildServiceRegistration.class));
            }
            this.registrations.addAll(preserved);
            return null;
        });
    }

    private static class ServiceBackedSharedResource implements SharedResource {
        private final String name;
        private final int maxUsages;
        private final SharedResourceLeaseRegistry leaseRegistry;

        public ServiceBackedSharedResource(String name, int maxUsages, SharedResourceLeaseRegistry leaseRegistry) {
            this.name = name;
            this.maxUsages = maxUsages;
            this.leaseRegistry = leaseRegistry;
        }

        @Override
        public int getMaxUsages() {
            return maxUsages;
        }

        @Override
        public ResourceLock getResourceLock() {
            return leaseRegistry.getResourceLock(name);
        }
    }

    public static abstract class DefaultServiceRegistration<T extends BuildService<P>, P extends BuildServiceParameters> implements BuildServiceRegistration<T, P> {
        private final String name;
        private final P parameters;
        private final RegisteredBuildServiceProvider<T, P> provider;
        private SharedResource resourceWrapper;

        public DefaultServiceRegistration(String name, P parameters, RegisteredBuildServiceProvider<T, P> provider) {
            this.name = name;
            this.parameters = parameters;
            this.provider = provider;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public P getParameters() {
            return parameters;
        }

        @Override
        public Provider<T> getService() {
            return provider;
        }

        public SharedResource asSharedResource(Supplier<SharedResource> factory) {
            if (resourceWrapper == null) {
                resourceWrapper = factory.get();
            }
            return resourceWrapper;
        }
    }

    @NonExtensible
    public abstract static class DefaultServiceSpec<P extends BuildServiceParameters> implements BuildServiceSpec<P> {
        private final P parameters;

        public DefaultServiceSpec(P parameters) {
            this.parameters = parameters;
        }

        @Override
        public P getParameters() {
            return parameters;
        }

        @Override
        public void parameters(Action<? super P> configureAction) {
            configureAction.execute(parameters);
        }
    }

    private class ServiceCleanupListener extends BuildAdapter {
        @SuppressWarnings("deprecation")
        @Override
        public void buildFinished(BuildResult result) {
            discardAll(true);
        }
    }
}
