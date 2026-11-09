/*
 * Copyright 2024 the original author or authors.
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

/**
 * A {@link ServiceRegistry} that manages the lifecycle of its services.
 * <p>
 * Registries are created with a {@link ServiceRegistryBuilder},
 * and their services are declared by {@link ServiceRegistrationProvider}s.
 *
 * <h2>Service lifecycle</h2>
 *
 * Services are created lazily
 * and might not be instantiated at all during the lifetime of the registry.
 * <p>
 * The registry owns all of its service instances,
 * including instances registered ready-made rather than created by the registry.
 * They are disposed of when the registry is {@link #close() closed}.
 * <p>
 * The registry should be closed when it is no longer required,
 * so that resources held by its services are released.
 */
public interface ManagedServiceRegistry extends ServiceRegistry, AutoCloseable {

    /**
     * Closes this registry and disposes of its service instances.
     * <p>
     * Services that were never instantiated are not created on close.
     * Services of parent and child registries are left as they are.
     * <p>
     * An instance that implements {@link org.gradle.internal.concurrent.Stoppable Stoppable}
     * is {@link org.gradle.internal.concurrent.Stoppable#stop() stopped}.
     * Otherwise, an instance that implements {@link AutoCloseable}
     * is {@link AutoCloseable#close() closed}.
     * Any other instance is left as it is.
     * <p>
     * Instances are disposed of in reverse dependency order:
     * a service is disposed of only after the services of this registry that depend on it.
     * Disposal continues past failures,
     * and the failures are rethrown once all instances have been processed.
     * <p>
     * Once closed, the registry can no longer be used to look up services.
     * Closing it again has no effect.
     */
    @Override
    void close();
}
