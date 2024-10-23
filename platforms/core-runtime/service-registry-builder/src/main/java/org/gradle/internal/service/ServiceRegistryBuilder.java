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
 * <h2>Service lookup order</h2>
 *
 * How the service registry is built affects the lookup results in that registry.
 * <p>
 * <b>Own services</b> of a registry are services contributed by the {@link #provider(ServiceRegistrationProvider) service providers}.
 * <p>
 * <b>All services</b> of a registry are its own services and <em>all services</em> of its {@link #parent(ServiceRegistry) parents}.
 * <p>
 * The lookup order is the following:
 * <ol>
 * <li> Own services of the current registry
 * <li> All services of the first parent
 * <li> All services of the second parent
 * <li> ...
 * </ol>
 *
 * The lookup result in the <em>own services</em> does not depend on the order in which service providers were added.
 * <p>
 * If any service type is registered more than once within <em>own services</em>,
 * it will result in an ambiguity error <em>at the lookup time</em>.
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
     * The display name is used for debugging and internal purposes.
     */
    public ServiceRegistryBuilder displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /**
     * Adds a parent for the service registry.
     * <p>
     * There can be more than one parent and the order of parents <b>affects</b> the {@link ServiceRegistryBuilder lookup results}.
     */
    public ServiceRegistryBuilder parent(ServiceRegistry parent) {
        this.parents.add(parent);
        return this;
    }

    /**
     * Adds a service provider for the service registry.
     * <p>
     * There can be more than one provider and the order of service providers
     * does not affect {@link ServiceRegistryBuilder lookup results}.
     * <p>
     * Providers are examined for service declarations and service registration logic at the time of building the registry.
     * This implies that the {@code configure} methods will be executed before the registry is built.
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
     * There can be more than one registration action and the order in which they register services
     * does not affect {@link ServiceRegistryBuilder lookup results}.
     * <p>
     * The registration action is executed at the time of building the registry.
     *
     * @see #provider(ServiceRegistrationProvider)
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
     * <p>
     * Only one scope can be specified. The last configured scope takes effect.
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
     * <p>
     * Only one scope can be specified. The last configured scope takes effect.
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
