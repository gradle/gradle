/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.service;

import org.gradle.internal.service.scopes.Scope;

import java.util.ArrayList;
import java.util.List;

/**
 * A builder for a {@link ServiceRegistry}.
 *
 * @see ServiceRegistryBuilder#builder()
 */
public class ServiceRegistryBuilder {

    private final List<ServiceRegistry> parents = new ArrayList<ServiceRegistry>();
    private final List<ServiceRegistrationProvider> providers = new ArrayList<ServiceRegistrationProvider>();
    private String displayName;
    private Class<? extends Scope> scope;
    private boolean strict;

    private ServiceRegistryBuilder() {
    }

    /**
     * Creates a new builder.
     */
    public static ServiceRegistryBuilder builder() {
        return new ServiceRegistryBuilder();
    }

    /**
     * Sets the display name to be used by the service registry.
     * <p>
     * The display name is used for debugging and in the errors messages.
     * The errors are not user-facing and oriented at troubleshooting.
     * For instance, errors when services fail validation at registration time,
     * or when their dependencies are missing at instantiation time.
     */
    public ServiceRegistryBuilder displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /**
     * Adds a parent for the service registry.
     * <p>
     * Parent registries are used to lookup services not found in the current registry.
     * <p>
     * There can be more than one parent.
     */
    public ServiceRegistryBuilder parent(ServiceRegistry parent) {
        this.parents.add(parent);
        return this;
    }

    /**
     * Adds a service provider for the service registry.
     * <p>
     * Providers are examined for service declarations and service registration logic
     * at the time of building the registry.
     * <p>
     * There can be more than one service provider.
     *
     * @see ServiceRegistrationProvider
     */
    public ServiceRegistryBuilder provider(ServiceRegistrationProvider provider) {
        this.providers.add(provider);
        return this;
    }

    /**
     * Adds a service provider for the service registry in the form of a registration action.
     * <p>
     * The registration action is executed at the time of building the registry.
     * <p>
     * There can be more than one registration action.
     *
     * @see ServiceRegistrationAction
     */
    public ServiceRegistryBuilder provider(final ServiceRegistrationAction register) {
        return provider(new ServiceRegistrationProvider() {
            @SuppressWarnings("unused")
            void configure(ServiceRegistration registration) {
                register.registerServices(registration);
            }
        });
    }

    /**
     * Providing a scope makes the resulting {@link ServiceRegistry}
     * validate all registered services for being annotated with the given scope.
     * <p>
     * However, this still allows to register services without the
     * {@link org.gradle.internal.service.scopes.ServiceScope @ServiceScope} annotation.
     *
     * @see #scopeStrictly(Class)
     */
    public ServiceRegistryBuilder scope(Class<? extends Scope> scope) {
        this.scope = scope;
        this.strict = false;
        return this;
    }

    /**
     * Providing a scope makes the resulting {@link ServiceRegistry}
     * validate all registered services for being annotated with the given scope.
     * <p>
     * All registered services require the {@link org.gradle.internal.service.scopes.ServiceScope @ServiceScope}
     * annotation to be present and contain the given scope.
     *
     * @see #scope(Class)
     */
    public ServiceRegistryBuilder scopeStrictly(Class<? extends Scope> scope) {
        this.scope = scope;
        this.strict = true;
        return this;
    }

    /**
     * Creates a service registry with the provided configuration.
     * <p>
     * The registry <b>should be {@link CloseableServiceRegistry#close() closed}</b> when it is no longer required
     * to cleanly dispose of the resources potentially held by created services.
     *
     * @see CloseableServiceRegistry
     */
    public CloseableServiceRegistry build() {
        ServiceRegistry[] parents = this.parents.toArray(new ServiceRegistry[0]);

        DefaultServiceRegistry registry = scope != null
            ? new ScopedServiceRegistry(scope, strict, displayName, parents)
            : new DefaultServiceRegistry(displayName, parents);

        for (ServiceRegistrationProvider provider : providers) {
            registry.addProvider(provider);
        }
        return registry;
    }
}
