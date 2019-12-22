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

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.NonExtensible;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistration;
import org.gradle.api.services.BuildServiceSpec;
import org.gradle.internal.Cast;
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
import java.util.function.Supplier;

public class DefaultBuildServicesRegistry implements BuildServiceRegistryInternal {
    private final NamedDomainObjectSet<BuildServiceRegistration<?, ?>> registrations;
    private final InstantiatorFactory instantiatorFactory;
    private final ServiceRegistry services;
    private final ListenerManager listenerManager;
    private final IsolatableFactory isolatableFactory;
    private final SharedResourceLeaseRegistry leaseRegistry;
    private final IsolationScheme<BuildService, BuildServiceParameters> isolationScheme = new IsolationScheme<>(BuildService.class, BuildServiceParameters.class, BuildServiceParameters.None.class);
    private final Instantiator paramsInstantiator;
    private final Instantiator specInstantiator;

    public DefaultBuildServicesRegistry(DomainObjectCollectionFactory factory, InstantiatorFactory instantiatorFactory, ServiceRegistry services, ListenerManager listenerManager, IsolatableFactory isolatableFactory, SharedResourceLeaseRegistry leaseRegistry) {
        this.registrations = Cast.uncheckedCast(factory.newNamedDomainObjectSet(BuildServiceRegistration.class));
        this.instantiatorFactory = instantiatorFactory;
        this.services = services;
        this.listenerManager = listenerManager;
        this.isolatableFactory = isolatableFactory;
        this.leaseRegistry = leaseRegistry;
        this.paramsInstantiator = instantiatorFactory.decorateScheme().withServices(services).instantiator();
        this.specInstantiator = instantiatorFactory.decorateLenientScheme().withServices(services).instantiator();
    }

    @Override
    public NamedDomainObjectSet<BuildServiceRegistration<?, ?>> getRegistrations() {
        return registrations;
    }

    @Override
    public SharedResource forService(Provider<? extends BuildService<?>> service) {
        if (!(service instanceof BuildServiceProvider)) {
            throw new IllegalArgumentException("The given provider is not a build service provider.");
        }
        BuildServiceProvider<?, ?> provider = (BuildServiceProvider<?, ?>) service;
        DefaultServiceRegistration<?, ?> registration = (DefaultServiceRegistration<?, ?>) registrations.getByName(provider.getName());
        return registration.asSharedResource(() -> {
            // Prevent further changes to registration
            registration.getMaxParallelUsages().finalizeValue();
            int maxUsages = registration.getMaxParallelUsages().getOrElse(-1);

            if (maxUsages > 0) {
                leaseRegistry.registerSharedResource(provider.getName(), maxUsages);
            }
            return new ServiceBackedSharedResource(provider.getName(), maxUsages, leaseRegistry);
        });
    }

    @Override
    public <T extends BuildService<P>, P extends BuildServiceParameters> Provider<T> registerIfAbsent(String name, Class<T> implementationType, Action<? super BuildServiceSpec<P>> configureAction) {
        BuildServiceRegistration<?, ?> existing = registrations.findByName(name);
        if (existing != null) {
            // TODO - assert same type
            // TODO - assert same parameters
            return Cast.uncheckedCast(existing.getService());
        }

        // TODO - extract some shared infrastructure to take care of parameter instantation (eg strict vs lenient, which services are visible)
        Class<P> parameterType = isolationScheme.parameterTypeFor(implementationType);
        P parameters;
        if (parameterType != null) {
            parameters = paramsInstantiator.newInstance(parameterType);
        } else {
            // TODO - should either provider a non-null empty parameters in this case or fail whenever the parameters are queried in the service, the spec and the registration
            parameters = null;
        }

        // TODO - should defer execution of the action, to match behaviour for other container `register()` methods.

        DefaultServiceSpec<P> spec = specInstantiator.newInstance(DefaultServiceSpec.class, parameters);
        configureAction.execute(spec);
        Integer maxParallelUsages = spec.getMaxParallelUsages().getOrNull();

        // TODO - finalize the parameters during isolation
        // TODO - need to lock the project during isolation - should do this the same way as artifact transforms
        return doRegister(name, implementationType, parameterType, parameters, maxParallelUsages);
    }

    @Override
    public BuildServiceProvider<?, ?> register(String name, Class<? extends BuildService> implementationType, BuildServiceParameters parameters, int maxUsages) {
        if (registrations.findByName(name) != null) {
            throw new IllegalArgumentException(String.format("Service '%s' has already been registered.", name));
        }
        return doRegister(name, implementationType, isolationScheme.parameterTypeFor(implementationType), parameters, maxUsages <= 0 ? null : maxUsages);
    }

    private <T extends BuildService<P>, P extends BuildServiceParameters> BuildServiceProvider<T, P> doRegister(String name, Class<T> implementationType, Class<P> parameterType, P parameters, @Nullable Integer maxParallelUsages) {
        BuildServiceProvider<T, P> provider = new BuildServiceProvider<>(name, implementationType, parameters, isolationScheme, instantiatorFactory.injectScheme(), isolatableFactory, services);

        DefaultServiceRegistration<T, P> registration = specInstantiator.newInstance(DefaultServiceRegistration.class, name, parameters, provider);
        registration.getMaxParallelUsages().set(maxParallelUsages);
        registrations.add(registration);

        // TODO - should stop the service after last usage (ie after the last task that uses it) instead of at the end of the build
        // TODO - should reuse service across build invocations, until the parameters change (which contradicts the previous item)
        listenerManager.addListener(new ServiceCleanupListener(provider));
        return provider;
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
        public ResourceLock getResourceLock(int usages) {
            return leaseRegistry.getResourceLock(name, usages);
        }
    }

    public static abstract class DefaultServiceRegistration<T extends BuildService<P>, P extends BuildServiceParameters> implements BuildServiceRegistration<T, P> {
        private final String name;
        private final P parameters;
        private final BuildServiceProvider<T, P> provider;
        private SharedResource resourceWrapper;

        public DefaultServiceRegistration(String name, P parameters, BuildServiceProvider<T, P> provider) {
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

    private static class ServiceCleanupListener extends BuildAdapter {
        private final BuildServiceProvider<?, ?> provider;

        ServiceCleanupListener(BuildServiceProvider<?, ?> provider) {
            this.provider = provider;
        }

        @Override
        public void buildFinished(BuildResult result) {
            provider.maybeStop();
        }
    }
}
