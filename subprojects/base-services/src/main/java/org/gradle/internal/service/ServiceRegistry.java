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
package org.gradle.internal.service;

import org.gradle.internal.Factory;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.lang.reflect.Type;
import java.util.List;

/**
 * A registry of services.
 */
public interface ServiceRegistry {
    /**
     * Locates the service of the given type.
     *
     * @param serviceType The service type.
     * @param <T>         The service type.
     * @return The service instance. Never returns null.
     * @throws UnknownServiceException When there is no service of the given type available.
     * @throws ServiceLookupException On failure to lookup the specified service.
     */
    @UsedByScanPlugin
    <T> T get(Class<T> serviceType) throws UnknownServiceException, ServiceLookupException;

    /**
     * Locates all services of the given type.
     *
     * @param serviceType The service type.
     * @param <T>         The service type.
     * @throws ServiceLookupException On failure to lookup the specified service.
     */
    <T> List<T> getAll(Class<T> serviceType) throws ServiceLookupException;

    /**
     * Locates the service of the given type.
     *
     * @param serviceType The service type.
     * @return The service instance. Never returns null.
     * @throws UnknownServiceException When there is no service of the given type available.
     * @throws ServiceLookupException On failure to lookup the specified service.
     */
    Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException;

    /**
     * Locates a factory which can create services of the given type.
     *
     * @param type The service type that the factory should create.
     * @param <T>  The service type that the factory should create.
     * @return The factory. Never returns null.
     * @throws UnknownServiceException When there is no factory available for services of the given type.
     * @throws ServiceLookupException On failure to lookup the specified service factory.
     */
    <T> Factory<T> getFactory(Class<T> type) throws UnknownServiceException, ServiceLookupException;

    /**
     * Creates a new service instance of the given type.
     *
     * @param type The service type
     * @param <T>  The service type.
     * @return The instance. Never returns null.
     * @throws UnknownServiceException When there is no factory available for services of the given type.
     * @throws ServiceLookupException On failure to lookup the specified service factory.
     */
    <T> T newInstance(Class<T> type) throws UnknownServiceException, ServiceLookupException;

    boolean hasService(Class<?> serviceType);

}
