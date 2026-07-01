/*
 * Copyright 2026 the original author or authors.
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

import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

/**
 * Decorate an existing {@link ServiceLookup} to additionally support injecting
 * any of the given object instances.
 */
public class InstanceInjectingServiceLookup implements ServiceLookup {

    private final Iterable<Object> instances;
    private final ServiceLookup delegate;
    private final ServiceLookup userTypeFilteredView;

    public InstanceInjectingServiceLookup(Iterable<Object> instances, ServiceLookup delegate) {
        this.instances = instances;
        this.delegate = delegate;

        ServiceLookup filteredDelegate = delegate.withUserTypeFilter();
        this.userTypeFilteredView = filteredDelegate == delegate
            ? this
            : new InstanceInjectingServiceLookup(instances, filteredDelegate);
    }

    @Override
    public @Nullable Object find(Type serviceType) throws ServiceLookupException {
        Object instance = getInjectedInstance(serviceType);
        if (instance != null) {
            return instance;
        }

        return delegate.find(serviceType);
    }

    @Override
    public Object get(Type serviceType) throws UnknownServiceException, ServiceLookupException {
        Object instance = getInjectedInstance(serviceType);
        if (instance != null) {
            return instance;
        }

        return delegate.get(serviceType);
    }

    @Override
    public Object get(Type serviceType, Class<? extends Annotation> annotatedWith) throws UnknownServiceException, ServiceLookupException {
        Object instance = getInjectedInstance(serviceType);
        if (instance != null) {
            return instance;
        }

        return delegate.get(serviceType, annotatedWith);
    }

    @Override
    public ServiceLookup withUserTypeFilter() {
        return userTypeFilteredView;
    }

    private @Nullable Object getInjectedInstance(Type serviceType) {
        if (serviceType instanceof Class) {
            Class<?> clazz = (Class<?>) serviceType;
            for (Object instance : instances) {
                if (clazz.isInstance(instance)) {
                    return instance;
                }
            }
        }

        return null;
    }

}
