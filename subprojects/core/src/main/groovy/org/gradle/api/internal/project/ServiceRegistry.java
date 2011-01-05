/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.project;

import org.gradle.api.internal.Factory;

/**
 * A registry of services.
 */
public interface ServiceRegistry {
    /**
     * Locates the service of the given type. There is a single instance for each service type.
     *
     * @param serviceType The service type.
     * @param <T>         The service type.
     * @return The service instance. Never returns null.
     * @throws IllegalArgumentException When there is no service of the given type available.
     */
    <T> T get(Class<T> serviceType) throws IllegalArgumentException;

    /**
     * Locates a factory which can create services of the given type.
     *
     * @param type The service type that the factory should create.
     * @param <T>  The service type that the factory should create.
     * @return The factory. Never returns null.
     * @throws IllegalArgumentException When there is no factory available for services of the given type.
     */
    <T> Factory<? extends T> getFactory(Class<T> type) throws IllegalArgumentException;

    /**
     * Creates a new service instance of the given type.
     *
     * @param type The service type
     * @param <T>  The service type.
     * @return The instance. Never returns null.
     * @throws IllegalArgumentException When there is no factory available for services of the given type.
     */
    <T> T newInstance(Class<T> type) throws IllegalArgumentException;
}
