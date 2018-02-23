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

/**
 * Allows services to be added to a registry.
 */
public interface ServiceRegistration {
    /**
     * Adds a service to this registry. The given object is closed when the associated registry is closed.
     * @param serviceType The type to make this service visible as.
     * @param serviceInstance The service implementation.
     */
    <T> void add(Class<T> serviceType, T serviceInstance);

    /**
     * Adds a service to this registry.
     *
     * Note: currently no dependencies are injected into the implementation.
     *
     * @param serviceType The service implementation to make visible. This class should have a public no-args constructor.
     */
    void add(Class<?> serviceType);

    /**
     * Adds a service provider bean to this registry. This provider may define factory and decorator methods. See {@link DefaultServiceRegistry} for details.
     */
    void addProvider(Object provider);
}
