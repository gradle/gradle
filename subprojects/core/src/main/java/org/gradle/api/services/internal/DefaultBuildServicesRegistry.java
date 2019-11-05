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

import com.google.common.reflect.TypeToken;
import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.provider.AbstractReadOnlyProvider;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistration;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.api.services.BuildServiceSpec;
import org.gradle.internal.Cast;
import org.gradle.internal.Try;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;

import javax.annotation.Nullable;
import java.lang.reflect.ParameterizedType;

public class DefaultBuildServicesRegistry implements BuildServiceRegistry {
    private final NamedDomainObjectSet<BuildServiceRegistration<?, ?>> registrations;
    private final InstantiatorFactory instantiatorFactory;
    private final ServiceRegistry services;
    private final ListenerManager listenerManager;

    public DefaultBuildServicesRegistry(DomainObjectCollectionFactory factory, InstantiatorFactory instantiatorFactory, ServiceRegistry services, ListenerManager listenerManager) {
        this.registrations = Cast.uncheckedCast(factory.newNamedDomainObjectSet(BuildServiceRegistration.class));
        this.instantiatorFactory = instantiatorFactory;
        this.services = services;
        this.listenerManager = listenerManager;
    }

    @Override
    public NamedDomainObjectSet<BuildServiceRegistration<?, ?>> getRegistrations() {
        return registrations;
    }

    @Override
    public <T extends BuildService<P>, P extends BuildServiceParameters> Provider<T> maybeRegister(String name, Class<T> implementationType, Action<? super BuildServiceSpec<P>> configureAction) {
        BuildServiceRegistration<?, ?> existing = registrations.findByName(name);
        if (existing != null) {
            // TODO - assert same type
            // TODO - assert same parameters
            return Cast.uncheckedCast(existing.getService());
        }

        // TODO - extract some shared infrastructure for this
        ParameterizedType superType = (ParameterizedType) TypeToken.of(implementationType).getSupertype(BuildService.class).getType();
        Class<P> parameterType = Cast.uncheckedNonnullCast(TypeToken.of(superType.getActualTypeArguments()[0]).getRawType());
        P parameters = instantiatorFactory.decorateScheme().withServices(services).instantiator().newInstance(parameterType);
        configureAction.execute(new BuildServiceSpec<P>() {
            @Override
            public P getParameters() {
                return parameters;
            }

            @Override
            public void parameters(Action<? super P> configureAction) {
                configureAction.execute(parameters);
            }
        });
        // TODO - Add BuildServiceParameters.NONE marker and skip some work when using this
        // TODO - isolate parameters
        // TODO - defer isolation of parameters until execution time
        // TODO - finalize the parameters during isolation
        SharedServiceProvider<T, P> provider = new SharedServiceProvider<>(name, implementationType, parameterType, parameters, instantiatorFactory.injectScheme());

        registrations.add(new BuildServiceRegistration<T, P>() {
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
        });

        // TODO - should stop the service after last usage (ie after the last task that uses it) instead of at the end of the build
        // TODO - should reuse service across build invocations, until the parameters change
        listenerManager.addListener(new BuildAdapter() {
            @Override
            public void buildFinished(BuildResult result) {
                provider.maybeStop();
            }
        });
        return provider;
    }

    // TODO - make this work with instant execution
    // TODO - complain when used at configuration time, except when opted in to this
    private static class SharedServiceProvider<T extends BuildService<P>, P extends BuildServiceParameters> extends AbstractReadOnlyProvider<T> {
        private final String name;
        private final Class<T> implementationType;
        private final InstantiationScheme instantiationScheme;
        private final Class<P> parametersType;
        private P parameters;
        private Try<T> instance;

        private SharedServiceProvider(String name, Class<T> implementationType, Class<P> parametersType, P parameters, InstantiationScheme instantiationScheme) {
            this.name = name;
            this.implementationType = implementationType;
            this.parametersType = parametersType;
            this.parameters = parameters;
            this.instantiationScheme = instantiationScheme;
        }

        @Nullable
        @Override
        public Class<T> getType() {
            return implementationType;
        }

        @Override
        public boolean isPresent() {
            return true;
        }

        @Nullable
        @Override
        public T getOrNull() {
            synchronized (this) {
                if (instance == null) {
                    // TODO - extract a ServiceLookup implementation to reuse
                    DefaultServiceRegistry services = new DefaultServiceRegistry();
                    services.add(parametersType, parameters);
                    try {
                        instance = Try.successful(instantiationScheme.withServices(services).instantiator().newInstance(implementationType));
                    } catch (Exception e) {
                        instance = Try.failure(new ServiceLifecycleException("Failed to create service '" + name + "'.", e));
                    }
                    parameters = null;
                }
                return instance.get();
            }
        }

        public void maybeStop() {
            synchronized (this) {
                if (instance != null) {
                    instance.ifSuccessful(t -> {
                        if (t instanceof AutoCloseable) {
                            try {
                                ((AutoCloseable) t).close();
                            } catch (Exception e) {
                                throw new ServiceLifecycleException("Failed to stop service '" + name + "'.", e);
                            }
                        }
                    });
                }
            }
        }
    }

    @Contextual
    private static class ServiceLifecycleException extends GradleException {
        public ServiceLifecycleException(String message, @Nullable Throwable cause) {
            super(message, cause);
        }
    }
}
